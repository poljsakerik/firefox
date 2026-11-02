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

    /** The public site for [locale]: mirlumi.si in Slovenian, mirlumi.com otherwise. */
    fun siteUrl(locale: Locale): String =
        if (locale.language == "sl") {
            "https://mirlumi.si"
        } else {
            "https://mirlumi.com"
        }
}
