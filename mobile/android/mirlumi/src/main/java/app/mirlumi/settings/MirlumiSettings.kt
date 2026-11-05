/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settings

import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.navigation.NavAction
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.preference.Preference
import androidx.preference.PreferenceGroup

/**
 * Opens Settings on [MirlumiSettingsFragment] rather than the browser's settings, which it leads to. While it's off,
 * the app's navigation graph keeps its own route to Settings, and Mirlumi's rows stay in the browser's settings.
 */
object MirlumiSettings {
    /** Whether Settings opens on Mirlumi's screen, as passed to [install]. */
    var isEnabled = false
        @VisibleForTesting internal set

    /** The keys of Mirlumi's rows, which the browser's settings show only while Mirlumi's screen is off. */
    @VisibleForTesting
    internal val rowKeys = listOf("mirlumi_allowed_sites", "mirlumi_open_mode", "mirlumi_settings_pin")

    /**
     * Points [settingsAction], the app's action to its settings, at [screen], the destination for Mirlumi's screen, in
     * every graph [navController] shows. Call from the activity's `onCreate`.
     */
    fun install(navController: NavController, enabled: Boolean, @IdRes settingsAction: Int, @IdRes screen: Int) {
        isEnabled = enabled
        if (!enabled) return
        navController.addOnDestinationChangedListener { controller, _, _ ->
            route(controller.graph, settingsAction, screen)
        }
    }

    /** Hides Mirlumi's rows in [settings], the browser's settings, while they're on Mirlumi's screen. */
    fun hideMovedRows(settings: PreferenceGroup) {
        if (!isEnabled) return
        rowKeys.forEach { settings.findPreference<Preference>(it)?.isVisible = false }
    }

    @VisibleForTesting
    internal fun route(graph: NavGraph, @IdRes settingsAction: Int, @IdRes screen: Int) {
        val action = graph.getAction(settingsAction) ?: return
        if (action.destinationId != screen) {
            graph.putAction(settingsAction, NavAction(screen, action.navOptions, action.defaultArguments))
        }
    }
}
