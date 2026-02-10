/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.messaging

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import org.meshtastic.core.strings.relays
import org.meshtastic.core.strings.resend
import org.meshtastic.core.ui.component.MeshtasticDialog

@Suppress("UnusedParameter")
@Composable
fun DeliveryInfo(
    title: StringResource,
    resendOption: Boolean,
    text: StringResource? = null,
    relayNodeName: String? = null,
    relays: Int = 0,
    onConfirm: (() -> Unit) = {},
    onDismiss: () -> Unit = {},
) = MeshtasticDialog(
    title = stringResource(title),
    onDismiss = onDismiss,
    dismissText = stringResource(Res.string.close),
    confirmText = if (resendOption) stringResource(Res.string.resend) else null,
    onConfirm = if (resendOption) onConfirm else null,
    text = {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            text?.let {
                Text(
                    text = stringResource(it),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (relays != 0) {
                Text(
                    text = pluralStringResource(Res.plurals.relays, relays, relays),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    },
)
