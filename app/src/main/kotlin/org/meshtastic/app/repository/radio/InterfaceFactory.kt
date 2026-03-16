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
package org.meshtastic.app.repository.radio

import org.koin.core.annotation.Single
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport

/**
 * Entry point for create radio backend instances given a specific address.
 *
 * This class is responsible for building and dissecting radio addresses based upon their interface type and the "rest"
 * of the address (which varies per implementation).
 */
@Single
class InterfaceFactory(
    private val nopInterfaceFactory: NopInterfaceFactory,
    private val bluetoothSpec: Lazy<BleRadioInterfaceSpec>,
    private val mockSpec: Lazy<MockInterfaceSpec>,
    private val serialSpec: Lazy<SerialInterfaceSpec>,
    private val tcpSpec: Lazy<TCPInterfaceSpec>,
) {
    internal val nopInterface by lazy { nopInterfaceFactory.create("") }

    private val specMap: Map<InterfaceId, InterfaceSpec<*>>
        get() =
            mapOf(
                InterfaceId.BLUETOOTH to bluetoothSpec.value,
                InterfaceId.MOCK to mockSpec.value,
                InterfaceId.NOP to NopInterfaceSpec(nopInterfaceFactory),
                InterfaceId.SERIAL to serialSpec.value,
                InterfaceId.TCP to tcpSpec.value,
            )

    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    fun createInterface(address: String, service: RadioInterfaceService): RadioTransport {
        val (spec, rest) = splitAddress(address)
        return spec?.createInterface(rest, service) ?: nopInterface
    }

    fun addressValid(address: String?): Boolean = address?.let {
        val (spec, rest) = splitAddress(it)
        spec?.addressValid(rest)
    } ?: false

    private fun splitAddress(address: String): Pair<InterfaceSpec<*>?, String> {
        val c = address[0].let { InterfaceId.forIdChar(it) }?.let { specMap[it] }
        val rest = address.substring(1)
        return Pair(c, rest)
    }
}
