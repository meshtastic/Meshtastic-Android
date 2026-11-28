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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.label_print_action
import org.meshtastic.core.resources.label_print_band_connect
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Print
import org.meshtastic.core.ui.label.LabelSpec
import org.meshtastic.core.ui.label.NameTagContent
import org.meshtastic.core.ui.label.isLabelPrintSupported
import org.meshtastic.feature.connections.connectLink
import org.meshtastic.feature.connections.fixedPairingPin
import org.meshtastic.proto.Config

/**
 * The connect label for [node], the radio connected at [address], or null when that connection is not one to share (a
 * serial port, a demo transport), when [address], the selection, is not [connectedAddress], the radio this session's
 * handshake came from, or when [nodeIsThisSession] is false: until the radio has reported itself in this session,
 * [node] and [bluetooth] can be the previous radio's. Its QR carries the PIN only when [bluetooth], the radio's own
 * config, reports fixed PIN pairing with a PIN a connect link accepts.
 */
internal fun connectLabelSpec(
    node: Node,
    address: String,
    connectedAddress: String?,
    bluetooth: Config.BluetoothConfig?,
    band: String,
    nodeIsThisSession: Boolean,
): LabelSpec.Connect? = connectLink(address)
    .takeIf { nodeIsThisSession && address == connectedAddress }
    ?.let { plain ->
        val link = connectLink(address, fixedPairingPin(bluetooth)) ?: plain
        LabelSpec.Connect(
            content =
            NameTagContent(
                shortName = node.user.short_name,
                longName = node.user.long_name,
                nodeId = node.user.id.ifEmpty { NodeAddress.numToDefaultId(node.num) },
            ),
            link = link,
            linkWithoutPin = plain.takeIf { it != link },
            band = band,
        )
    }

/** [connectLabelSpec] where Print label is offered at all, with the band's words. */
@Composable
internal fun rememberConnectLabel(
    node: Node,
    address: String,
    connectedAddress: String?,
    bluetooth: Config.BluetoothConfig?,
    nodeIsThisSession: Boolean,
): LabelSpec.Connect? {
    val supported = isLabelPrintSupported()
    val band = stringResource(Res.string.label_print_band_connect)
    return if (supported) {
        connectLabelSpec(node, address, connectedAddress, bluetooth, band, nodeIsThisSession)
    } else {
        null
    }
}

/** Print label for the connected radio. The sheet it opens belongs to the screen, not to the connection card. */
@Composable
internal fun ConnectLabelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Icon(imageVector = MeshtasticIcons.Print, contentDescription = null)
        Text(text = stringResource(Res.string.label_print_action), modifier = Modifier.padding(start = 8.dp))
    }
}
