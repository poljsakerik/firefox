/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.imagecheck

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.app.contentsafety.ClassifiableContent
import android.app.contentsafety.ContentClassificationResult
import android.app.contentsafety.ContentSafetyManager
import android.content.Context
import android.content.LocusId
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.annotation.RequiresApi
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import mozilla.components.support.base.log.logger.Logger
import org.json.JSONObject

/** Judges one image: the system's check where it's on, the bundled model otherwise. Answers are cached by content. */
internal class ImageChecker(private val system: SystemContentSafety?, private val model: OnnxImageModel?) {
    private val answers = LruCache<String, JSONObject>(CACHE_SIZE)

    val hasModel: Boolean
        get() = model != null

    fun check(image: EncodedImage): JSONObject {
        val key = MessageDigest.getInstance("SHA-256").digest(image.bytes).joinToString("") { "%02x".format(it) }
        answers.get(key)?.let {
            return it
        }
        // SVG is recognised by its bytes, which no photo format starts with; a type that merely says so isn't trusted.
        val answer =
            when {
                ImagePixels.looksLikeSvg(image.bytes) -> ImageCheckMessages.unsupported()
                !ImagePixels.decodes(image.bytes) -> ImageCheckMessages.error("not an image")
                else -> judge(image)
            }
        if (!answer.has("error")) answers.put(key, answer)
        return answer
    }

    private fun judge(image: EncodedImage): JSONObject {
        val systemAnswer =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2
            ) {
                system?.check(image)
            } else {
                null
            }
        return systemAnswer ?: model?.check(image.bytes) ?: ImageCheckMessages.error("no model")
    }

    companion object {
        private const val CACHE_SIZE = 512
        private val logger = Logger("mirlumi-image-check")

        fun create(context: Context): ImageChecker {
            val model = runCatching {
                OnnxImageModel.load(context)
            }
                .onFailure { logger.error("The image check model didn't load", it) }
                .getOrNull()
            return ImageChecker(systemContentSafety(context), model)
        }

        /** Android's own check, which arrived in a minor release, 37.2. */
        private fun systemContentSafety(context: Context): SystemContentSafety? =
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
                    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2
            ) {
                context.getSystemService(ContentSafetyManager::class.java)?.let { SystemContentSafety(context, it) }
            } else {
                null
            }
    }
}

/**
 * The bundled model, as model.json describes it: uint8 RGB [1, height, width, 3] in, a probability per label out.
 * Nothing here depends on which model it is, so replacing it needs no code change.
 */
internal class OnnxImageModel(
    private val environment: OrtEnvironment,
    private val session: OrtSession,
    private val width: Int,
    private val height: Int,
) {
    fun check(bytes: ByteArray): JSONObject {
        val bitmap = ImagePixels.decode(bytes, width, height) ?: return ImageCheckMessages.error("not an image")
        val pixels = ImagePixels.rgb(bitmap)
        bitmap.recycle()
        val shape = longArrayOf(1, height.toLong(), width.toLong(), CHANNELS)
        OnnxTensor.createTensor(environment, ByteBuffer.wrap(pixels), shape, OnnxJavaType.UINT8).use { input ->
            session.run(mapOf(session.inputNames.first() to input)).use { output ->
                val tensor = output[0] as? OnnxTensor ?: return ImageCheckMessages.error("unexpected output")
                val buffer = tensor.floatBuffer
                return ImageCheckMessages.scores(FloatArray(buffer.remaining()).also { buffer.get(it) })
            }
        }
    }

    companion object {
        private const val DIRECTORY = "image-check"
        private const val CHANNELS = 3L
        private const val MAX_THREADS = 4

        fun load(context: Context): OnnxImageModel {
            val manifest =
                JSONObject(context.assets.open("$DIRECTORY/model.json").use { it.readBytes().decodeToString() })
            val bytes = context.assets.open("$DIRECTORY/${manifest.getString("file")}").use { it.readBytes() }
            val input = manifest.getJSONObject("input")
            val environment = OrtEnvironment.getEnvironment()
            val options =
                OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(1, MAX_THREADS))
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
            return OnnxImageModel(
                environment,
                environment.createSession(bytes, options),
                input.getInt("width"),
                input.getInt("height"),
            )
        }
    }
}

/** Decoding and resizing, the same way on every model: stretched to the model's size, RGB bytes row by row. */
internal object ImagePixels {
    fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        val scaled = Bitmap.createScaledBitmap(decoded, width, height, true)
        if (scaled !== decoded) decoded.recycle()
        return scaled
    }

    fun rgb(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return rgb(pixels)
    }

    /** ARGB ints to RGB bytes. Transparent pixels keep their colour, as the model's training images had none. */
    fun rgb(argb: IntArray): ByteArray {
        val out = ByteArray(argb.size * CHANNELS)
        for (i in argb.indices) {
            val pixel = argb[i]
            out[i * CHANNELS] = (pixel shr RED_SHIFT and BYTE).toByte()
            out[i * CHANNELS + 1] = (pixel shr GREEN_SHIFT and BYTE).toByte()
            out[i * CHANNELS + 2] = (pixel and BYTE).toByte()
        }
        return out
    }

    fun decodes(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    fun looksLikeSvg(bytes: ByteArray): Boolean {
        val start = bytes.copyOf(minOf(bytes.size, SNIFF_BYTES)).decodeToString().trimStart()
        return start.startsWith("<svg") || (start.startsWith("<?xml") && start.contains("<svg"))
    }

    private const val SNIFF_BYTES = 512
    private const val CHANNELS = 3
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8
    private const val BYTE = 0xFF
}

/**
 * Android's own content safety check, from Android 17 QPR2 (API 37.2): on-device, nudity-focused, and only answering
 * when the user (or a parent through Family Link) has content safety switched on. Otherwise it returns null and the
 * model decides.
 */
@RequiresApi(Build.VERSION_CODES_FULL.CINNAMON_BUN_2)
internal class SystemContentSafety(
    private val context: Context,
    private val manager: ContentSafetyManager,
) {
    private val next = AtomicInteger()

    /** Android's answer, or null when it gives none or fails, so the model decides instead. */
    fun check(image: EncodedImage): JSONObject? = runCatching {
        classify(image)
    }
        .onFailure { logger.warn("Content safety couldn't check an image", it) }
        .getOrNull()

    private fun classify(image: EncodedImage): JSONObject? {
        if (!manager.isContentSafetyEnabled) return null
        val file = File.createTempFile("mirlumi-image", null, context.cacheDir)
        try {
            file.writeBytes(image.bytes)
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { data ->
                val content =
                    ClassifiableContent.Builder(LocusId("mirlumi-image-${next.incrementAndGet()}"), image.mimeType)
                        .setData(data)
                        .build()
                val done = CountDownLatch(1)
                var type = ContentClassificationResult.TYPE_UNCLASSIFIED
                manager.requestContentClassification(content, Runnable::run) { result ->
                    type = result.type
                    done.countDown()
                }
                if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) return null
                return when (type) {
                    ContentClassificationResult.TYPE_BLOCKED,
                    ContentClassificationResult.TYPE_WARNING -> ImageCheckMessages.sensitive(true)
                    ContentClassificationResult.TYPE_ALLOWED -> ImageCheckMessages.sensitive(false)
                    else -> null
                }
            }
        } finally {
            file.delete()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 5L
        private val logger = Logger("mirlumi-image-check")
    }
}
