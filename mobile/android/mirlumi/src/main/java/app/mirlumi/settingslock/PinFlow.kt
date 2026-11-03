/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

/** What the PIN screen is for. */
internal enum class PinPurpose {
    /** Opening a locked screen. */
    UNLOCK,

    /** One action, such as installing an extension from a web page. */
    AUTHORIZE,

    /** Choosing the first PIN. */
    SETUP,

    /** Replacing the PIN from Settings. */
    CHANGE,
}

/** The steps of entering, choosing and confirming a PIN, with no UI. */
internal class PinFlow(
    private val pin: SettingsPin,
    purpose: PinPurpose,
) {
    enum class Step {
        CURRENT,
        CHOOSE,
        CONFIRM,
    }

    sealed interface Message {
        data object Wrong : Message

        data class LockedOut(val untilMillis: Long) : Message

        data object Mismatch : Message
    }

    data class State(
        val step: Step,
        val digits: Int = 0,
        val message: Message? = null,
        val done: Boolean = false,
    )

    private val choosesFirstPin = purpose == PinPurpose.SETUP || !pin.isSet
    private val choosesNewPin = choosesFirstPin || purpose == PinPurpose.CHANGE
    private var entered = ""
    private var chosen = ""

    var state = State(step = if (choosesFirstPin) Step.CHOOSE else Step.CURRENT)
        private set

    init {
        refreshLockout()
    }

    val isLockedOut: Boolean
        get() = state.message is Message.LockedOut

    fun type(digit: Char) {
        if (!acceptsDigits() || digit !in '0'..'9') return
        entered += digit
        state = state.copy(digits = entered.length, message = if (entered.length == 1) null else state.message)
        if (entered.length == SettingsPin.LENGTH) submit()
    }

    private fun acceptsDigits(): Boolean = !state.done && !isLockedOut && entered.length < SettingsPin.LENGTH

    fun delete() {
        if (state.done || entered.isEmpty()) return
        entered = entered.dropLast(1)
        state = state.copy(digits = entered.length)
    }

    /** Clears an expired lockout, or shows one that began elsewhere. */
    fun refreshLockout() {
        if (state.step != Step.CURRENT) return
        val until = pin.lockedOutUntil()
        state =
            when {
                until != null -> state.copy(message = Message.LockedOut(until))
                isLockedOut -> state.copy(message = null)
                else -> state
            }
    }

    private fun submit() {
        val value = entered
        entered = ""
        state =
            when (state.step) {
                Step.CURRENT ->
                    when (val result = pin.check(value)) {
                        SettingsPin.Result.Correct ->
                            if (choosesNewPin) State(Step.CHOOSE) else state.copy(digits = 0, done = true)
                        is SettingsPin.Result.Wrong -> state.copy(digits = 0, message = Message.Wrong)
                        is SettingsPin.Result.LockedOut ->
                            state.copy(digits = 0, message = Message.LockedOut(result.untilMillis))
                    }
                Step.CHOOSE -> {
                    chosen = value
                    State(Step.CONFIRM)
                }
                Step.CONFIRM ->
                    if (value == chosen) {
                        pin.set(value)
                        state.copy(digits = 0, done = true)
                    } else {
                        State(Step.CHOOSE, message = Message.Mismatch)
                    }
            }
    }
}
