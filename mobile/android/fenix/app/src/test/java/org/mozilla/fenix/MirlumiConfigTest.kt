/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import android.content.Context
import io.mockk.mockk
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.fenix.settings.SupportUtils
import org.mozilla.fenix.settings.SupportUtils.MozillaPage
import org.mozilla.fenix.settings.SupportUtils.SumoTopic
import org.mozilla.fenix.tabstray.redux.state.Page

/** Pins the configuration that makes this Mirlumi Browser rather than Firefox. */
class MirlumiConfigTest {
    @Test
    fun `identity is Mirlumi's own`() {
        assertEquals("app.mirlumi.browser.dev", BuildConfig.APPLICATION_ID)
        assertEquals("mirlumi-dev", BuildConfig.DEEP_LINK_SCHEME)
    }

    @Test
    fun `no telemetry, crash reporting or attribution is built in`() {
        assertFalse(BuildConfig.TELEMETRY)
        assertFalse(BuildConfig.CRASH_REPORTING)
        assertNull(BuildConfig.SENTRY_TOKEN)
        assertNull(BuildConfig.ADJUST_TOKEN)
    }

    @Test
    fun `no remote experiments are fetched`() {
        assertNull(BuildConfig.NIMBUS_ENDPOINT)
        assertEquals("", BuildConfig.WALLPAPER_URL)
    }

    @Test
    fun `accounts stay off`() {
        assertFalse(MirlumiConfig.accountsEnabled)
    }

    @Test
    fun `only English and Slovenian are offered`() {
        assertEquals(listOf("en-US", "sl"), BuildConfig.SUPPORTED_LOCALE_ARRAY.sorted())
    }

    @Test
    fun `synced tabs page is not offered`() {
        assertFalse(Page.SyncedTabs in Page.visiblePages(shouldShowTabGroupsPage = true))
        assertTrue(Page.NormalTabs in Page.visiblePages(shouldShowTabGroupsPage = false))
    }

    @Test
    fun `product links open the Mirlumi site for the language`() {
        val english = Locale.ENGLISH
        val slovenian = Locale.forLanguageTag("sl")
        val context = mockk<Context>()

        assertEquals(
            "https://mirlumi.com/podpora.html",
            SupportUtils.getSumoURLForTopic(context, SumoTopic.HELP, english),
        )
        assertEquals("https://mirlumi.si/podpora.html", SupportUtils.getGenericSumoURLForTopic(SumoTopic.HELP, slovenian))
        assertEquals(
            "https://mirlumi.com/politika-zasebnosti.html",
            SupportUtils.getMozillaPageUrl(MozillaPage.PRIVACY_NOTICE, english),
        )
        assertEquals(
            "https://mirlumi.si/pravno-obvestilo.html",
            SupportUtils.getMozillaPageUrl(MozillaPage.TERMS_OF_SERVICE, slovenian),
        )
    }
}
