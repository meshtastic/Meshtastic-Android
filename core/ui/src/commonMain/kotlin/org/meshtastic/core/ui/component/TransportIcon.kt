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
import org.meshtastic.core.resources.node_bearer_audio
import org.meshtastic.core.resources.node_bearer_ble_adv
import org.meshtastic.core.resources.node_bearer_gatt
import org.meshtastic.core.resources.node_bearer_video
import org.meshtastic.core.resources.node_bearer_wifi_aware
import org.meshtastic.core.resources.transport_api
import org.meshtastic.core.resources.transport_lora
import org.meshtastic.core.resources.transport_mqtt
import org.meshtastic.core.resources.transport_udp
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.Api
import org.meshtastic.core.ui.icon.Bluetooth
import org.meshtastic.core.ui.icon.BluetoothSearching
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MqttConnected
import org.meshtastic.core.ui.icon.QrCodeScanner
import org.meshtastic.core.ui.icon.Speaker
import org.meshtastic.core.ui.icon.Udp
import org.meshtastic.core.ui.icon.Wifi
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

    transport == TransportMechanism.TRANSPORT_BLE_ADV.value ->
        MeshtasticIcons.BluetoothSearching to stringResource(Res.string.node_bearer_ble_adv)

    transport == TransportMechanism.TRANSPORT_BLE_GATT.value ->
        MeshtasticIcons.Bluetooth to stringResource(Res.string.node_bearer_gatt)

    transport == TransportMechanism.TRANSPORT_AUDIO.value ->
        MeshtasticIcons.Speaker to stringResource(Res.string.node_bearer_audio)

    transport == TransportMechanism.TRANSPORT_VIDEO.value ->
        MeshtasticIcons.QrCodeScanner to stringResource(Res.string.node_bearer_video)

    transport == TransportMechanism.TRANSPORT_WIFI_AWARE.value ->
        MeshtasticIcons.Wifi to stringResource(Res.string.node_bearer_wifi_aware)

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
