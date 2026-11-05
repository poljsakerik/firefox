/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import android.content.Context
import android.os.Bundle
import android.os.StrictMode
import androidx.annotation.VisibleForTesting
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.DialogFragmentNavigator
import androidx.navigation.fragment.FragmentNavigator
import kotlin.concurrent.thread
import mozilla.components.lib.dataprotect.SecureAbove22Preferences

/**
 * Keeps Settings, and every other screen that changes the browser, behind a parent's PIN. The PIN is chosen once
 * onboarding is over, and can't be skipped. Entering it opens the locked screens until the user leaves them or the app
 * goes to the background.
 */
object SettingsLock {
    private const val UNLOCK_RESULT = "app.mirlumi.settingslock.unlock"

    private var unlocked = false
    private var pendingAuthorization: Pair<() -> Unit, () -> Unit>? = null
    private var pin: SettingsPin? = null
    private val guards = mutableMapOf<FragmentActivity, Guard>()

    /** Whether Settings are behind the PIN, as passed to [install]. */
    var isEnabled = false
        @VisibleForTesting internal set

    /**
     * Watches [navController] for locked screens and asks for the PIN, or for a first PIN once [isOnboarded]. Call from
     * the activity's `onCreate`.
     */
    fun install(
        activity: FragmentActivity,
        navController: NavController,
        enabled: Boolean,
        isOnboarded: () -> Boolean,
    ) {
        isEnabled = enabled
        if (!enabled) return
        val context = activity.applicationContext
        thread(name = "mirlumi-settings-lock") { pin(context).isSet }
        val guard = Guard(activity, navController, isOnboarded)
        guards[activity] = guard
        navController.addOnDestinationChangedListener(guard)
        activity.lifecycle.addObserver(guard)
        activity.supportFragmentManager.setFragmentResultListener(UNLOCK_RESULT, activity) { _, _ ->
            unlocked = true
            guard.evaluate()
        }
    }

    /**
     * Runs [action] once the PIN is entered, or straight away when the lock is off or already open. Runs [onDenied]
     * instead if the PIN screen closes without it, or another request replaces this one.
     */
    fun authorize(fragmentManager: FragmentManager, onDenied: () -> Unit = {}, action: () -> Unit) {
        if (!isEnabled || unlocked) {
            action()
            return
        }
        pendingAuthorization?.second?.invoke()
        pendingAuthorization = action to onDenied
        PinDialogFragment.show(fragmentManager, PinPurpose.AUTHORIZE)
    }

    @Synchronized
    internal fun pin(context: Context): SettingsPin =
        pin ?: SettingsPin(KeystorePinStore(context.applicationContext)).also { pin = it }

    /** Called by the PIN screen when it closes, [succeeded] or not. */
    internal fun onPinScreenClosed(purpose: PinPurpose, activity: FragmentActivity, succeeded: Boolean) {
        when (purpose) {
            PinPurpose.UNLOCK,
            PinPurpose.SETUP ->
                if (succeeded) {
                    activity.supportFragmentManager.setFragmentResult(UNLOCK_RESULT, Bundle())
                } else {
                    guards[activity]?.leave()
                }
            PinPurpose.AUTHORIZE -> {
                val (action, onDenied) = pendingAuthorization ?: return
                pendingAuthorization = null
                if (succeeded) action() else onDenied()
            }
            PinPurpose.CHANGE -> Unit
        }
    }

    private fun lock() {
        unlocked = false
    }

    private class Guard(
        private val activity: FragmentActivity,
        private val navController: NavController,
        private val isOnboarded: () -> Boolean,
    ) : NavController.OnDestinationChangedListener, DefaultLifecycleObserver {
        override fun onDestinationChanged(controller: NavController, destination: NavDestination, arguments: Bundle?) {
            evaluate()
        }

        override fun onStart(owner: LifecycleOwner) {
            evaluate()
        }

        override fun onStop(owner: LifecycleOwner) {
            if (!activity.isChangingConfigurations) lock()
        }

        override fun onDestroy(owner: LifecycleOwner) {
            navController.removeOnDestinationChangedListener(this)
            guards.remove(activity)
        }

        fun evaluate() {
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
            val screen = navController.currentDestination?.className() ?: return
            val fragments = activity.supportFragmentManager
            val pin = pin(activity)
            if (LockedScreens.isLocked(screen)) {
                if (!unlocked) {
                    PinDialogFragment.show(fragments, if (pin.isSet) PinPurpose.UNLOCK else PinPurpose.SETUP)
                }
                return
            }
            lock()
            PinDialogFragment.dismissOptional(fragments)
            if (!pin.isSet && !LockedScreens.isStartScreen(screen) && isOnboarded()) {
                PinDialogFragment.show(fragments, PinPurpose.SETUP, required = true)
            }
        }

        /** Goes back past the locked screens, after the PIN screen was dismissed without the PIN. */
        fun leave() {
            while (navController.currentDestination?.className()?.let(LockedScreens::isLocked) == true) {
                if (!navController.popBackStack()) {
                    activity.finish()
                    return
                }
            }
        }
    }

    private fun NavDestination.className(): String? =
        when (this) {
            is FragmentNavigator.Destination -> className
            is DialogFragmentNavigator.Destination -> className
            else -> null
        }
}

/**
 * Keystore-encrypted preferences, read once into memory. The first read touches the disk, so [SettingsLock.install]
 * starts it in the background; if the lock needs it sooner, the main thread reads it, explicitly allowed.
 */
private class KeystorePinStore(context: Context) : PinStore {
    private val values: MutableMap<String, String> by lazy {
        allowingDiskAccess {
            prefs = SecureAbove22Preferences(context, "mirlumi_settings_lock")
            prefs.all().toMutableMap()
        }
    }
    private lateinit var prefs: SecureAbove22Preferences

    @Synchronized override fun get(key: String): String? = values[key]

    @Synchronized
    override fun put(key: String, value: String) {
        values[key] = value
        allowingDiskAccess { prefs.putString(key, value) }
    }

    @Synchronized
    override fun remove(key: String) {
        values.remove(key)
        allowingDiskAccess { prefs.remove(key) }
    }

    private inline fun <T> allowingDiskAccess(block: () -> T): T {
        val policy = StrictMode.allowThreadDiskWrites()
        return try {
            block()
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
    }
}
