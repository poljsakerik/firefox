/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.imagecheck

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mozilla.components.browser.state.state.createTab
import mozilla.components.concept.engine.HitResult
import mozilla.components.feature.contextmenu.ContextMenuCandidate
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageCheckTest {
    @Test
    fun `a check request needs an id and a data URL`() {
        val good = JSONObject().put("type", "check").put("id", 3).put("data", "data:image/png;base64,AAAA")
        assertEquals(ImageCheckMessages.Request(3, "data:image/png;base64,AAAA"), ImageCheckMessages.request(good))
        assertNull(ImageCheckMessages.request(JSONObject(good.toString()).put("type", "allow")))
        assertNull(ImageCheckMessages.request(JSONObject(good.toString()).put("id", -1)))
        assertNull(ImageCheckMessages.request(JSONObject(good.toString()).put("data", "https://example.com/a.png")))
        assertNull(ImageCheckMessages.request("not json"))
    }

    @Test
    fun `data URLs decode in base64 and percent encoding`() {
        val base64 = ImageCheckMessages.decode("data:image/JPEG;base64,AQID")!!
        assertArrayEquals(byteArrayOf(1, 2, 3), base64.bytes)
        assertEquals("image/jpeg", base64.mimeType)
        val text = ImageCheckMessages.decode("data:image/svg+xml,%3Csvg%3E")!!
        assertEquals("<svg>", text.bytes.decodeToString())
        assertNull(ImageCheckMessages.decode("https://example.com/"))
    }

    @Test
    fun `pixels become RGB bytes in order`() {
        val argb = intArrayOf(0xFF102030.toInt(), 0x80FFFFFF.toInt())
        assertArrayEquals(
            byteArrayOf(0x10, 0x20, 0x30, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()),
            ImagePixels.rgb(argb),
        )
    }

    @Test
    fun `SVG is recognised by its content`() {
        assertTrue(ImagePixels.looksLikeSvg("  <?xml version=\"1.0\"?><svg/>".toByteArray()))
        assertFalse(ImagePixels.looksLikeSvg(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }

    @Test
    fun `the long-press menu loses the actions that hand over an image while the check is on`() {
        val candidates =
            listOf(
                    "save_image",
                    "copy_image",
                    "share_image",
                    "download_link",
                    "open_image_in_new_tab",
                    "copy_image_location",
                    "copy_link",
                )
                .map { ContextMenuCandidate("mozac.feature.contextmenu.$it", it, { _, _ -> true }, { _, _ -> }) }
        ImageCheck.setEnabledForTest(false)
        assertEquals(candidates, ImageCheck.contextMenu(candidates))
        ImageCheck.setEnabledForTest(true)
        val kept = ImageCheck.contextMenu(candidates)
        assertEquals(listOf("open_image_in_new_tab", "copy_image_location", "copy_link"), kept.map { it.label })
        val tab = createTab("https://example.com/")
        val web = HitResult.IMAGE("https://example.com/a.png")
        val data = HitResult.IMAGE("data:image/png;base64,AAAA")
        assertTrue(kept[0].showFor(tab, web))
        assertFalse(kept[0].showFor(tab, data))
        assertFalse(kept[1].showFor(tab, data))
        ImageCheck.setEnabledForTest(false)
    }

    @Test
    fun `a top-level data image is refused while the check is on`() {
        ImageCheck.setEnabledForTest(true)
        assertNotNull(ImageCheck.interceptLoad("DATA:image/jpeg;base64,/9j/", isSubframeRequest = false))
        assertNull(ImageCheck.interceptLoad("data:image/jpeg;base64,/9j/", isSubframeRequest = true))
        assertNull(ImageCheck.interceptLoad("data:text/html,hi", isSubframeRequest = false))
        assertNull(ImageCheck.interceptLoad("https://example.com/a.jpg", isSubframeRequest = false))
        ImageCheck.setEnabledForTest(false)
        assertNull(ImageCheck.interceptLoad("data:image/jpeg;base64,/9j/", isSubframeRequest = false))
    }

    @Test
    fun `the checker answers from the system before the model, and SVG without either`() {
        val checker = ImageChecker(system = null, model = null)
        assertEquals(
            true,
            checker.check(EncodedImage("<svg/>".toByteArray(), "image/svg+xml")).optBoolean("unsupported"),
        )
        assertTrue(checker.check(EncodedImage(byteArrayOf(1, 2, 3), "image/png")).has("error"))
    }

    @Test
    fun `the bundled model's description matches what the code expects`() {
        val assets = ApplicationProvider.getApplicationContext<Context>().assets
        val manifest = JSONObject(assets.open("image-check/model.json").use { it.readBytes().decodeToString() })
        assertEquals("image-check.onnx", manifest.getString("file"))
        val labels = manifest.getJSONArray("labels")
        val unsafe = manifest.getJSONObject("unsafe")
        assertTrue(
            unsafe.keys().asSequence().all { label -> (0 until labels.length()).any { labels.getString(it) == label } }
        )
        assertTrue(manifest.getJSONObject("input").getInt("width") > 0)
    }
}
