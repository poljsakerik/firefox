/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.mirlumi.settingslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mirlumi.R
import kotlinx.coroutines.delay
import mozilla.components.compose.base.button.TextButton
import mozilla.components.compose.base.theme.AcornTheme
import mozilla.components.ui.icons.R as iconsR

@Composable
internal fun PinScreen(
    purpose: PinPurpose,
    state: PinFlow.State,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancel: (() -> Unit)?,
    onLockoutTick: () -> Unit,
) {
    val lockout = state.message as? PinFlow.Message.LockedOut
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockout) {
        now = System.currentTimeMillis()
        while (lockout != null && now < lockout.untilMillis) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
        if (lockout != null) onLockoutTick()
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = AcornTheme.layout.space.static300,
                        vertical = AcornTheme.layout.space.static400,
                    ),
        ) {
            Icon(
                painter = painterResource(iconsR.drawable.mozac_ic_lock_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(title(purpose, state.step)),
                style = AcornTheme.typography.headline6,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            body(purpose, state.step)?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(it),
                    style = AcornTheme.typography.body2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
            }
            Spacer(Modifier.height(32.dp))
            Dots(entered = state.digits)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.heightIn(min = 56.dp).padding(vertical = AcornTheme.layout.space.static100),
            ) {
                state.message?.let {
                    Text(
                        text = message(it, now),
                        style = AcornTheme.typography.body2,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            Keypad(enabled = lockout == null, onDigit = onDigit, onDelete = onDelete, onCancel = onCancel)
        }
    }
}

@Composable
private fun Dots(entered: Int) {
    val description = pluralStringResource(R.plurals.mirlumi_pin_progress, entered, entered, SettingsPin.LENGTH)
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        repeat(SettingsPin.LENGTH) { index ->
            val color = MaterialTheme.colorScheme.primary
            Box(
                Modifier.size(16.dp)
                    .clip(CircleShape)
                    .border(2.dp, color, CircleShape)
                    .background(if (index < entered) color else MaterialTheme.colorScheme.surface)
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(KEY_SIZE)) {
                if (onCancel != null) {
                    TextButton(text = stringResource(R.string.mirlumi_pin_cancel), onClick = onCancel)
                }
            }
            DigitKey('0', enabled, onDigit)
            Surface(
                onClick = onDelete,
                enabled = enabled,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(KEY_SIZE),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(iconsR.drawable.mozac_ic_back_24),
                        contentDescription = stringResource(R.string.mirlumi_pin_delete),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Char, enabled: Boolean, onDigit: (Char) -> Unit) {
    Surface(
        onClick = { onDigit(digit) },
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(KEY_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(digit.toString(), style = AcornTheme.typography.headline5)
        }
    }
}

private fun title(purpose: PinPurpose, step: PinFlow.Step): Int =
    when (step) {
        PinFlow.Step.CURRENT ->
            if (purpose == PinPurpose.CHANGE) R.string.mirlumi_pin_current_title else R.string.mirlumi_pin_unlock_title
        PinFlow.Step.CHOOSE ->
            if (purpose == PinPurpose.CHANGE) R.string.mirlumi_pin_new_title else R.string.mirlumi_pin_setup_title
        PinFlow.Step.CONFIRM -> R.string.mirlumi_pin_confirm_title
    }

private fun body(purpose: PinPurpose, step: PinFlow.Step): Int? =
    when (step) {
        PinFlow.Step.CURRENT ->
            when (purpose) {
                PinPurpose.AUTHORIZE -> R.string.mirlumi_pin_authorize_body
                PinPurpose.CHANGE -> null
                else -> R.string.mirlumi_pin_unlock_body
            }
        PinFlow.Step.CHOOSE -> R.string.mirlumi_pin_setup_body
        PinFlow.Step.CONFIRM -> R.string.mirlumi_pin_confirm_body
    }

@Composable
private fun message(message: PinFlow.Message, now: Long): String =
    when (message) {
        PinFlow.Message.Wrong -> stringResource(R.string.mirlumi_pin_wrong)
        PinFlow.Message.Mismatch -> stringResource(R.string.mirlumi_pin_mismatch)
        is PinFlow.Message.LockedOut -> {
            val seconds = ((message.untilMillis - now + 999) / 1_000).coerceAtLeast(1).toInt()
            val wait =
                if (seconds < 60) {
                    pluralStringResource(R.plurals.mirlumi_pin_seconds, seconds, seconds)
                } else {
                    val minutes = (seconds + 59) / 60
                    pluralStringResource(R.plurals.mirlumi_pin_minutes, minutes, minutes)
                }
            stringResource(R.string.mirlumi_pin_locked_out, wait)
        }
    }

private val KEY_SIZE = 72.dp
