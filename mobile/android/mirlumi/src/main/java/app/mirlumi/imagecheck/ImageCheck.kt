/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.imagecheck

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import mozilla.components.concept.engine.request.RequestInterceptor
import mozilla.components.concept.engine.webextension.MessageHandler
import mozilla.components.concept.engine.webextension.Port
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.webextensions.BuiltInWebExtensionController
import org.json.JSONObject

/**
 * Hides every image on a page until it's been checked, then shows it, or a placeholder when it's explicit. The page
 * engine from packages/image-check runs in a built-in extension; it hands each image to this object over a native port.
 * Android's own content safety check judges it where the user has that switched on (Android 17 QPR2 and later), and the
 * bundled model from model.json everywhere else.
 */
object ImageCheck {
    private val logger = Logger("mirlumi-image-check")

    private const val EXTENSION_ID = "image-check@mirlumi.app"
    private const val EXTENSION_URL = "resource://android/assets/extensions/mirlumi-image-check/"
    private const val PORT_NAME = "mirlumiImageCheck"

    private val controller = BuiltInWebExtensionController(EXTENSION_ID, EXTENSION_URL, PORT_NAME)
    private val main = Handler(Looper.getMainLooper())

    // One image at a time: the model already spreads each one over several cores.
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "mirlumi-image-check")
    }

    // Created on the executor by the first check, so loading the model never competes with the app's start.
    private var checker: ImageChecker? = null

    /** Whether the check is on, as passed to [install]. */
    var isEnabled = false
        private set

    @VisibleForTesting
    internal fun setEnabledForTest(enabled: Boolean) {
        isEnabled = enabled
    }

    /** Reader view shows an article's images in another extension's page, where they can't be checked. */
    val allowsReaderView: Boolean
        get() = !isEnabled

    private val HANDS_OVER_IMAGES =
        setOf(
            "mozac.feature.contextmenu.save_image",
            "mozac.feature.contextmenu.copy_image",
            "mozac.feature.contextmenu.share_image",
            "mozac.feature.contextmenu.download_link",
        )
    private val OPENS_IMAGE =
        setOf("mozac.feature.contextmenu.open_image_in_new_tab", "mozac.feature.contextmenu.copy_image_location")

    /** Installs the extension in [runtime], or removes it when [enabled] is false. Call once the engine exists. */
    fun install(context: Context, runtime: WebExtensionRuntime, enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) {
            runtime.listInstalledWebExtensions(
                onSuccess = { installed ->
                    installed.firstOrNull { it.id == EXTENSION_ID }?.let { runtime.uninstallWebExtension(it) }
                }
            )
            return
        }
        val appContext = context.applicationContext
        controller.registerBackgroundMessageHandler(Messages(appContext), PORT_NAME)
        controller.install(
            runtime,
            onSuccess = { logger.debug("Installed image check extension") },
            onError = { throwable -> logger.error("Failed to install the image check extension", throwable) },
        )
    }

    /**
     * The long-press menu without the actions that hand an image's bytes to Downloads, the clipboard or another app,
     * which would show it unchecked: saving, copying and sharing an image, and downloading a link, which may lead to an
     * image whatever its address says. Opening an image in a new tab, and copying its address, stay for web images
     * only: that tab checks them, but nothing checks a data: or blob: image opened on its own.
     */
    fun contextMenu(candidates: List<ContextMenuCandidate>): List<ContextMenuCandidate> {
        if (!isEnabled) return candidates
        return candidates
            .filterNot { it.id in HANDS_OVER_IMAGES }
            .map { candidate ->
                if (candidate.id in OPENS_IMAGE) {
                    candidate.copy(showFor = { tab, hit -> isWebAddress(hit.src) && candidate.showFor(tab, hit) })
                } else {
                    candidate
                }
            }
    }

    /** Refuses a top-level data: image, which would show unchecked: Gecko runs no content scripts in it. */
    fun interceptLoad(uri: String, isSubframeRequest: Boolean): RequestInterceptor.InterceptionResponse? =
        if (isEnabled && !isSubframeRequest && uri.startsWith("data:image/", ignoreCase = true)) {
            RequestInterceptor.InterceptionResponse.Deny
        } else {
            null
        }

    private fun isWebAddress(address: String): Boolean =
        address.startsWith("http://", ignoreCase = true) || address.startsWith("https://", ignoreCase = true)

    private fun failed(e: Throwable): JSONObject {
        logger.warn("Image check failed", e)
        return ImageCheckMessages.error(e.javaClass.simpleName)
    }

    private class Messages(private val context: Context) : MessageHandler {
        override fun onPortMessage(message: Any, port: Port) {
            val request = ImageCheckMessages.request(message) ?: return
            executor.execute {
                val started = SystemClock.elapsedRealtime()
                // Every request gets an answer, whatever goes wrong: the page waits for it, and an error keeps the
                // image
                // hidden. A request the page has stopped waiting for is skipped.
                val answer =
                    if (System.currentTimeMillis() > request.expires) {
                        ImageCheckMessages.error("expired")
                    } else {
                        runCatching {
                                val image = ImageCheckMessages.decode(request.dataUrl)
                                if (image == null) {
                                    ImageCheckMessages.error("not a data: URL")
                                } else {
                                    // A checker without its model isn't kept, so a model that failed to load is tried
                                    // again.
                                    (checker ?: ImageChecker.create(context).also { if (it.hasModel) checker = it })
                                        .check(image)
                                }
                            }
                            .getOrElse(::failed)
                    }
                logger.debug("Checked an image in ${SystemClock.elapsedRealtime() - started} ms: $answer")
                main.post { port.postMessage(ImageCheckMessages.answer(request.id, answer)) }
            }
        }
    }
}

