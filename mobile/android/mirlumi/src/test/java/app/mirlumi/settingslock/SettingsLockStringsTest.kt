/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mirlumi.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class SettingsLockStringsTest {
    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<android.content.Context>().resources

    private fun progress(entered: Int) =
        resources.getQuantityString(R.plurals.mirlumi_pin_progress, entered, entered, SettingsPin.LENGTH)

    private fun seconds(count: Int) = resources.getQuantityString(R.plurals.mirlumi_pin_seconds, count, count)

    @Test
    fun `English counts the digits entered`() {
        assertEquals("0 digits of 4 entered", progress(0))
        assertEquals("1 digit of 4 entered", progress(1))
        assertEquals("4 digits of 4 entered", progress(4))
        assertEquals("30 seconds", seconds(30))
    }

    @Test
    @Config(qualifiers = "sl")
    fun `Slovenian declines the digits entered and the lockout time`() {
        assertEquals("Vnesenih 0 števk od 4", progress(0))
        assertEquals("Vnesena 1 števka od 4", progress(1))
        assertEquals("Vneseni 2 števki od 4", progress(2))
        assertEquals("Vnesene 3 števke od 4", progress(3))
        assertEquals("Vnesene 4 števke od 4", progress(4))
        assertEquals("1 sekundo", seconds(1))
        assertEquals("2 sekundi", seconds(2))
        assertEquals("3 sekunde", seconds(3))
        assertEquals("30 sekund", seconds(30))
    }
}
