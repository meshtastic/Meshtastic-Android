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

import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.TransportConfig.Transport

/** One switch on the Network screen: a [Transport] the node can run and whether `transport.enabled` lists it. */
internal data class TransportSwitch(val transport: Transport, val checked: Boolean)

/**
 * One switch per transport the node lists in `DeviceMetadata.supported_transports`, in enum-number order. Empty when
 * the node lists none, which is how firmware that still switches on `network.enabled_protocols` reads.
 */
internal fun transportSwitches(supported: List<Transport>, enabled: List<Transport>): List<TransportSwitch> =
    supported.distinct().sortedBy { it.value }.map { TransportSwitch(it, it in enabled) }

/**
 * [current] with [transport] listed or not and every other entry kept, including ones the node offers no switch for.
 */
internal fun toggleTransport(current: List<Transport>, transport: Transport, on: Boolean): List<Transport> = when {
    !on -> current.filterNot { it == transport }
    transport in current -> current
    else -> current + transport
}

/**
 * The sections a Network save writes: only the ones that changed. Transport goes first, because the node reboots on the
 * network write.
 */
internal fun networkSaveConfigs(
    network: Config.NetworkConfig,
    savedNetwork: Config.NetworkConfig,
    transport: Config.TransportConfig,
    savedTransport: Config.TransportConfig,
): List<Config> = buildList {
    if (transport != savedTransport) add(Config.Builder().also { it.transport = transport }.build())
    if (network != savedNetwork) add(Config.Builder().also { it.network = network }.build())
}
