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
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/** Android implementation of [RadioTransportFactory] delegating to the legacy [InterfaceFactory]. */
@Single(binds = [RadioTransportFactory::class])
@Suppress("LongParameterList")
class AndroidRadioTransportFactory(
    private val context: Context,
    private val interfaceFactory: Lazy<InterfaceFactory>,
    private val buildConfigProvider: BuildConfigProvider,
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override fun isMockInterface(): Boolean =
        buildConfigProvider.isDebug || Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    override fun isPlatformAddressValid(address: String): Boolean = interfaceFactory.value.addressValid(address)

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport {
        // Fallback to legacy factory for Serial, Mocks, and NOPs
        return interfaceFactory.value.createInterface(address, service)
    }
}
