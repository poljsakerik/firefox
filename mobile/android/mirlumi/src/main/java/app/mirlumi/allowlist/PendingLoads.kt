/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import androidx.annotation.MainThread
import java.util.WeakHashMap

/**
 * The last page each session was refused while the extension started, so it can be loaded again once the extension can
 * judge it. Sessions are held weakly, and a later top-level load in a session replaces or [forget]s its page. Used from
 * GeckoView's callbacks, all on the main thread.
 */
@MainThread
internal class PendingLoads<S : Any> {
    private val urls = WeakHashMap<S, String>()

    val isEmpty: Boolean
        get() = urls.isEmpty()

    fun remember(session: S, url: String) {
        urls[session] = url
    }

    fun forget(session: S) {
        urls.remove(session)
    }

    /** Hands each remembered page whose session [isLive] to [load], once, and forgets them all. */
    fun resume(isLive: (S) -> Boolean, load: (S, String) -> Unit) {
        val pending = urls.toMap()
        urls.clear()
        pending.forEach { (session, url) -> if (isLive(session)) load(session, url) }
    }
}
