/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails

@RunWith(AndroidJUnit4::class)
class AllowlistInterceptTest {
    private val deny = RequestInterceptor.InterceptionResponse.Deny
    private val session = mock(EngineSession::class.java)

    @After
    fun reset() {
        Allowlist.isEnabled = false
        Allowlist.extensionReady = false
        Allowlist.isLive = { false }
        Allowlist.resumePendingLoads()
    }

    private fun loadedUrls(session: EngineSession): List<Any?> =
        mockingDetails(session).invocations.filter { it.method.name == "loadUrl" }.map { it.arguments[0] }

    private fun connect() {
        Allowlist.extensionReady = true
        Allowlist.resumePendingLoads()
    }

    @Test
    fun `web pages are refused until the extension is ready`() {
        Allowlist.isEnabled = true
        assertEquals(deny, Allowlist.interceptLoad(session, "https://pbskids.org/", isSubframeRequest = false))
        assertEquals(deny, Allowlist.interceptLoad(session, "http://example.com/", isSubframeRequest = false))
        assertNull(Allowlist.interceptLoad(session, "https://example.com/", isSubframeRequest = true))
        assertNull(Allowlist.interceptLoad(session, "about:home", isSubframeRequest = false))

        Allowlist.extensionReady = true
        assertNull(
            "the extension decides",
            Allowlist.interceptLoad(session, "https://example.com/", isSubframeRequest = false),
        )
    }

    @Test
    fun `a page refused while the extension starts is loaded again once it's ready`() {
        Allowlist.isEnabled = true
        Allowlist.isLive = { true }
        val other = mock(EngineSession::class.java)
        assertEquals(deny, Allowlist.interceptLoad(session, "https://example.com/first", isSubframeRequest = false))
        assertEquals(deny, Allowlist.interceptLoad(session, "https://example.com/latest", isSubframeRequest = false))
        assertEquals(deny, Allowlist.interceptLoad(other, "https://pbskids.org/", isSubframeRequest = false))
        assertTrue("nothing loads before the extension is ready", loadedUrls(session).isEmpty())

        connect()
        assertEquals("only the latest page, once", listOf("https://example.com/latest"), loadedUrls(session))
        assertEquals(listOf("https://pbskids.org/"), loadedUrls(other))
        assertTrue(Allowlist.pendingLoads.isEmpty)

        assertNull(
            "the extension judges the retry",
            Allowlist.interceptLoad(session, "https://example.com/latest", false),
        )
        Allowlist.resumePendingLoads()
        assertEquals(1, loadedUrls(session).size)
    }

    @Test
    fun `a refused page isn't loaded again when its tab moved on or closed`() {
        Allowlist.isEnabled = true
        val closed = mock(EngineSession::class.java)
        Allowlist.isLive = { it !== closed }
        Allowlist.interceptLoad(session, "https://example.com/", isSubframeRequest = false)
        assertNull(Allowlist.interceptLoad(session, "about:home", isSubframeRequest = false))
        Allowlist.interceptLoad(closed, "https://example.com/", isSubframeRequest = false)

        connect()
        assertTrue(loadedUrls(session).isEmpty())
        assertTrue(loadedUrls(closed).isEmpty())
    }

    @Test
    fun `a hidden host refused at startup isn't loaded again`() {
        Allowlist.isEnabled = true
        Allowlist.isLive = { true }
        assertEquals(deny, Allowlist.interceptLoad(session, "https://addons.mozilla.org/", isSubframeRequest = false))
        assertNull(Allowlist.interceptLoad(session, "https://example.com/", isSubframeRequest = true))

        connect()
        assertTrue(loadedUrls(session).isEmpty())
    }

    @Test
    fun `hosts Gecko hides from extensions are always refused`() {
        Allowlist.isEnabled = true
        Allowlist.extensionReady = true
        assertEquals(deny, Allowlist.interceptLoad(session, "https://addons.mozilla.org/", isSubframeRequest = false))
        assertEquals(
            deny,
            Allowlist.interceptLoad(session, "https://SUPPORT.mozilla.org./kb", isSubframeRequest = false),
        )
    }

    @Test
    fun `nothing is refused while the allowlist is off`() {
        assertNull(Allowlist.interceptLoad(session, "https://addons.mozilla.org/", isSubframeRequest = false))
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
