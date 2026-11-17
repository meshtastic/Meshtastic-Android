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
package org.meshtastic.core.network.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.host.BearerFactory
import org.meshtastic.node.host.HostCapabilities
import org.meshtastic.node.host.Requirement
import org.meshtastic.node.testing.FakeTransport
import org.meshtastic.node.transport.Bearer
import org.meshtastic.node.transport.BearerNames
import org.meshtastic.node.transport.ExperimentalTransportApi
import org.meshtastic.node.transport.MeshTransport
import org.meshtastic.proto.Config

/**
 * Hands the node the same fake UDP and GATT bearers on every build, with every requirement met, and a fake transport
 * for each name in [extraRadios].
 */
@OptIn(ExperimentalTransportApi::class)
internal class FakeBearerFactory(extraRadios: List<String> = emptyList()) : BearerFactory {
    private val transports: List<MeshTransport> =
        listOf(FakeTransport(BearerNames.UDP), FakeTransport(BearerNames.GATT)) + extraRadios.map(::FakeTransport)

    override val hostCapabilities: HostCapabilities = HostCapabilities(hasWifi = true, hasBluetooth = true)

    override val requirements: StateFlow<Map<Bearer, Requirement>> = MutableStateFlow(emptyMap())

    override fun build(lora: Config.LoRaConfig?, logger: MeshLogger): List<MeshTransport> = transports
}
