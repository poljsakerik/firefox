/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.mirlumi.R
import mozilla.components.compose.base.theme.AcornTheme

/** A full-screen PIN pad: unlocking, choosing the first PIN, or changing it. */
class PinDialogFragment : DialogFragment() {
    private val purpose: PinPurpose
        get() = PinPurpose.valueOf(requireArguments().getString(ARG_PURPOSE)!!)

    private val required: Boolean
        get() = requireArguments().getBoolean(ARG_REQUIRED)

    private val model: PinViewModel by viewModels {
        viewModelFactory {
            initializer { PinViewModel(PinFlow(SettingsLock.pin(requireContext()), purpose)) }
        }
    }

    private var succeeded = false
    private var closedQuietly = false

    // Set once the dialog has closed. The fragment stays findable until its removal runs, after onDismiss, and a
    // replacement opened in between must not mistake it for one still on screen.
    private var closing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.MirlumiPinDialog)
        isCancelable = !required
        // The action waiting on this belonged to the activity before it was recreated, so it can't run any more.
        if (savedInstanceState != null && purpose == PinPurpose.AUTHORIZE) {
            dismissAllowingStateLoss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state = model.state
                AcornTheme {
                    PinScreen(
                        purpose = purpose,
                        state = state,
                        onDigit = model::type,
                        onDelete = model::delete,
                        onCancel = if (required) null else ::dismiss,
                        onLockoutTick = model::refreshLockout,
                    )
                }
                LaunchedEffect(state.done) {
                    if (state.done) onSucceeded()
                }
            }
        }

    private fun onSucceeded() {
        if (succeeded) return
        succeeded = true
        if (purpose == PinPurpose.CHANGE) {
            Toast.makeText(requireContext(), R.string.mirlumi_pin_changed, Toast.LENGTH_SHORT).show()
        }
        dismissAllowingStateLoss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        closing = true
        super.onDismiss(dialog)
        val activity = activity ?: return
        if (closedQuietly || activity.isChangingConfigurations || activity.isFinishing) return
        SettingsLock.onPinScreenClosed(purpose, activity, succeeded)
    }

    companion object {
        private const val TAG = "mirlumi_pin"
        private const val ARG_PURPOSE = "purpose"
        private const val ARG_REQUIRED = "required"

        private fun tag(purpose: PinPurpose) =
            when (purpose) {
                PinPurpose.UNLOCK,
                PinPurpose.SETUP -> TAG
                PinPurpose.AUTHORIZE -> "${TAG}_authorize"
                PinPurpose.CHANGE -> "${TAG}_change"
            }

        /** Shows the PIN screen for [purpose], unless one for the same need is already open. */
        internal fun show(fragmentManager: FragmentManager, purpose: PinPurpose, required: Boolean = false) {
            val open = fragmentManager.findFragmentByTag(tag(purpose)) as? PinDialogFragment
            if (fragmentManager.isStateSaved || (open != null && !open.closing)) return
            PinDialogFragment()
                .apply {
                    arguments =
                        Bundle().apply {
                            putString(ARG_PURPOSE, purpose.name)
                            putBoolean(ARG_REQUIRED, required)
                        }
                }
                .show(fragmentManager, tag(purpose))
        }

        /** Closes an unlock screen that's no longer needed, because the locked screen behind it has gone. */
        internal fun dismissOptional(fragmentManager: FragmentManager) {
            val dialog = fragmentManager.findFragmentByTag(TAG) as? PinDialogFragment ?: return
            if (!dialog.required && !dialog.closing && !fragmentManager.isStateSaved) {
                dialog.closedQuietly = true
                dialog.dismiss()
            }
        }
    }
}

internal class PinViewModel(private val flow: PinFlow) : ViewModel() {
    var state by mutableStateOf(flow.state)
        private set

    fun type(digit: Char) = update { flow.type(digit) }

    fun delete() = update { flow.delete() }

    fun refreshLockout() = update { flow.refreshLockout() }

    private fun update(change: () -> Unit) {
        change()
        state = flow.state
    }
}
