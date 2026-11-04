/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllowlistMessagesTest {
    @Test
    fun `an allow request carries its id and entry`() {
        val request = AllowlistMessages.allowRequest(JSONObject("""{"type":"allow","id":3,"entry":"example.com"}"""))
        assertEquals(AllowlistMessages.AllowRequest(3, "example.com"), request)
    }

    @Test
    fun `malformed requests are ignored`() {
        for (message in
            listOf(
                """{"type":"allow","id":3}""",
                """{"type":"allow","entry":"example.com"}""",
                """{"type":"allow","id":3,"entry":"not an entry"}""",
                """{"type":"other","id":3,"entry":"example.com"}""",
            )) {
            assertNull(message, AllowlistMessages.allowRequest(JSONObject(message)))
        }
        assertNull(AllowlistMessages.allowRequest("allow"))
    }

    @Test
    fun `results say whether the PIN was entered and whether asking failed`() {
        val granted = AllowlistMessages.allowResult(3, AllowlistMessages.Outcome.GRANTED)
        assertEquals("allowResult", granted.getString("type"))
        assertEquals(3, granted.getInt("id"))
        assertTrue(granted.getBoolean("granted"))
        assertFalse(granted.getBoolean("failed"))
        assertFalse(AllowlistMessages.allowResult(3, AllowlistMessages.Outcome.DENIED).getBoolean("failed"))
        assertTrue(AllowlistMessages.allowResult(3, AllowlistMessages.Outcome.FAILED).getBoolean("failed"))
    }

    @Test
    fun `the parent's sites go to the extension as a list`() {
        val message = AllowlistMessages.parentSites(listOf("a.org", "b.org"))
        assertEquals("parentSites", message.getString("type"))
        assertEquals(2, message.getJSONArray("sites").length())
    }

    @Test
    fun `the open mode setting goes to the extension as a message`() {
        val message = AllowlistMessages.mode(true)
        assertEquals("mode", message.getString("type"))
        assertTrue(message.getBoolean("open"))
        assertFalse(AllowlistMessages.mode(false).getBoolean("open"))
    }
}
