/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import app.mirlumi.R

/**
 * The Settings switch for open mode: [OpenMode]. Declare it in a preference screen's XML. Settings are behind the PIN,
 * so only a parent reaches it. Hidden when allowlist-only browsing or the page classifier is off in this build.
 */
class OpenModePreference(context: Context, attrs: AttributeSet?) : SwitchPreferenceCompat(context, attrs) {
    init {
        setTitle(R.string.mirlumi_open_mode_title)
        setSummary(R.string.mirlumi_open_mode_summary)
        isIconSpaceReserved = false
        isPersistent = false
        isVisible = Allowlist.isEnabled && Allowlist.isOpenModeAvailable
        isChecked = isVisible && Allowlist.openMode(context).isOpen
        setOnPreferenceChangeListener { _, value ->
            Allowlist.openMode(context).set(value == true)
            true
        }
    }
}
