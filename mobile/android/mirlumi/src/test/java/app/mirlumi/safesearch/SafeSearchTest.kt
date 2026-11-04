/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.safesearch

import java.io.File
import mozilla.components.concept.engine.CancellableOperation
import mozilla.components.concept.engine.webextension.WebExtension
import mozilla.components.concept.engine.webextension.WebExtensionRuntime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SafeSearchTest {
    private val extensionDir = File("src/main/assets/extensions/mirlumi-safe-search")

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
    fun `installs the extension when enabled`() {
        val runtime = FakeRuntime()
        SafeSearch.install(runtime, enabled = true)
        assertEquals(listOf(SafeSearch.EXTENSION_ID to SafeSearch.EXTENSION_URL), runtime.installed)
    }

    @Test
    fun `looks for an earlier copy to remove instead of installing when disabled`() {
        val runtime = FakeRuntime()
        SafeSearch.install(runtime, enabled = false)
        assertTrue(runtime.installed.isEmpty())
        assertTrue(runtime.listed)
    }

    @Test
    fun `the manifest matches the extension's ID and may see every address the rules change`() {
        val manifest = JSONObject(File(extensionDir, "manifest.template.json").readText())
        assertEquals(
            SafeSearch.EXTENSION_ID,
            manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id"),
        )
        assertTrue(SafeSearch.EXTENSION_URL.endsWith("/${extensionDir.name}/"))

        val permissionArray = manifest.getJSONArray("permissions")
        val permissions = List(permissionArray.length()) { permissionArray.getString(it) }
        val rules = File(extensionDir, "generated/safe-search.js").readText()
        val patternList = checkNotNull(Regex("""REQUEST_PATTERNS = \[([^\]]*)]""").find(rules)).groupValues[1]
        val patterns = patternList.split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
        assertEquals(4, patterns.size)
        assertTrue(permissions.containsAll(patterns + listOf("webRequest", "webRequestBlocking")))
    }
}
