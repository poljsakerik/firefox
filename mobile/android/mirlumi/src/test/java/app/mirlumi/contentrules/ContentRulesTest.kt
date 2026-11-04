/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.contentrules

import java.io.File
import mozilla.components.concept.engine.CancellableOperation
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRulesTest {
    private val extension = File("src/main/assets/extensions/mirlumi-content-rules")

    private class FakeRuntime : WebExtensionRuntime {
        val installed = mutableListOf<Pair<String, String>>()
        var listed = false

        override fun installBuiltInWebExtension(
            id: String,
            url: String,
            onSuccess: (WebExtension) -> Unit,
            onError: (Throwable) -> Unit,
        ): CancellableOperation {
            installed += id to url
            return CancellableOperation.Noop()
        }

        override fun listInstalledWebExtensions(
            onSuccess: (List<WebExtension>) -> Unit,
            onError: (Throwable) -> Unit,
        ) {
            listed = true
            onSuccess(emptyList())
        }
    }

    @Test
    fun `installs the bundled extension as a built-in when enabled`() {
        val runtime = FakeRuntime()
        ContentRules.install(runtime, enabled = true)
        assertEquals(
            listOf("content-rules@mirlumi.app" to "resource://android/assets/extensions/mirlumi-content-rules/"),
            runtime.installed,
        )
        assertFalse(runtime.listed)
    }

    @Test
    fun `looks for an earlier copy to remove instead of installing when disabled`() {
        val runtime = FakeRuntime()
        ContentRules.install(runtime, enabled = false)
        assertTrue(runtime.installed.isEmpty())
        assertTrue(runtime.listed)
    }

    @Test
    fun `the extension holds every file its manifest names`() {
        val manifest = File(extension, "manifest.template.json").readText()
        val named = Regex("\"([\\w/.-]+\\.js)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(
            listOf(
                "generated/engine.js",
                "generated/rules.js",
                "content.js",
                "generated/rules.js",
                "generated/validate.js",
                "background.js",
            ),
            named,
        )
        named.forEach { assert(File(extension, it).isFile) { "missing $it" } }
        assert(File(extension, "generated/config.json").isFile)
    }
}
