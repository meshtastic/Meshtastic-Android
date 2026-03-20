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

import android.content.Context
import android.provider.Settings
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * Android implementation of [RadioTransportFactory] delegating to the legacy [InterfaceFactory].
 */
@Single
class AndroidRadioTransportFactory(
    private val context: Context,
    private val interfaceFactory: Lazy<InterfaceFactory>,
    private val buildConfigProvider: BuildConfigProvider,
) : RadioTransportFactory {

    override val supportedDeviceTypes: List<DeviceType> =
        listOf(
            DeviceType.BLE,
            DeviceType.TCP,
            DeviceType.USB,
        )

    override fun isMockInterface(): Boolean =
        buildConfigProvider.isDebug || Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    override fun createTransport(address: String, service: RadioInterfaceService): RadioTransport {
        return interfaceFactory.value.createInterface(address, service)
    }

    override fun isAddressValid(address: String?): Boolean {
        return interfaceFactory.value.addressValid(address)
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String {
        return interfaceFactory.value.toInterfaceAddress(interfaceId, rest)
    }
}
