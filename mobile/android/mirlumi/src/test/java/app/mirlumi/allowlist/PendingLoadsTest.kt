/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingLoadsTest {
    private class Session

    @Test
    fun `each live session gets its latest page once`() {
        val pending = PendingLoads<Session>()
        val a = Session()
        val b = Session()
        val closed = Session()
        val forgotten = Session()
        pending.remember(a, "https://a.example/1")
        pending.remember(a, "https://a.example/2")
        pending.remember(b, "https://b.example/")
        pending.remember(closed, "https://c.example/")
        pending.remember(forgotten, "https://d.example/")
        pending.forget(forgotten)

        val loaded = mutableListOf<Pair<Session, String>>()
        pending.resume(isLive = { it !== closed }) { session, url -> loaded += session to url }
        assertEquals(setOf(a to "https://a.example/2", b to "https://b.example/"), loaded.toSet())
        assertEquals(2, loaded.size)
        assertTrue(pending.isEmpty)

        pending.resume(isLive = { true }) { session, url -> loaded += session to url }
        assertEquals(2, loaded.size)
    }
}
