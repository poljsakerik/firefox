/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentSitesTest {
    private class MemoryStore(var saved: Set<String> = emptySet()) : SiteStore {
        override fun read() = saved

        override fun write(sites: Set<String>) {
            saved = sites
        }
    }

    @Test
    fun `added sites are kept sorted and saved`() {
        val store = MemoryStore()
        val sites = ParentSites(store)
        assertTrue(sites.add("zoo.example.org"))
        assertTrue(sites.add("example.com"))
        assertFalse(sites.add("example.com"))
        assertEquals(listOf("example.com", "zoo.example.org"), sites.sites)
        assertEquals(setOf("example.com", "zoo.example.org"), store.saved)
    }

    @Test
    fun `removing a site saves and tells listeners`() {
        val store = MemoryStore(setOf("example.com", "example.org"))
        val sites = ParentSites(store)
        var heard: List<String>? = null
        sites.addListener { heard = it }
        assertTrue(sites.remove("example.com"))
        assertFalse(sites.remove("example.com"))
        assertEquals(listOf("example.org"), heard)
        assertEquals(setOf("example.org"), store.saved)
    }

    @Test
    fun `anything that can't be an entry is refused, also when read back`() {
        val sites = ParentSites(MemoryStore(setOf("example.com", "not an entry", "")))
        assertEquals(listOf("example.com"), sites.sites)
        for (bad in listOf("", "com", "Example.com", "example.com/", "example.com/../x", "a b.com", "*.example.com")) {
            assertFalse(bad, sites.add(bad))
        }
        assertTrue(sites.add("bbc.co.uk/cbeebies"))
    }
}
