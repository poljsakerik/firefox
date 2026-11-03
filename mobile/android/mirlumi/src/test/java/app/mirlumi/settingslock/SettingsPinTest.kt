/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPinTest {
    private val store = FakePinStore()
    private var now = 1_000_000L
    private var elapsed = 5_000_000L
    private val pin = SettingsPin(store, now = { now }, elapsed = { elapsed })

    private fun advance(millis: Long) {
        now += millis
        elapsed += millis
    }

    @Test
    fun `stores a salted hash and never the PIN`() {
        pin.set("4821")
        val other = FakePinStore().also { SettingsPin(it).set("4821") }

        assertTrue(pin.isSet)
        assertFalse(store.values.values.any { "4821" in it })
        assertTrue(store.values["pin"] != other.values["pin"])
    }

    @Test
    fun `accepts only the PIN that was set`() {
        pin.set("4821")

        assertEquals(SettingsPin.Result.Correct, pin.check("4821"))
        assertEquals(SettingsPin.Result.Wrong, pin.check("4822"))
        assertEquals(SettingsPin.Result.Wrong, pin.check("482"))
        assertEquals("2", store.values["failures"])
    }

    @Test
    fun `nothing is correct before a PIN is set`() {
        assertFalse(pin.isSet)
        assertEquals(SettingsPin.Result.Wrong, pin.check("0000"))
    }

    @Test
    fun `rejects PINs that aren't four digits`() {
        listOf("", "123", "12345", "12a4").forEach {
            assertFalse(SettingsPin.isValid(it))
        }
        assertTrue(SettingsPin.isValid("0000"))
    }

    @Test
    fun `locks out after the free attempts, doubling each time, and a correct PIN resets`() {
        pin.set("4821")
        repeat(SettingsPin.FREE_ATTEMPTS) { pin.check("0000") }

        assertEquals(SettingsPin.Result.LockedOut(now + 30_000), pin.check("0000"))
        assertEquals(SettingsPin.Result.LockedOut(now + 30_000), pin.check("4821"))

        advance(30_000)
        assertNull(pin.lockedOutUntil())
        assertEquals(SettingsPin.Result.LockedOut(now + 60_000), pin.check("0000"))

        advance(60_000)
        assertEquals(SettingsPin.Result.Correct, pin.check("4821"))
        assertEquals(SettingsPin.Result.Wrong, pin.check("0000"))
        assertEquals("1", store.values["failures"])
    }

    @Test
    fun `the lockout never exceeds an hour`() {
        pin.set("4821")
        repeat(40) {
            pin.check("0000")
            pin.lockedOutUntil()?.let { advance(it - now) }
        }
        pin.check("0000")

        assertEquals(now + SettingsPin.MAX_LOCKOUT_MS, pin.lockedOutUntil())
    }

    @Test
    fun `changing the clock doesn't shorten or lengthen the lockout`() {
        pin.set("4821")
        repeat(SettingsPin.FREE_ATTEMPTS + 1) { pin.check("0000") }
        advance(10_000)

        now += 24 * 60 * 60_000L
        assertEquals(now + 20_000, pin.lockedOutUntil())
        assertTrue(pin.check("4821") is SettingsPin.Result.LockedOut)

        now -= 48 * 60 * 60_000L
        assertEquals(now + 20_000, pin.lockedOutUntil())
    }

    @Test
    fun `restarting the device doesn't end the lockout`() {
        pin.set("4821")
        repeat(SettingsPin.FREE_ATTEMPTS + 1) { pin.check("0000") }

        // Ten seconds after a restart, the elapsed clock is far below where the lockout began.
        elapsed = 10_000
        assertEquals(now + 20_000, pin.lockedOutUntil())

        elapsed = 30_000
        assertNull(pin.lockedOutUntil())
    }

    @Test
    fun `the attempt count survives a restart`() {
        pin.set("4821")
        repeat(SettingsPin.FREE_ATTEMPTS) { pin.check("0000") }

        val restarted = SettingsPin(store, now = { now }, elapsed = { elapsed })

        assertTrue(restarted.check("0000") is SettingsPin.Result.LockedOut)
    }

    @Test
    fun `setting a new PIN replaces the old one and clears the attempts`() {
        pin.set("4821")
        pin.check("0000")
        pin.set("1357")

        assertEquals(SettingsPin.Result.Wrong, pin.check("4821"))
        assertNull(store.values["lockout_until"])
        assertEquals("1", store.values["failures"])
        assertEquals(SettingsPin.Result.Correct, pin.check("1357"))
    }
}
