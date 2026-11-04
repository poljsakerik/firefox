/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.searchengines

import mozilla.components.browser.state.action.BrowserAction
import mozilla.components.browser.state.action.SearchAction
import mozilla.components.browser.state.search.SearchEngine
import mozilla.components.browser.state.state.BrowserState
import mozilla.components.lib.state.Middleware
import mozilla.components.lib.state.Store

/**
 * Keeps DuckDuckGo the only web search engine, so every search the browser starts goes to it, where safe search is
 * forced. Other bundled engines, engines added later and custom engines never reach the browser's state, so the address
 * bar's engine picker, the search widget and Settings offer DuckDuckGo alone. Searching tabs, bookmarks and history
 * stays.
 *
 * Must come before Android Components' `SearchMiddleware` in the store's middleware, so that what this drops is never
 * stored either.
 */
class OnlyDuckDuckGo : Middleware<BrowserState, BrowserAction> {
    override fun invoke(
        store: Store<BrowserState, BrowserAction>,
        next: (BrowserAction) -> Unit,
        action: BrowserAction,
    ) {
        when (action) {
            is SearchAction.SetSearchEnginesAction -> next(action.onlyDuckDuckGo())
            // Each of these would add another engine, choose one, or take DuckDuckGo away.
            is SearchAction.UpdateCustomSearchEngineAction,
            is SearchAction.AddAdditionalSearchEngineAction -> Unit
            is SearchAction.SelectSearchEngineAction -> if (action.searchEngineId == ENGINE_ID) next(action)
            is SearchAction.SelectPrivateSearchEngineAction -> if (action.searchEngineId == ENGINE_ID) next(action)
            is SearchAction.HideSearchEngineAction -> if (action.searchEngineId != ENGINE_ID) next(action)
            is SearchAction.UpdateDisabledSearchEngineIdsAction ->
                if (action.searchEngineId != ENGINE_ID || action.isEnabled) next(action)
            else -> next(action)
        }
    }

    companion object {
        /** DuckDuckGo's identifier in Firefox's search configuration, which offers it in every region and locale. */
        const val ENGINE_ID = "ddg"
    }
}

/**
 * The engines [this] sets, with DuckDuckGo the only one and the default, in normal and private tabs alike. DuckDuckGo
 * is taken from whichever list holds it, so it comes back if it was hidden.
 */
internal fun SearchAction.SetSearchEnginesAction.onlyDuckDuckGo(): SearchAction.SetSearchEnginesAction {
    val duckDuckGo: SearchEngine? =
        (regionSearchEngines + hiddenSearchEngines + additionalSearchEngines + additionalAvailableSearchEngines)
            .firstOrNull { it.id == OnlyDuckDuckGo.ENGINE_ID }
    return copy(
        regionSearchEngines = listOfNotNull(duckDuckGo),
        customSearchEngines = emptyList(),
        hiddenSearchEngines = emptyList(),
        disabledSearchEngineIds = disabledSearchEngineIds - OnlyDuckDuckGo.ENGINE_ID,
        additionalSearchEngines = emptyList(),
        additionalAvailableSearchEngines = emptyList(),
        userSelectedSearchEngineId = duckDuckGo?.id,
        userSelectedSearchEngineName = duckDuckGo?.name,
        userSelectedPrivateSearchEngineId = null,
        userSelectedPrivateSearchEngineName = null,
        regionDefaultSearchEngineId = OnlyDuckDuckGo.ENGINE_ID,
        regionSearchEnginesOrder = listOfNotNull(duckDuckGo?.id),
    )
}
