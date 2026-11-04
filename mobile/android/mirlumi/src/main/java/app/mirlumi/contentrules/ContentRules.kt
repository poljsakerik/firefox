/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.contentrules

import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import mozilla.components.support.base.log.logger.Logger

/**
 * Hides and blocks short-form video (Instagram Reels, YouTube Shorts, Snapchat Spotlight) and app prompts, with rules
 * that a built-in web extension applies to every page. The extension bundles a set of rules and replaces it with newer
 * signed rules it downloads, so the rules follow site changes without an app release.
 */
object ContentRules {
    private val logger = Logger("mirlumi-content-rules")

    const val EXTENSION_ID = "content-rules@mirlumi.app"
    const val EXTENSION_URL = "resource://android/assets/extensions/mirlumi-content-rules/"

    /**
     * Installs the extension when [enabled], or updates it when this build carries another version, and removes a copy
     * an earlier build installed when not. Call on every start.
     */
    fun install(runtime: WebExtensionRuntime, enabled: Boolean) {
        if (enabled) {
            runtime.installBuiltInWebExtension(
                EXTENSION_ID,
                EXTENSION_URL,
                onSuccess = { logger.debug("Installed content rules extension ${it.id}") },
                onError = { logger.error("Failed to install the content rules extension", it) },
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
