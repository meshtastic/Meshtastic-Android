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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.licensed_amateur_radio
import org.meshtastic.core.resources.licensed_amateur_radio_text
import org.meshtastic.core.resources.licensed_mode_enable_confirm
import org.meshtastic.core.resources.licensed_mode_enable_title
import org.meshtastic.core.resources.licensed_mode_enable_warning
import org.meshtastic.core.resources.licensed_mode_enable_warning_signed
import org.meshtastic.core.ui.component.MeshtasticResourceDialog
import org.meshtastic.core.ui.component.SwitchPreference

internal const val LICENSED_MODE_SWITCH_TEST_TAG = "licensed_mode_switch"

/**
 * The licensed (ham) mode toggle. Enabling is staged behind a confirmation dialog: on firmware that signs licensed
 * traffic (design#122, `has_xeddsa`), the copy explains authenticated-but-plaintext operation and the one-time node
 * number migration; otherwise it carries the legacy plaintext/compatibility warning. Disabling applies immediately.
 */
@Composable
internal fun LicensedModeSetting(
    checked: Boolean,
    enabled: Boolean,
    signingSupported: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
) {
    var showEnableConfirmation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(enabled) {
        if (!enabled) {
            showEnableConfirmation = false
        }
    }

    if (showEnableConfirmation) {
        MeshtasticResourceDialog(
            titleRes = Res.string.licensed_mode_enable_title,
            messageRes =
            if (signingSupported == true) {
                Res.string.licensed_mode_enable_warning_signed
            } else {
                Res.string.licensed_mode_enable_warning
            },
            confirmTextRes = Res.string.licensed_mode_enable_confirm,
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
        title = stringResource(Res.string.licensed_amateur_radio),
        summary = stringResource(Res.string.licensed_amateur_radio_text),
        checked = checked,
        enabled = enabled,
        modifier = Modifier.testTag(LICENSED_MODE_SWITCH_TEST_TAG),
        onCheckedChange = { licensed ->
            if (licensed && !checked) {
                showEnableConfirmation = true
            } else {
                onCheckedChange(licensed)
            }
        },
        containerColor = CardDefaults.cardColors().containerColor,
    )
}
