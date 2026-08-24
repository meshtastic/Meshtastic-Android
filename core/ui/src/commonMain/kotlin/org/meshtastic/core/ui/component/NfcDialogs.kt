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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.nfc_disabled
import org.meshtastic.core.resources.open_settings
import org.meshtastic.core.ui.util.rememberOpenNfcSettings

/**
 * Prompt shown when an NFC action (scan or write) is requested while NFC is turned off. Offers to open system settings.
 * [titleRes] lets the caller frame it for the specific action (e.g. scan vs write).
 */
@Composable
fun NfcDisabledDialog(titleRes: StringResource, onDismiss: () -> Unit) {
    val openNfcSettings = rememberOpenNfcSettings()
    MeshtasticDialog(
        onDismiss = onDismiss,
        titleRes = titleRes,
        messageRes = Res.string.nfc_disabled,
        onConfirm = {
            openNfcSettings()
            onDismiss()
        },
        confirmTextRes = Res.string.open_settings,
        dismissTextRes = Res.string.cancel,
    )
}
