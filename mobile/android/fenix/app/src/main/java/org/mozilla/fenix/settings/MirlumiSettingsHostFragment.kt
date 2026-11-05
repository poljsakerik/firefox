/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import app.mirlumi.settings.MirlumiSettingsFragment
import org.mozilla.fenix.R
import org.mozilla.fenix.e2e.SystemInsetsPaddedFragment
import org.mozilla.fenix.ext.showToolbar

/** Mirlumi's Settings screen with Fenix's toolbar and section headers. */
class MirlumiSettingsHostFragment : MirlumiSettingsFragment(), SystemInsetsPaddedFragment {
    override val categoryLayout = R.layout.preference_category_no_icon_style

    override fun showTitle(title: String) = showToolbar(title)
}
