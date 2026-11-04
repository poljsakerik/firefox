/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Context
import android.os.StrictMode

/** Where [OpenMode] keeps its setting. */
internal interface OpenModeStore {
    fun read(): Boolean

    fun write(open: Boolean)
}

/**
 * Open mode, which a parent chooses in Settings behind the PIN: pages that aren't on the list open, and the page
 * classifier blocks those it flags. Off by default, which is allowlist-only browsing. [available] is false when the
 * build leaves the page classifier out, and then open mode is always off.
 */
internal class OpenMode(private val store: OpenModeStore, private val available: Boolean) {
    private var open: Boolean? = null
    private val listeners = mutableListOf<(Boolean) -> Unit>()

    val isOpen: Boolean
        @Synchronized get() = available && (open ?: store.read().also { open = it })

    @Synchronized
    fun set(value: Boolean) {
        if (!available || value == isOpen) return
        open = value
        store.write(value)
        listeners.toList().forEach { it(value) }
    }

    @Synchronized
    fun addListener(listener: (Boolean) -> Unit) {
        listeners += listener
    }
}

internal class PreferencesOpenModeStore(context: Context) : OpenModeStore {
    private val prefs = context.applicationContext.getSharedPreferences("mirlumi_allowlist", Context.MODE_PRIVATE)

    override fun read(): Boolean {
        val policy = StrictMode.allowThreadDiskReads()
        return try {
            prefs.getBoolean(KEY, false)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }

    override fun write(open: Boolean) {
        prefs.edit().putBoolean(KEY, open).apply()
    }

    private companion object {
        const val KEY = "open_mode"
    }
}
