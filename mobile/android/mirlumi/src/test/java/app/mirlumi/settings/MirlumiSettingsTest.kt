/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settings

import android.content.Context
import androidx.navigation.NavAction
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavOptions
import androidx.navigation.NavigatorProvider
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mirlumi.R
import app.mirlumi.allowlist.Allowlist
import app.mirlumi.settingslock.SettingsLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MirlumiSettingsTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        Allowlist.isEnabled = false
        Allowlist.isOpenModeAvailable = false
        SettingsLock.isEnabled = false
        MirlumiSettings.isEnabled = false
    }

    private fun screen(): PreferenceScreen =
        PreferenceManager(context).inflateFromResource(context, R.xml.mirlumi_settings, null).also {
            MirlumiSettingsFragment.prepareCategories(it, categoryLayout = 0)
        }

    private fun PreferenceScreen.isShown(key: String) = findPreference<Preference>(key)!!.isVisible

    @Test
    fun `every feature's row shows while its feature is on, with the browser's settings last`() {
        Allowlist.isEnabled = true
        Allowlist.isOpenModeAvailable = true
        SettingsLock.isEnabled = true
        val screen = screen()
        assertTrue(screen.isShown("mirlumi_browsing_category"))
        assertTrue(screen.isShown("mirlumi_allowed_sites"))
        assertTrue(screen.isShown("mirlumi_open_mode"))
        assertTrue(screen.isShown("mirlumi_pin_category"))
        assertTrue(screen.isShown("mirlumi_settings_pin"))
        val last = screen.getPreference(screen.preferenceCount - 1)
        assertEquals(MirlumiSettingsFragment.BROWSER_SETTINGS_KEY, last.key)
        assertTrue(last.isVisible)
    }

    @Test
    fun `a section whose features are off is hidden, and the browser's settings stay`() {
        SettingsLock.isEnabled = true
        val screen = screen()
        assertFalse(screen.isShown("mirlumi_browsing_category"))
        assertTrue(screen.isShown("mirlumi_pin_category"))
        assertTrue(screen.isShown(MirlumiSettingsFragment.BROWSER_SETTINGS_KEY))
    }

    @Test
    fun `open mode's row is hidden without the page classifier`() {
        Allowlist.isEnabled = true
        val screen = screen()
        assertTrue(screen.isShown("mirlumi_allowed_sites"))
        assertFalse(screen.isShown("mirlumi_open_mode"))
    }

    @Test
    fun `the app's action to Settings is pointed at Mirlumi's screen, keeping its options`() {
        val graph = NavGraph(NavGraphNavigator(NavigatorProvider()))
        val options = NavOptions.Builder().setLaunchSingleTop(true).build()
        graph.putAction(SETTINGS_ACTION, NavAction(BROWSER_SETTINGS, options))
        MirlumiSettings.route(graph, SETTINGS_ACTION, MIRLUMI_SCREEN)
        assertEquals(MIRLUMI_SCREEN, graph.getAction(SETTINGS_ACTION)!!.destinationId)
        assertEquals(options, graph.getAction(SETTINGS_ACTION)!!.navOptions)
    }

    @Test
    fun `a graph without the action is left as it is`() {
        val graph = NavGraph(NavGraphNavigator(NavigatorProvider()))
        MirlumiSettings.route(graph, SETTINGS_ACTION, MIRLUMI_SCREEN)
        assertNull(graph.getAction(SETTINGS_ACTION))
    }

    @Test
    fun `Mirlumi's rows leave the browser's settings only while its screen is on`() {
        Allowlist.isEnabled = true
        SettingsLock.isEnabled = true
        val browserSettings = screen()
        MirlumiSettings.hideMovedRows(browserSettings)
        assertTrue(browserSettings.isShown("mirlumi_allowed_sites"))
        assertTrue(browserSettings.isShown("mirlumi_settings_pin"))
        MirlumiSettings.isEnabled = true
        MirlumiSettings.hideMovedRows(browserSettings)
        MirlumiSettings.rowKeys.forEach { assertFalse(browserSettings.isShown(it)) }
    }

    @Test
    @Config(qualifiers = "sl")
    fun `Slovenian names the sections and the browser's settings`() {
        assertEquals("Nastavitve", context.getString(R.string.mirlumi_settings_title))
        assertEquals("Brskanje", context.getString(R.string.mirlumi_settings_browsing))
        assertEquals("Nastavitve brskalnika", context.getString(R.string.mirlumi_browser_settings_title))
    }

    private companion object {
        const val SETTINGS_ACTION = 1
        const val BROWSER_SETTINGS = 2
        const val MIRLUMI_SCREEN = 3
    }
}
