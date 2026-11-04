/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.allowlist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import app.mirlumi.R
import mozilla.components.compose.base.button.TextButton
import mozilla.components.compose.base.theme.AcornTheme

/** The sites a parent allowed, each with a way to remove it. Opened from Settings, so it's behind the PIN already. */
class AllowedSitesDialogFragment : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.MirlumiPinDialog)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    // Settings locks again in the background; this screen goes with it rather than staying open over the PIN.
    override fun onStop() {
        super.onStop()
        if (!requireActivity().isChangingConfigurations) dismissAllowingStateLoss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val parentSites = Allowlist.parentSites(requireContext())
            setContent {
                AcornTheme {
                    var sites by remember { mutableStateOf(parentSites.sites) }
                    DisposableEffect(parentSites) {
                        val listener: (List<String>) -> Unit = { sites = it }
                        parentSites.addListener(listener)
                        onDispose { parentSites.removeListener(listener) }
                    }
                    AllowedSitesScreen(sites = sites, onRemove = { parentSites.remove(it) }, onClose = ::dismiss)
                }
            }
        }

    companion object {
        private const val TAG = "mirlumi_allowed_sites"

        internal fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.isStateSaved || fragmentManager.findFragmentByTag(TAG) != null) return
            AllowedSitesDialogFragment().show(fragmentManager, TAG)
        }
    }
}

@Composable
private fun AllowedSitesScreen(sites: List<String>, onRemove: (String) -> Unit, onClose: () -> Unit) {
    val space = AcornTheme.layout.space
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = space.static200, vertical = space.static300)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.mirlumi_allowed_sites_title),
                    style = AcornTheme.typography.headline6,
                    modifier = Modifier.weight(1f),
                )
                TextButton(text = stringResource(R.string.mirlumi_allowed_sites_close), onClick = onClose)
            }
            Text(
                text = stringResource(R.string.mirlumi_allowed_sites_body),
                style = AcornTheme.typography.body2,
                modifier = Modifier.padding(vertical = space.static200),
            )
            if (sites.isEmpty()) {
                Text(
                    text = stringResource(R.string.mirlumi_allowed_sites_empty),
                    style = AcornTheme.typography.body1,
                    modifier = Modifier.padding(vertical = space.static200),
                )
            }
            LazyColumn(verticalArrangement = Arrangement.Top) {
                items(sites, key = { it }) { site ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(text = site, style = AcornTheme.typography.body1, modifier = Modifier.weight(1f))
                        TextButton(
                            text = stringResource(R.string.mirlumi_allowed_sites_remove),
                            onClick = { onRemove(site) },
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
