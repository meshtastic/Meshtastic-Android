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
package org.meshtastic.core.data.radio

import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs

/**
 * SDK-backed implementation of [RadioInterfaceService].
 *
 * Delegates device-address management to [RadioPrefs] and connection lifecycle to [RadioClientAccessor].
 * The heavy transport work (BLE, TCP, Serial) is handled by the SDK internally.
 */
@Single(binds = [RadioInterfaceService::class])
class SdkRadioInterfaceService(
    private val radioPrefs: RadioPrefs,
    private val accessor: RadioClientAccessor,
) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType> =
        listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override val currentDeviceAddressFlow: StateFlow<String?> = radioPrefs.devAddr

    override fun isMockTransport(): Boolean {
        val addr = radioPrefs.devAddr.value ?: return false
        return addr.firstOrNull() == InterfaceId.MOCK.id
    }

    override fun getDeviceAddress(): String? = radioPrefs.devAddr.value

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        val current = radioPrefs.devAddr.value
        if (current == deviceAddr) return false
        radioPrefs.setDevAddr(deviceAddr)
        return true
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String =
        "${interfaceId.id}$rest"

    override fun connect() {
        accessor.rebuildAndConnectAsync()
    }

    override suspend fun disconnect() {
        accessor.disconnect()
    }
}