/** An image as the page sent it: its bytes and the MIME type its data: URL named. */
internal class EncodedImage(val bytes: ByteArray, val mimeType: String)

/** The messages between the app and the extension's background script. Answers are what engine.js reads. */
internal object ImageCheckMessages {
    data class Request(val id: Int, val dataUrl: String, val expires: Long = Long.MAX_VALUE)

    private val DATA_URL = Regex("^data:([^;,]*)((?:;[^;,]*)*),")

    fun request(message: Any): Request? {
        val json = message as? JSONObject ?: return null
        if (json.optString("type") != "check") return null
        val id = json.optInt("id", -1).takeIf { it >= 0 } ?: return null
        val data = json.optString("data").takeIf { it.startsWith("data:") } ?: return null
        return Request(id, data, json.optLong("expires", Long.MAX_VALUE))
    }

    /** The bytes of a data: URL, or null when it isn't one. */
    fun decode(dataUrl: String): EncodedImage? {
        val header = DATA_URL.find(dataUrl) ?: return null
        val payload = dataUrl.substring(header.range.last + 1)
        val base64 = header.groupValues[2].split(';').any { it.equals("base64", ignoreCase = true) }
        val bytes =
            if (base64) {
                android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
            } else {
                android.net.Uri.decode(payload).toByteArray(Charsets.ISO_8859_1)
            }
        return EncodedImage(bytes, header.groupValues[1].ifEmpty { "application/octet-stream" }.lowercase())
    }

    fun answer(id: Int, result: JSONObject): JSONObject =
        JSONObject().put("type", "answer").put("id", id).put("result", result)

    fun scores(probabilities: FloatArray): JSONObject =
        JSONObject().put("scores", org.json.JSONArray(probabilities.map { it.toDouble() }))

    fun sensitive(sensitive: Boolean): JSONObject = JSONObject().put("sensitive", sensitive)

    fun unsupported(): JSONObject = JSONObject().put("unsupported", true)

    fun error(reason: String): JSONObject = JSONObject().put("error", reason)
}
