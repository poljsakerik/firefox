/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

/**
 * The Fenix screens that need the PIN, by fragment class: anything that changes the browser rather than browses.
 * Matching packages rather than single screens means a settings screen added upstream is locked without a change here.
 */
internal object LockedScreens {
    private const val FENIX = "org.mozilla.fenix"

    private val lockedPackages =
        listOf(
            "$FENIX.settings.",
            "$FENIX.addons.",
            "$FENIX.exceptions.",
            "$FENIX.trackingprotection.",
            "$FENIX.translations.preferences.",
            "$FENIX.translations.settings.",
            "$FENIX.iconpicker.",
            "$FENIX.ipprotection.",
            "$FENIX.nimbus.",
            "$FENIX.debugsettings.",
            "$FENIX.crashes.",
            "$FENIX.perf.",
        )

    private val lockedScreens = setOf("$FENIX.onboarding.WallpaperOnboardingDialogFragment")

    // An installed extension's toolbar popup is part of browsing; managing extensions isn't.
    private val openScreens = setOf("$FENIX.addons.WebExtensionActionPopupFragment")

    // The splash screen and onboarding itself, where the PIN can't be set up yet.
    private val startScreens = setOf("$FENIX.StartupFragment", "$FENIX.onboarding.OnboardingFragment")

    fun isLocked(className: String): Boolean =
        className !in openScreens && (className in lockedScreens || lockedPackages.any { className.startsWith(it) })

    fun isStartScreen(className: String): Boolean = className in startScreens
}
