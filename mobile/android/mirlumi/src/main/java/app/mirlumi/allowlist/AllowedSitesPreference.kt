/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import androidx.fragment.app.FragmentActivity
import androidx.preference.Preference
import app.mirlumi.R

/** The Settings row that lists the sites a parent allowed. Declare it in a preference screen's XML. */
class AllowedSitesPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    init {
        setTitle(R.string.mirlumi_allowed_sites_title)
        setSummary(R.string.mirlumi_allowed_sites_summary)
        isIconSpaceReserved = false
        isVisible = Allowlist.isEnabled
    }

    override fun onClick() {
        val activity =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .firstNotNullOfOrNull { it as? FragmentActivity } ?: return
        AllowedSitesDialogFragment.show(activity.supportFragmentManager)
    }
}
