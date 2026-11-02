/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.home.pocket

import android.content.Context

/** Utility singleton for providing helper functions related to content recommendations feature flags. */
object ContentRecommendationsFeatureHelper {
    /** Show Pocket sponsored stories in between Pocket recommended stories on home. */
    @Suppress("UNUSED_PARAMETER")
    fun isPocketSponsoredStoriesFeatureEnabled(context: Context): Boolean = false

    /**
     * Returns true if the current locale is part of the supported locales for content recommendations, and false
     * otherwise.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isContentRecommendationsFeatureEnabled(context: Context): Boolean = false
}
