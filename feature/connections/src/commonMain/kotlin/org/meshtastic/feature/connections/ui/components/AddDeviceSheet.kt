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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.isValidAddress
import org.meshtastic.core.network.repository.NetworkConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_network_device
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.ip_port

/** Sheet for manually adding a TCP device by IP address and port. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddDeviceDialog(
    sheetState: SheetState,
    onHideDialog: () -> Unit,
    onClickAdd: (address: String, fullAddress: String) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onHideDialog, sheetState = sheetState) {
        AddDeviceForm(onCancel = onHideDialog, onClickAdd = onClickAdd)
    }
}

/**
 * The manual-address form: address and port fields stacked full width, then a low-emphasis cancel and the one filled
 * add action, end-aligned and free to wrap.
 */
@Composable
internal fun AddDeviceForm(onCancel: () -> Unit, onClickAdd: (address: String, fullAddress: String) -> Unit) {
    val addressState = rememberTextFieldState("")
    val portState = rememberTextFieldState(NetworkConstants.SERVICE_PORT.toString())
    val portOutOfRange = portState.text.toString().toIntOrNull()?.let { it !in VALID_PORTS } ?: false

    // Scrolls so a short window (landscape, large font) can still reach the actions.
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            state = addressState,
            labelPosition = TextFieldLabelPosition.Above(),
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(Res.string.address)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            state = portState,
            labelPosition = TextFieldLabelPosition.Above(),
            placeholder = { Text(NetworkConstants.SERVICE_PORT.toString()) },
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(Res.string.ip_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            isError = portOutOfRange,
            modifier = Modifier.fillMaxWidth(),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }

            Button(
                onClick = {
                    manualTcpAddress(addressState.text.toString(), portState.text.toString())?.let { address ->
                        onClickAdd(address, "t$address")
                    }
                },
            ) {
                Text(stringResource(Res.string.add_network_device))
            }
        }
    }
}

private val VALID_PORTS = 1..65535

/**
 * The address a manual entry adds, or null when it can't be connected to: an invalid host, or a numeric port outside
 * 1..65535. The default port is left off, and a non-numeric port falls back to the default.
 */
internal fun manualTcpAddress(address: String, portText: String): String? {
    val port = portText.toIntOrNull()
    return when {
        !address.isValidAddress() -> null
        port != null && port !in VALID_PORTS -> null
        port != null && port != NetworkConstants.SERVICE_PORT -> "$address:$portText"
        else -> address
    }
}
