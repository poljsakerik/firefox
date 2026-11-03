/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import android.os.SystemClock
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Where [SettingsPin] keeps its values. Production storage is encrypted with a key in the Android Keystore. */
interface PinStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/**
 * The parent's PIN for Settings. Only a salted PBKDF2 hash is stored. After [FREE_ATTEMPTS] wrong entries in a row,
 * each further wrong entry locks entry for twice as long as the last, from [FIRST_LOCKOUT_MS] up to [MAX_LOCKOUT_MS].
 * The count survives restarts, and only a correct entry resets it.
 *
 * A lockout is measured on [elapsed], the time since the device started, which keeps counting during sleep and doesn't
 * move when someone changes the clock. [now] only turns what's left of it into a time to show.
 */
class SettingsPin(
    private val store: PinStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
) {
    sealed interface Result {
        data object Correct : Result

        data object Wrong : Result

        data class LockedOut(val untilMillis: Long) : Result
    }

    val isSet: Boolean
        get() = store.get(KEY_PIN) != null

    fun set(pin: String) {
        require(isValid(pin)) { "A PIN is $LENGTH digits" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val encoder = Base64.getEncoder()
        store.put(KEY_PIN, "$ITERATIONS:${encoder.encodeToString(salt)}:${encoder.encodeToString(hash(pin, salt, ITERATIONS))}")
        resetAttempts()
    }

    /** When entry opens again, or null if it's open now. */
    fun lockedOutUntil(): Long? = remainingLockout()?.let { now() + it }

    private fun remainingLockout(): Long? {
        val started = store.get(KEY_LOCKOUT_STARTED)?.toLongOrNull() ?: return null
        val length = store.get(KEY_LOCKOUT_LENGTH)?.toLongOrNull() ?: return null
        val clock = elapsed()
        // The elapsed clock starts again at zero when the device restarts. Everything since then came after the
        // lockout began, so counting only that time can make a lockout longer, never shorter.
        val served = if (clock >= started) clock - started else clock
        return (length - served).takeIf { it > 0 }
    }

    fun check(pin: String): Result {
        lockedOutUntil()?.let { return Result.LockedOut(it) }
        val stored = store.get(KEY_PIN)?.split(":") ?: return Result.Wrong
        val decoder = Base64.getDecoder()
        val matches =
            isValid(pin) &&
                stored.size == 3 &&
                MessageDigest.isEqual(
                    hash(pin, decoder.decode(stored[1]), stored[0].toInt()),
                    decoder.decode(stored[2]),
                )
        if (matches) {
            resetAttempts()
            return Result.Correct
        }
        val failures = (store.get(KEY_FAILURES)?.toIntOrNull() ?: 0) + 1
        store.put(KEY_FAILURES, failures.toString())
        if (failures <= FREE_ATTEMPTS) {
            return Result.Wrong
        }
        val lockout = (FIRST_LOCKOUT_MS shl minOf(failures - FREE_ATTEMPTS - 1, MAX_DOUBLINGS)).coerceAtMost(MAX_LOCKOUT_MS)
        store.put(KEY_LOCKOUT_STARTED, elapsed().toString())
        store.put(KEY_LOCKOUT_LENGTH, lockout.toString())
        return Result.LockedOut(now() + lockout)
    }

    private fun resetAttempts() {
        store.remove(KEY_FAILURES)
        store.remove(KEY_LOCKOUT_STARTED)
        store.remove(KEY_LOCKOUT_LENGTH)
    }

    private fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        const val LENGTH = 4
        const val FREE_ATTEMPTS = 5
        const val FIRST_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 60 * 60_000L

        private const val MAX_DOUBLINGS = 10
        private const val ITERATIONS = 20_000
        private const val SALT_BYTES = 16
        private const val HASH_BITS = 256
        private const val KEY_PIN = "pin"
        private const val KEY_FAILURES = "failures"
        private const val KEY_LOCKOUT_STARTED = "lockout_started"
        private const val KEY_LOCKOUT_LENGTH = "lockout_length"

        fun isValid(pin: String): Boolean = pin.length == LENGTH && pin.all { it in '0'..'9' }
    }
}
