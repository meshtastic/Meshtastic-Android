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
import android.hardware.usb.UsbManager
import android.provider.Settings
import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * Android implementation of [RadioTransportFactory]. Handles pure-KMP transports (BLE) via [BaseRadioTransportFactory]
 * while creating platform-specific connections (TCP, USB/Serial, Mock, NOP) directly in [createPlatformTransport].
 */
@Single(binds = [RadioTransportFactory::class])
@Suppress("LongParameterList")
class AndroidRadioTransportFactory(
    private val context: Context,
    private val buildConfigProvider: BuildConfigProvider,
    private val usbRepository: UsbRepository,
    private val usbManager: UsbManager,
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override fun isMockTransport(): Boolean =
        buildConfigProvider.isDebug || Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"

    override fun isPlatformAddressValid(address: String): Boolean {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) } ?: return false
        val rest = address.substring(1)
        return when (interfaceId) {
            InterfaceId.MOCK,
            InterfaceId.NOP,
            InterfaceId.REPLAY,
            InterfaceId.TCP,
            -> true

            InterfaceId.SERIAL -> {
                val deviceMap = usbRepository.serialDevices.value
                val driver = deviceMap[rest] ?: deviceMap.values.firstOrNull()
                driver != null && usbManager.hasPermission(driver.device)
            }

            InterfaceId.BLUETOOTH -> true // Handled by base class
        }
    }

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        val rest = address.substring(1)

        return when (interfaceId) {
            InterfaceId.MOCK -> MockRadioTransport(callback = service, scope = service.serviceScope, address = rest)

            InterfaceId.REPLAY -> createReplayTransport(service, rest)

            InterfaceId.TCP ->
                TcpRadioTransport(
                    callback = service,
                    scope = service.serviceScope,
                    dispatchers = dispatchers,
                    address = rest,
                )

            InterfaceId.SERIAL ->
                SerialRadioTransport(
                    callback = service,
                    scope = service.serviceScope,
                    usbRepository = usbRepository,
                    address = rest,
                )

            InterfaceId.NOP,
            null,
            -> NopRadioTransport(rest)

            InterfaceId.BLUETOOTH -> error("BLE addresses should be handled by BaseRadioTransportFactory")
        }
    }

    /**
     * Replay selection ("r"). Replays the bundled burningmesh capture asset on-device via [ReplayRadioTransport] —
     * realistic ~200-node traffic for perf / benchmark / populated-UI work. The asset only ships in debug (and
     * benchmark) builds; when it is absent we fall back to the lightweight synthetic [MockRadioTransport] so selecting
     * the entry still yields a working virtual device.
     */
    private fun createReplayTransport(service: RadioInterfaceService, rest: String): RadioTransport {
        val replayFrames =
            runCatching { context.assets.open(REPLAY_ASSET_NAME).use { it.readBytes() } }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
        return if (replayFrames != null) {
            Logger.i { "Replay device → replaying $REPLAY_ASSET_NAME (${replayFrames.size} bytes)" }
            ReplayRadioTransport(
                callback = service,
                scope = service.serviceScope,
                address = rest,
                frames = replayFrames,
            )
        } else {
            Logger.w { "Replay device selected but $REPLAY_ASSET_NAME asset is missing — falling back to mock" }
            MockRadioTransport(callback = service, scope = service.serviceScope, address = rest)
        }
    }

    private companion object {
        const val REPLAY_ASSET_NAME = "burningmesh.fromradio"
    }
}
