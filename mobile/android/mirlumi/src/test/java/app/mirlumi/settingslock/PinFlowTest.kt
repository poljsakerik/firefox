/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinFlowTest {
    private var now = 1_000_000L
    private val pin = SettingsPin(FakePinStore(), now = { now }, elapsed = { now })

    private fun PinFlow.enter(digits: String) = digits.forEach(::type)

    @Test
    fun `setup asks for the PIN twice and stores it`() {
        val flow = PinFlow(pin, PinPurpose.SETUP)
        assertEquals(PinFlow.Step.CHOOSE, flow.state.step)

        flow.enter("2468")
        assertEquals(PinFlow.Step.CONFIRM, flow.state.step)
        assertFalse(pin.isSet)

        flow.enter("2468")
        assertTrue(flow.state.done)
        assertEquals(SettingsPin.Result.Correct, pin.check("2468"))
    }

    @Test
    fun `setup starts again when the two PINs differ`() {
        val flow = PinFlow(pin, PinPurpose.SETUP)
        flow.enter("2468")
        flow.enter("2469")

        assertEquals(PinFlow.State(PinFlow.Step.CHOOSE, message = PinFlow.Message.Mismatch), flow.state)
        assertFalse(pin.isSet)

        flow.type('1')
        assertEquals(null, flow.state.message)
    }

    @Test
    fun `unlocking without a PIN chooses one instead`() {
        assertEquals(PinFlow.Step.CHOOSE, PinFlow(pin, PinPurpose.UNLOCK).state.step)
        assertEquals(PinFlow.Step.CHOOSE, PinFlow(pin, PinPurpose.AUTHORIZE).state.step)
    }

    @Test
    fun `unlock finishes on the right PIN only`() {
        pin.set("2468")
        val flow = PinFlow(pin, PinPurpose.UNLOCK)
        assertEquals(PinFlow.Step.CURRENT, flow.state.step)

        flow.enter("1111")
        assertEquals(PinFlow.State(PinFlow.Step.CURRENT, message = PinFlow.Message.Wrong), flow.state)

        flow.enter("2468")
        assertTrue(flow.state.done)
    }

    @Test
    fun `delete removes the last digit`() {
        pin.set("2468")
        val flow = PinFlow(pin, PinPurpose.UNLOCK)
        flow.enter("247")
        flow.delete()
        flow.enter("68")

        assertTrue(flow.state.done)
    }

    @Test
    fun `change checks the current PIN before choosing a new one`() {
        pin.set("2468")
        val flow = PinFlow(pin, PinPurpose.CHANGE)
        flow.enter("2468")
        assertEquals(PinFlow.Step.CHOOSE, flow.state.step)

        flow.enter("1357")
        flow.enter("1357")

        assertTrue(flow.state.done)
        assertEquals(SettingsPin.Result.Correct, pin.check("1357"))
    }

    @Test
    fun `a lockout blocks typing until it ends`() {
        pin.set("2468")
        val flow = PinFlow(pin, PinPurpose.UNLOCK)
        repeat(SettingsPin.FREE_ATTEMPTS + 1) { flow.enter("1111") }
        assertTrue(flow.isLockedOut)

        flow.enter("2468")
        assertFalse(flow.state.done)
        assertEquals(0, flow.state.digits)

        now += SettingsPin.FIRST_LOCKOUT_MS
        flow.refreshLockout()
        flow.enter("2468")
        assertTrue(flow.state.done)
    }

    @Test
    fun `a lockout from an earlier visit shows at once`() {
        pin.set("2468")
        repeat(SettingsPin.FREE_ATTEMPTS + 1) { pin.check("1111") }

        assertTrue(PinFlow(pin, PinPurpose.UNLOCK).isLockedOut)
    }
}
