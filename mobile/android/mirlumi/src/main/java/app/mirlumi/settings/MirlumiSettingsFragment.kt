/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settings

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import app.mirlumi.R

/**
 * The first Settings screen: Mirlumi's own settings, each shown while its feature is on, and a last row that opens the
 * browser's settings through the navigation action [R.id.mirlumi_action_browser_settings]. The app's navigation graph
 * declares it as [R.id.mirlumi_settings], with a subclass that shows the app's toolbar.
 */
abstract class MirlumiSettingsFragment : PreferenceFragmentCompat() {
    /** The layout for section headers, or 0 for the theme's. */
    @get:LayoutRes protected open val categoryLayout: Int = 0

    /** Shows the activity's toolbar with [title]. */
    protected abstract fun showTitle(title: String)

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.mirlumi_settings, rootKey)
        prepareCategories(preferenceScreen, categoryLayout)
        findPreference<Preference>(BROWSER_SETTINGS_KEY)?.setOnPreferenceClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.mirlumi_settings) {
                navController.navigate(R.id.mirlumi_action_browser_settings)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        showTitle(getString(R.string.mirlumi_settings_title))
    }

    internal companion object {
        const val BROWSER_SETTINGS_KEY = "mirlumi_browser_settings"

        /** Gives [screen]'s sections [categoryLayout], unless it's 0, and hides the sections with no rows showing. */
        fun prepareCategories(screen: PreferenceGroup, @LayoutRes categoryLayout: Int) {
            for (index in 0 until screen.preferenceCount) {
                val category = screen.getPreference(index) as? PreferenceCategory ?: continue
                if (categoryLayout != 0) category.layoutResource = categoryLayout
                category.isVisible = (0 until category.preferenceCount).any { category.getPreference(it).isVisible }
            }
        }
    }
}
