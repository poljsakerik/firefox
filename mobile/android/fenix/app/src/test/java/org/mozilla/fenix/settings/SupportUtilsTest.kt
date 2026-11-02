/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportUtilsTest {

    private fun mockContext(versionName: String): Context {
        val context: Context = mockk()
        val packageManager: PackageManager = mockk()
        val packageInfo = PackageInfo()

        every { context.packageName } returns "org.mozilla.fenix"
        every { context.packageManager } returns packageManager
        @Suppress("DEPRECATION")
        every { packageManager.getPackageInfo("org.mozilla.fenix", 0) } returns packageInfo
        packageInfo.versionName = versionName

        return context
    }
}
