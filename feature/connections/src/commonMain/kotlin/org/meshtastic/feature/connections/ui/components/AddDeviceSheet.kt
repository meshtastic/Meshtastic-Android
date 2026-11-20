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
import org.meshtastic.core.model.util.normalizeWebSocketUrl
import org.meshtastic.core.network.repository.NetworkConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_network_device
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.ip_port
import org.meshtastic.core.resources.websocket_port_in_url
import org.meshtastic.feature.connections.TCP_DEVICE_PREFIX
import org.meshtastic.feature.connections.WEBSOCKET_DEVICE_PREFIX

/**
 * Sheet for manually adding a network device: a host and port for TCP, or a `ws://` / `wss://` URL in the address field
 * for firmware's WebSocket phone API, which disables the port field.
 */
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
    // Where a bare host is a WebSocket, an empty port is the scheme's own.
    val webSocketScheme = bareHostWebSocketScheme()
    val defaultPort = if (webSocketScheme == null) NetworkConstants.SERVICE_PORT.toString() else ""
    val portState = rememberTextFieldState(defaultPort)
    val webSocket = isWebSocketEntry(addressState.text.toString())
    val portOutOfRange = !webSocket && portState.text.toString().toIntOrNull()?.let { it !in VALID_PORTS } ?: false
    val portHint: (@Composable () -> Unit)? =
        if (webSocket) {
            { Text(stringResource(Res.string.websocket_port_in_url)) }
        } else {
            null
        }

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
            placeholder = { Text(defaultPort) },
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(stringResource(Res.string.ip_port)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            enabled = !webSocket,
            supportingText = portHint,
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
                    manualAddress(addressState.text.toString(), portState.text.toString())?.let { fullAddress ->
                        onClickAdd(fullAddress.drop(1), fullAddress)
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
 * The full address a manual entry adds, prefix included, or null when it can't be connected to. A `ws://` or `wss://`
 * URL in the address field is a WebSocket phone link, normalized by [normalizeWebSocketUrl] with the port field
 * ignored. A bare host is firmware's WebSocket endpoint where raw TCP cannot open ([webSocketScheme] set, on web) and a
 * TCP host checked by [manualTcpAddress] everywhere else.
 */
internal fun manualAddress(
    address: String,
    portText: String,
    webSocketScheme: String? = bareHostWebSocketScheme(),
): String? = when {
    isWebSocketEntry(address) -> normalizeWebSocketUrl(address)?.let { WEBSOCKET_DEVICE_PREFIX + it }
    webSocketScheme != null -> bareHostWebSocket(address.trim(), portText, webSocketScheme)
    else -> manualTcpAddress(address, portText)?.let { TCP_DEVICE_PREFIX + it }
}

/** Whether the address field holds a `ws://` or `wss://` URL, which carries its own port. */
internal fun isWebSocketEntry(address: String): Boolean {
    val trimmed = address.trim()
    return trimmed.startsWith("ws://", ignoreCase = true) || trimmed.startsWith("wss://", ignoreCase = true)
}

/**
 * A bare host where raw TCP cannot reach: firmware's WebSocket phone API at it, on [portText] or the scheme's port.
 * Firmware's TLS ports, meshtasticd's 9443 and an ESP32's 443, take `wss` even from a plain page, which may open
 * either.
 */
private fun bareHostWebSocket(host: String, portText: String, scheme: String): String? {
    val portField = portText.trim()
    val port = portField.toIntOrNull()
    val portOk = portField.isEmpty() || (port != null && port in VALID_PORTS)
    val hostPart = if (':' in host && !host.startsWith("[")) "[$host]" else host
    val secure = if (port in FIRMWARE_TLS_PORTS) "wss" else scheme
    val url = "$secure://$hostPart${port?.let { ":$it" }.orEmpty()}"
    // An IPv6 literal is validated bare: the brackets belong to the URL, not the address.
    val hostOk = host.removeSurrounding("[", "]").isValidAddress()
    return normalizeWebSocketUrl(url)?.takeIf { portOk && hostOk }?.let { WEBSOCKET_DEVICE_PREFIX + it }
}

private val FIRMWARE_TLS_PORTS = setOf(443, 9443)

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
