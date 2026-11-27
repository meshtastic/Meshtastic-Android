/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.repository.TransparentModeTurnOff
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.lora_field_transparent_mode
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.retry
import org.meshtastic.core.resources.transparent_mode_banner_summary
import org.meshtastic.core.resources.transparent_mode_banner_title
import org.meshtastic.core.resources.transparent_mode_enable_confirm
import org.meshtastic.core.resources.transparent_mode_enable_title
import org.meshtastic.core.resources.transparent_mode_enable_warning
import org.meshtastic.core.resources.transparent_mode_leave_on
import org.meshtastic.core.resources.transparent_mode_notice_message
import org.meshtastic.core.resources.transparent_mode_notice_title
import org.meshtastic.core.resources.transparent_mode_still_on
import org.meshtastic.core.resources.transparent_mode_summary
import org.meshtastic.core.resources.transparent_mode_turn_off
import org.meshtastic.core.resources.transparent_mode_turned_off
import org.meshtastic.core.resources.transparent_mode_turning_off
import org.meshtastic.core.resources.transparent_mode_unconfirmed
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.TransparentModeViewModel

const val TRANSPARENT_MODE_SWITCH_TEST_TAG = "transparent_mode_switch"

/**
 * Shows the transparent mode notice over any screen while the connected node calls for it. [suppressed] holds it back
 * while a flow that must not be interrupted, the firmware update, is open.
 */
@Composable
fun TransparentModeNoticeHost(suppressed: Boolean, viewModel: TransparentModeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.noticeVisible && !suppressed) {
        TransparentModeNotice(turnOff = state.turnOff, onTurnOff = viewModel::turnOff, onClose = viewModel::closeNotice)
    }
}

/**
 * The transparent mode notice. With no [turnOff] it explains the mode and offers the choice; otherwise it reports where
 * the turn-off stands. [onClose] leaves the node as it is.
 */
@Composable
fun TransparentModeNotice(
    turnOff: TransparentModeTurnOff?,
    onTurnOff: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (turnOff) {
        null -> TransparentModeChoiceNotice(onTurnOff = onTurnOff, onLeaveOn = onClose, modifier = modifier)

        TransparentModeTurnOff.IN_PROGRESS ->
            MeshtasticDialog(
                modifier = modifier,
                titleRes = Res.string.transparent_mode_notice_title,
                icon = MeshtasticIcons.Warning,
                dismissable = false,
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(text = stringResource(Res.string.transparent_mode_turning_off))
                    }
                },
            )

        TransparentModeTurnOff.TURNED_OFF ->
            MeshtasticDialog(
                modifier = modifier,
                titleRes = Res.string.lora_field_transparent_mode,
                messageRes = Res.string.transparent_mode_turned_off,
                confirmTextRes = Res.string.okay,
                onConfirm = onClose,
                dismissable = false,
            )

        TransparentModeTurnOff.STILL_ON,
        TransparentModeTurnOff.UNCONFIRMED,
        ->
            MeshtasticDialog(
                modifier = modifier,
                titleRes = Res.string.transparent_mode_notice_title,
                messageRes =
                if (turnOff == TransparentModeTurnOff.STILL_ON) {
                    Res.string.transparent_mode_still_on
                } else {
                    Res.string.transparent_mode_unconfirmed
                },
                icon = MeshtasticIcons.Warning,
                confirmTextRes = Res.string.retry,
                onConfirm = onTurnOff,
                dismissTextRes = Res.string.transparent_mode_leave_on,
                onDismiss = onClose,
            )
    }
}

/** The notice as a connection first shows it: what transparent mode stops, and the choice to turn it off. */
@Composable
fun TransparentModeChoiceNotice(onTurnOff: () -> Unit, onLeaveOn: () -> Unit, modifier: Modifier = Modifier) {
    MeshtasticDialog(
        modifier = modifier,
        titleRes = Res.string.transparent_mode_notice_title,
        messageRes = Res.string.transparent_mode_notice_message,
        icon = MeshtasticIcons.Warning,
        confirmTextRes = Res.string.transparent_mode_turn_off,
        onConfirm = onTurnOff,
        dismissTextRes = Res.string.transparent_mode_leave_on,
        onDismiss = onLeaveOn,
    )
}

/** The indicator that stays up while the connected node is in transparent mode; tapping it reopens the notice. */
@Composable
fun TransparentModeBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        ListItem(
            leadingIcon = MeshtasticIcons.Warning,
            leadingIconTint = MaterialTheme.colorScheme.error,
            text = stringResource(Res.string.transparent_mode_banner_title),
            supportingText = stringResource(Res.string.transparent_mode_banner_summary),
            onClick = onClick,
        )
    }
}

/** The LoRa transparent mode toggle. Turning it on is staged behind a confirmation; turning it off applies at once. */
@Composable
fun TransparentModeSetting(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on enabled, so a confirmation left open when the control was disabled does not come back with it.
    var showEnableConfirmation by rememberSaveable(enabled) { mutableStateOf(false) }

    if (showEnableConfirmation) {
        TransparentModeEnableConfirmation(
            onConfirm = {
                showEnableConfirmation = false
                if (enabled) {
                    onCheckedChange(true)
                }
            },
            onDismiss = { showEnableConfirmation = false },
        )
    }

    SwitchPreference(
        title = stringResource(Res.string.lora_field_transparent_mode),
        summary = stringResource(Res.string.transparent_mode_summary),
        checked = checked,
        enabled = enabled,
        modifier = modifier.testTag(TRANSPARENT_MODE_SWITCH_TEST_TAG),
        onCheckedChange = { on ->
            if (on && !checked) {
                showEnableConfirmation = true
            } else {
                onCheckedChange(on)
            }
        },
        containerColor = CardDefaults.cardColors().containerColor,
    )
}

@Composable
fun TransparentModeEnableConfirmation(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    MeshtasticResourceDialog(
        titleRes = Res.string.transparent_mode_enable_title,
        messageRes = Res.string.transparent_mode_enable_warning,
        confirmTextRes = Res.string.transparent_mode_enable_confirm,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@PreviewLightDark
@Composable
private fun TransparentModeNoticePreview() {
    AppTheme { Box(Modifier.fillMaxSize()) { TransparentModeNotice(turnOff = null, onTurnOff = {}, onClose = {}) } }
}

@PreviewLightDark
@Composable
private fun TransparentModeNoticeStillOnPreview() {
    AppTheme {
        Box(Modifier.fillMaxSize()) {
            TransparentModeNotice(turnOff = TransparentModeTurnOff.STILL_ON, onTurnOff = {}, onClose = {})
        }
    }
}

@PreviewLightDark
@Composable
private fun TransparentModeBannerPreview() {
    AppTheme { Surface { TransparentModeBanner(onClick = {}, modifier = Modifier.padding(16.dp)) } }
}

@PreviewLightDark
@Composable
private fun TransparentModeSettingPreview() {
    AppTheme { Surface { TransparentModeSetting(checked = true, enabled = true, onCheckedChange = {}) } }
}
