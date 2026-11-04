/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import mozilla.components.concept.engine.request.RequestInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllowlistInterceptTest {
    private val deny = RequestInterceptor.InterceptionResponse.Deny

    @After
    fun reset() {
        Allowlist.isEnabled = false
        Allowlist.extensionReady = false
    }

    @Test
    fun `web pages are refused until the extension is ready`() {
        Allowlist.isEnabled = true
        assertEquals(deny, Allowlist.interceptLoad("https://pbskids.org/", isSubframeRequest = false))
        assertEquals(deny, Allowlist.interceptLoad("http://example.com/", isSubframeRequest = false))
        assertNull(Allowlist.interceptLoad("https://example.com/", isSubframeRequest = true))
        assertNull(Allowlist.interceptLoad("about:home", isSubframeRequest = false))

        Allowlist.extensionReady = true
        assertNull("the extension decides", Allowlist.interceptLoad("https://example.com/", isSubframeRequest = false))
    }

    @Test
    fun `hosts Gecko hides from extensions are always refused`() {
        Allowlist.isEnabled = true
        Allowlist.extensionReady = true
        assertEquals(deny, Allowlist.interceptLoad("https://addons.mozilla.org/", isSubframeRequest = false))
        assertEquals(deny, Allowlist.interceptLoad("https://SUPPORT.mozilla.org./kb", isSubframeRequest = false))
    }

    @Test
    fun `nothing is refused while the allowlist is off`() {
        assertNull(Allowlist.interceptLoad("https://addons.mozilla.org/", isSubframeRequest = false))
    }

    @Test
    fun `an app link's fallback is dropped when it would be refused`() {
        Allowlist.isEnabled = true
        Allowlist.extensionReady = true
        fun appIntent(fallback: String?) =
            RequestInterceptor.InterceptionResponse.AppIntent(Intent(), "intent://x", fallback, "App")

        val hidden = Allowlist.checkReplacement(appIntent("https://support.mozilla.org/"))
        assertNull((hidden as RequestInterceptor.InterceptionResponse.AppIntent).fallbackUrl)
        val ordinary = appIntent("https://example.com/")
        assertEquals("the extension judges it", ordinary, Allowlist.checkReplacement(ordinary))
        assertNull(Allowlist.checkReplacement(null))

        Allowlist.extensionReady = false
        val early = Allowlist.checkReplacement(appIntent("https://example.com/"))
        assertNull((early as RequestInterceptor.InterceptionResponse.AppIntent).fallbackUrl)
    }

    @Test
    fun `a replacement address is refused when the load would be`() {
        Allowlist.isEnabled = true
        Allowlist.extensionReady = true
        val hidden = RequestInterceptor.InterceptionResponse.Url("https://support.mozilla.org/")
        assertEquals(deny, Allowlist.checkReplacement(hidden))
        val ordinary = RequestInterceptor.InterceptionResponse.Url("https://example.com/")
        assertEquals("the extension judges it", ordinary, Allowlist.checkReplacement(ordinary))
        val page = RequestInterceptor.InterceptionResponse.Content("<p>")
        assertEquals(page, Allowlist.checkReplacement(page))

        Allowlist.extensionReady = false
        assertEquals(deny, Allowlist.checkReplacement(ordinary))
    }

    @Test
    fun `the hidden hosts match Gecko's pref`() {
        val prefs = File("../../../modules/libpref/init/all.js").readText()
        val value =
            Regex("""pref\("extensions\.webextensions\.restrictedDomains",\s*"([^"]*)"\)""")
                .find(prefs)!!
                .groupValues[1]
        assertEquals(value.split(",").map { it.trim() }.toSet(), GeckoRestrictedHosts.hosts)
    }
}
