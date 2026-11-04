/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenModeTest {
    private class FakeStore(var value: Boolean = false) : OpenModeStore {
        var writes = 0

        override fun read() = value

        override fun write(open: Boolean) {
            value = open
            writes++
        }
    }

    @Test
    fun `open mode is off until a parent turns it on`() {
        val store = FakeStore()
        val mode = OpenMode(store, available = true)
        assertFalse(mode.isOpen)
        mode.set(true)
        assertTrue(mode.isOpen)
        assertTrue(store.value)
        assertTrue(OpenMode(store, available = true).isOpen)
    }

    @Test
    fun `without the page classifier open mode stays off, even if it was stored on`() {
        val store = FakeStore(value = true)
        val mode = OpenMode(store, available = false)
        assertFalse(mode.isOpen)
        mode.set(true)
        assertFalse(mode.isOpen)
        assertEquals(0, store.writes)
    }

    @Test
    fun `listeners hear changes, and only changes`() {
        val heard = mutableListOf<Boolean>()
        val store = FakeStore()
        val mode = OpenMode(store, available = true)
        mode.addListener { heard += it }
        mode.set(false)
        mode.set(true)
        mode.set(true)
        mode.set(false)
        assertEquals(listOf(true, false), heard)
        assertEquals(2, store.writes)
    }
}
