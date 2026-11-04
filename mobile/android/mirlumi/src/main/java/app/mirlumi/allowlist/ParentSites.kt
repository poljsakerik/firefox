/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Context
import android.os.StrictMode

/** Where [ParentSites] keeps its entries. */
internal interface SiteStore {
    fun read(): Set<String>

    fun write(sites: Set<String>)
}

/**
 * The sites a parent allowed with the PIN, on top of the bundled list. Entries come from the extension, which builds
 * them with the shared matcher; [add] only refuses anything that can't be one, so a bad message can't store junk.
 */
internal class ParentSites(private val store: SiteStore) {
    private val entries: MutableSet<String> by lazy { store.read().filterTo(mutableSetOf(), ::isPlausibleEntry) }
    private val listeners = mutableListOf<(List<String>) -> Unit>()

    val sites: List<String>
        @Synchronized get() = entries.sorted()

    @Synchronized
    fun add(entry: String): Boolean {
        if (!isPlausibleEntry(entry) || !entries.add(entry)) return false
        changed()
        return true
    }

    @Synchronized
    fun remove(entry: String): Boolean {
        if (!entries.remove(entry)) return false
        changed()
        return true
    }

    @Synchronized
    fun addListener(listener: (List<String>) -> Unit) {
        listeners += listener
    }

    @Synchronized
    fun removeListener(listener: (List<String>) -> Unit) {
        listeners -= listener
    }

    private fun changed() {
        store.write(entries.toSet())
        val current = sites
        listeners.toList().forEach { it(current) }
    }

    companion object {
        private val PLAUSIBLE = Regex("^[a-z0-9-]+(\\.[a-z0-9-]+)+(/[a-z0-9._~-]+)*$")
        private const val MAX_LENGTH = 253

        fun isPlausibleEntry(entry: String): Boolean =
            entry.length <= MAX_LENGTH &&
                PLAUSIBLE.matches(entry) &&
                entry.split("/").drop(1).none { it == "." || it == ".." }
    }
}

internal class PreferencesSiteStore(context: Context) : SiteStore {
    private val prefs = context.applicationContext.getSharedPreferences("mirlumi_allowlist", Context.MODE_PRIVATE)

    override fun read(): Set<String> = allowingDiskAccess { prefs.getStringSet(KEY, null)?.toSet() ?: emptySet() }

    override fun write(sites: Set<String>) {
        prefs.edit().putStringSet(KEY, sites).apply()
    }

    private inline fun <T> allowingDiskAccess(block: () -> T): T {
        val policy = StrictMode.allowThreadDiskReads()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    private companion object {
        const val KEY = "parent_sites"
    }
}
