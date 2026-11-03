/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedScreensTest {
    @Test
    fun `screens that change the browser are locked`() {
        listOf(
            "org.mozilla.fenix.settings.SettingsFragment",
            "org.mozilla.fenix.settings.search.SearchEngineFragment",
            "org.mozilla.fenix.settings.logins.fragment.SavedLoginsFragment",
            "org.mozilla.fenix.settings.trustpanel.TrustPanelFragment",
            "org.mozilla.fenix.addons.AddonsManagementFragment",
            "org.mozilla.fenix.addons.InstalledAddonDetailsFragment",
            "org.mozilla.fenix.trackingprotection.ProtectionsDashboardFragment",
            "org.mozilla.fenix.exceptions.trackingprotection.TrackingProtectionExceptionsFragment",
            "org.mozilla.fenix.translations.preferences.automatic.AutomaticTranslationPreferenceFragment",
            "org.mozilla.fenix.translations.settings.TranslationSettingsFragment",
            "org.mozilla.fenix.nimbus.NimbusExperimentsFragment",
            "org.mozilla.fenix.onboarding.WallpaperOnboardingDialogFragment",
        ).forEach { assertTrue(it, LockedScreens.isLocked(it)) }
    }

    @Test
    fun `browsing and searching stay open`() {
        listOf(
            "org.mozilla.fenix.home.HomeFragment",
            "org.mozilla.fenix.browser.BrowserFragment",
            "org.mozilla.fenix.tabstray.ui.TabManagementFragment",
            "org.mozilla.fenix.bookmarks.BookmarkFragment",
            "org.mozilla.fenix.library.history.HistoryFragment",
            "org.mozilla.fenix.components.menu.MenuDialogFragment",
            "org.mozilla.fenix.translations.TranslationsDialogFragment",
            "org.mozilla.fenix.addons.WebExtensionActionPopupFragment",
            "org.mozilla.fenix.onboarding.OnboardingFragment",
            "org.mozilla.fenix.settingsx.Lookalike",
        ).forEach { assertFalse(it, LockedScreens.isLocked(it)) }
    }
}
