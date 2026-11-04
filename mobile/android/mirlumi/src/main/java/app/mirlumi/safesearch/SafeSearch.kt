/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.safesearch

import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.base.log.logger.Logger

/**
 * Sends every page load on a DuckDuckGo search host to safe.duckduckgo.com, where DuckDuckGo applies strict safe search
 * whatever the page's own setting says. A built-in web extension does it, in private tabs too, with the rules that
 * packages/safe-search in the mirlumi repository generates for both platforms.
 */
object SafeSearch {
    const val EXTENSION_ID = "safe-search@mirlumi.app"
    const val EXTENSION_URL = "resource://android/assets/extensions/mirlumi-safe-search/"

    private val logger = Logger("mirlumi-safe-search")

    /** Installs the extension when [enabled], and removes a copy an earlier build installed when not. */
    fun install(runtime: WebExtensionRuntime, enabled: Boolean) {
        if (enabled) {
            runtime.installBuiltInWebExtension(
                EXTENSION_ID,
                EXTENSION_URL,
                onError = { logger.error("Failed to install the safe search extension", it) },
            )
        } else {
            runtime.listInstalledWebExtensions(
                onSuccess = { extensions ->
                    extensions.filter { it.id == EXTENSION_ID }.forEach { runtime.uninstallWebExtension(it) }
                }
            )
        }
    }
}
