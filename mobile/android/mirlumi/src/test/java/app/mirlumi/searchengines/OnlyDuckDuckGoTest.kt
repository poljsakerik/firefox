/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.searchengines

import android.graphics.Bitmap
import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.browser.state.state.availableSearchEngines
import mozilla.components.browser.state.state.searchEngines
import mozilla.components.browser.state.state.selectedOrDefaultSearchEngine
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlyDuckDuckGoTest {
    private val icon = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    private fun engine(id: String, type: SearchEngine.Type = SearchEngine.Type.BUNDLED) =
        SearchEngine(
            id = id,
            name = id,
            icon = icon,
            type = type,
            resultUrls = listOf("https://$id.example/?q={searchTerms}"),
        )

    private val ddg = engine("ddg")
    private val google = engine("google")
    private val bing = engine("bing")
    private val custom = engine("custom", SearchEngine.Type.CUSTOM)

    /** Records what reaches the middleware after [OnlyDuckDuckGo], as Android Components' SearchMiddleware would. */
    private class Recorder : Middleware<BrowserState, BrowserAction> {
        val seen = mutableListOf<BrowserAction>()

        override fun invoke(
            store: Store<BrowserState, BrowserAction>,
            next: (BrowserAction) -> Unit,
            action: BrowserAction,
        ) {
            if (action is SearchAction) seen += action
            next(action)
        }
    }

    private val recorder = Recorder()
    private val store = BrowserStore(middleware = listOf(OnlyDuckDuckGo(), recorder))

    private fun setEngines(
        region: List<SearchEngine> = listOf(google, ddg, bing),
        hidden: List<SearchEngine> = emptyList(),
        userSelected: String? = "google",
    ) =
        store.dispatch(
            SearchAction.SetSearchEnginesAction(
                regionSearchEngines = region,
                customSearchEngines = listOf(custom),
                hiddenSearchEngines = hidden,
                disabledSearchEngineIds = listOf("ddg", "bing"),
                additionalSearchEngines = listOf(engine("wikipedia", SearchEngine.Type.BUNDLED_ADDITIONAL)),
                additionalAvailableSearchEngines = listOf(engine("youtube", SearchEngine.Type.BUNDLED_ADDITIONAL)),
                userSelectedSearchEngineId = userSelected,
                userSelectedSearchEngineName = userSelected,
                userSelectedPrivateSearchEngineId = "bing",
                userSelectedPrivateSearchEngineName = "bing",
                regionDefaultSearchEngineId = "google",
                regionSearchEnginesOrder = listOf("google", "ddg", "bing"),
                searchEnginesConfigurationId = 1,
            )
        )

    @Test
    fun `DuckDuckGo is the only engine and the default, in private tabs too`() {
        setEngines()
        val search = store.state.search
        assertEquals(listOf(ddg), search.searchEngines)
        assertEquals(listOf(ddg), search.regionSearchEngines)
        assertTrue(search.customSearchEngines.isEmpty())
        assertTrue(search.additionalSearchEngines.isEmpty())
        assertTrue(search.availableSearchEngines.isEmpty())
        assertTrue(search.hiddenSearchEngines.isEmpty())
        assertEquals(listOf("bing"), search.disabledSearchEngineIds)
        assertEquals(ddg, store.state.search.selectedOrDefaultSearchEngine)
        assertEquals("ddg", search.userSelectedSearchEngineId)
        assertEquals(null, search.userSelectedPrivateSearchEngineId)
        assertEquals("ddg", search.regionDefaultSearchEngineId)
    }

    @Test
    fun `DuckDuckGo comes back when it was hidden`() {
        setEngines(region = listOf(google, bing), hidden = listOf(ddg))
        assertEquals(listOf(ddg), store.state.search.searchEngines)
        assertTrue(store.state.search.hiddenSearchEngines.isEmpty())
    }

    @Test
    fun `no engine is added or chosen in DuckDuckGo's place`() {
        setEngines()
        store.dispatch(SearchAction.UpdateCustomSearchEngineAction(custom))
        store.dispatch(SearchAction.AddAdditionalSearchEngineAction("youtube"))
        store.dispatch(SearchAction.SelectSearchEngineAction("google", "google"))
        store.dispatch(SearchAction.SelectPrivateSearchEngineAction("bing", "bing"))
        store.dispatch(SearchAction.HideSearchEngineAction("ddg"))
        store.dispatch(SearchAction.UpdateDisabledSearchEngineIdsAction("ddg", isEnabled = false))

        assertEquals(listOf(ddg), store.state.search.searchEngines)
        assertEquals(ddg, store.state.search.selectedOrDefaultSearchEngine)
        assertEquals(listOf("bing"), store.state.search.disabledSearchEngineIds)
        assertEquals(
            "SearchMiddleware never sees, so never stores, what was dropped",
            listOf(SearchAction.SetSearchEnginesAction::class),
            recorder.seen.map { it::class },
        )
    }

    @Test
    fun `choosing DuckDuckGo and other search actions pass`() {
        setEngines()
        store.dispatch(SearchAction.SelectSearchEngineAction("ddg", "ddg"))
        store.dispatch(SearchAction.UpdateDisabledSearchEngineIdsAction("bing", isEnabled = true))
        store.dispatch(SearchAction.RefreshSearchEnginesAction)
        assertEquals(4, recorder.seen.size)
        assertEquals(ddg, store.state.search.selectedOrDefaultSearchEngine)
    }
}
