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
package org.meshtastic.core.ui.component

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.internal
import org.meshtastic.core.strings.via_api
import org.meshtastic.core.strings.via_mqtt
import org.meshtastic.core.strings.via_udp
import org.meshtastic.core.ui.icon.Api
import org.meshtastic.core.ui.icon.Cloud
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Udp
import org.meshtastic.proto.MeshPacket

@Composable
fun TransportIcon(transport: Int, viaMqtt: Boolean, modifier: Modifier = Modifier) {
    val (icon, description) =
        when {
            viaMqtt || transport == MeshPacket.TransportMechanism.TRANSPORT_MQTT.value ->
                MeshtasticIcons.Cloud to stringResource(Res.string.via_mqtt)
            transport == MeshPacket.TransportMechanism.TRANSPORT_MULTICAST_UDP.value ->
                MeshtasticIcons.Udp to stringResource(Res.string.via_udp)
            transport == MeshPacket.TransportMechanism.TRANSPORT_API.value ->
                MeshtasticIcons.Api to stringResource(Res.string.via_api)
            transport == MeshPacket.TransportMechanism.TRANSPORT_INTERNAL.value ->
                MeshtasticIcons.Device to stringResource(Res.string.internal)
            else -> return
        }
    Icon(icon, contentDescription = description, modifier = modifier)
}
