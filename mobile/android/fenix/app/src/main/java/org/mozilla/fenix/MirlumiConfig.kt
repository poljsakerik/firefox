/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix

import java.util.Locale

/**
 * Inherited Firefox services that Mirlumi Browser deliberately doesn't offer yet, and the addresses
 * that replace Mozilla's product links.
 */
object MirlumiConfig {
    /** Mozilla accounts and sync. Stays off until Mirlumi deliberately supports that service. */
    val accountsEnabled: Boolean = false

    /** Page summaries and AI controls, which depend on Mozilla-hosted services. */
    val aiServicesEnabled: Boolean = false

    /** Statements such as "You're protected", which must not imply child safety in this baseline. */
    val protectionClaimsEnabled: Boolean = false

    /**
     * Settings opens on Mirlumi's own settings, with the browser's settings one row below:
     * [app.mirlumi.settings.MirlumiSettings]. Off, Mirlumi's rows are in the browser's settings, under Privacy.
     */
    val settingsScreenEnabled: Boolean = true

    /** A parent's PIN in front of Settings and everything else that changes the browser: [app.mirlumi.settingslock]. */
    val settingsLockEnabled: Boolean = true

    /** DuckDuckGo searches always on its strict safe search host, safe.duckduckgo.com: [app.mirlumi.safesearch]. */
    val safeSearchEnabled: Boolean = true

    /**
     * DuckDuckGo the only search engine, with no others to choose, add or search once with, and no Google Lens:
     * [app.mirlumi.searchengines.OnlyDuckDuckGo].
     */
    val onlyDuckDuckGoEnabled: Boolean = true

    /** Only sites on the list for children, and sites a parent allowed with the PIN, open: [app.mirlumi.allowlist]. */
    val allowlistEnabled: Boolean = true

    /**
     * Open mode, which a parent can turn on in Settings: pages that aren't on the list open, and the on-device page
     * classifier blocks those it flags, and flagged DuckDuckGo searches. Needs [allowlistEnabled].
     */
    val pageClassifierEnabled: Boolean = true

    /** Hiding and blocking short-form video and app prompts: [app.mirlumi.contentrules.ContentRules]. */
    val contentRulesEnabled: Boolean = true

    /** Images hidden until checked, and explicit ones replaced: [app.mirlumi.imagecheck.ImageCheck]. */
    val imageCheckEnabled: Boolean = true

    /** The public site for [locale]: mirlumi.si in Slovenian, mirlumi.com otherwise. */
    fun siteUrl(locale: Locale): String =
        if (locale.language == "sl") {
            "https://mirlumi.si"
        } else {
            "https://mirlumi.com"
        }
}
