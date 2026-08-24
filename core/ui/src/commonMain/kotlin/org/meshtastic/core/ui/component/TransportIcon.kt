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

import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.internal
import org.meshtastic.core.resources.transport_api
import org.meshtastic.core.resources.transport_lora
import org.meshtastic.core.resources.transport_mqtt
import org.meshtastic.core.resources.transport_udp
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.Api
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttConnected
import org.meshtastic.core.ui.icon.Udp
import org.meshtastic.proto.MeshPacket.TransportMechanism

/** Icon + short label for a [TransportMechanism] value, or `null` if unknown. Single source for transport badging. */
@Composable
fun transportInfo(transport: Int, viaMqtt: Boolean): Pair<ImageVector, String>? = when {
    viaMqtt || transport == TransportMechanism.TRANSPORT_MQTT.value ->
        MeshtasticIcons.MqttConnected to stringResource(Res.string.transport_mqtt)

    transport == TransportMechanism.TRANSPORT_LORA.value ||
        transport == TransportMechanism.TRANSPORT_LORA_ALT1.value ||
        transport == TransportMechanism.TRANSPORT_LORA_ALT2.value ||
        transport == TransportMechanism.TRANSPORT_LORA_ALT3.value ->
        MeshtasticIcons.Antenna to stringResource(Res.string.transport_lora)

    transport == TransportMechanism.TRANSPORT_MULTICAST_UDP.value ||
        transport == TransportMechanism.TRANSPORT_UNICAST_UDP.value ->
        MeshtasticIcons.Udp to stringResource(Res.string.transport_udp)

    transport == TransportMechanism.TRANSPORT_API.value ->
        MeshtasticIcons.Api to stringResource(Res.string.transport_api)

    transport == TransportMechanism.TRANSPORT_INTERNAL.value ->
        MeshtasticIcons.Device to stringResource(Res.string.internal)

    else -> null
}

@Composable
fun TransportIcon(
    transport: Int,
    viaMqtt: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    // Lists only badge "notable" transports; LoRa (and unicast UDP) are the unremarkable default — skip them.
    val isLora =
        transport == TransportMechanism.TRANSPORT_LORA.value ||
            transport == TransportMechanism.TRANSPORT_LORA_ALT1.value ||
            transport == TransportMechanism.TRANSPORT_LORA_ALT2.value ||
            transport == TransportMechanism.TRANSPORT_LORA_ALT3.value
    val isUnicastUdp = transport == TransportMechanism.TRANSPORT_UNICAST_UDP.value
    if (!viaMqtt && (isLora || isUnicastUdp)) return
    val (icon, description) = transportInfo(transport, viaMqtt) ?: return
    Icon(icon, contentDescription = description, modifier = modifier, tint = tint)
}
