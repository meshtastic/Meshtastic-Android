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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.network
import org.meshtastic.core.resources.usb
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Usb
import org.meshtastic.core.ui.icon.Wifi

/**
 * Single-choice transport selector rendered below the connection card. A Material 3 [SingleChoiceSegmentedButtonRow]
 * makes the mutually-exclusive choice explicit: the segments read as one grouped control and the selected transport
 * shows a check, rather than three independent chips whose filled state was read as "enabled/available" instead of
 * "selected".
 *
 * @param showBluetooth false on hardware with no Bluetooth LE, where the BLE segment could never find anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportSelector(
    activeTransport: DeviceType,
    onSelectTransport: (DeviceType) -> Unit,
    modifier: Modifier = Modifier,
    showBluetooth: Boolean = true,
) {
    val transports = if (showBluetooth) DeviceType.entries else DeviceType.entries - DeviceType.BLE
    // Fill the width so the control reads as one deliberate group spanning the same width as the connection card
    // above; each SegmentedButton carries an internal weight(1f), so the segments divide the row evenly.
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        transports.forEachIndexed { index, transport ->
            TransportSegment(
                selected = activeTransport == transport,
                index = index,
                count = transports.size,
                label = transport.label,
                icon = transport.icon,
                onClick = { onSelectTransport(transport) },
            )
        }
    }
}

private val DeviceType.label: StringResource
    get() =
        when (this) {
            DeviceType.BLE -> Res.string.bluetooth
            DeviceType.TCP -> Res.string.network
            DeviceType.USB -> Res.string.usb
        }

private val DeviceType.icon: ImageVector
    @Composable
    get() =
        when (this) {
            DeviceType.BLE -> MeshtasticIcons.Bluetooth
            DeviceType.TCP -> MeshtasticIcons.Wifi
            DeviceType.USB -> MeshtasticIcons.Usb
        }

/**
 * A single transport segment: shows a check when [selected] and the transport [icon] otherwise, so selection is
 * unambiguous while the unselected segments still communicate which transport they represent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.TransportSegment(
    selected: Boolean,
    index: Int,
    count: Int,
    label: StringResource,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
        icon = {
            SegmentedButtonDefaults.Icon(active = selected) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                )
            }
        },
        label = { Text(text = stringResource(label), maxLines = 1) },
    )
}
