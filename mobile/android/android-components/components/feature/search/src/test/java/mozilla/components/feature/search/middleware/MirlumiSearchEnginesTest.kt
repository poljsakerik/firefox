/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.feature.search.middleware

import org.junit.Assert.assertEquals
import org.junit.Test

class MirlumiSearchEnginesTest {
    @Test
    fun `partner parameters are removed and the query kept`() {
        assertEquals(
            "https://www.google.com/search?q=%7BsearchTerms%7D",
            "https://www.google.com/search?client=firefox-b-m&q=%7BsearchTerms%7D".withoutPartnerParameters(),
        )
        assertEquals(
            "https://www.bing.com/search?q=%7BsearchTerms%7D",
            "https://www.bing.com/search?pc=MOZB&form=MOZMBA&q=%7BsearchTerms%7D".withoutPartnerParameters(),
        )
        assertEquals(
            "https://duckduckgo.com/?q=%7BsearchTerms%7D",
            "https://duckduckgo.com/?t=fpas&q=%7BsearchTerms%7D".withoutPartnerParameters(),
        )
        assertEquals(
            "https://en.wikipedia.org/wiki/Special:Search?search=%7BsearchTerms%7D",
            "https://en.wikipedia.org/wiki/Special:Search?sourceid=Mozilla-search&search=%7BsearchTerms%7D"
                .withoutPartnerParameters(),
        )
    }

    @Test
    fun `urls without partner parameters are unchanged`() {
        val url = "https://www.youtube.com/results?search_query=%7BsearchTerms%7D"

        assertEquals(url, url.withoutPartnerParameters())
    }
}
