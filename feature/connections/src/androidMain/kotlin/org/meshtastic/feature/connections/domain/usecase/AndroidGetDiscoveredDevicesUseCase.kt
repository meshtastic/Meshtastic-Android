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
package org.meshtastic.feature.connections.domain.usecase

import android.hardware.usb.UsbManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.Node
import org.meshtastic.core.network.repository.DiscoveredService
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.demo_mode
import org.meshtastic.core.resources.meshtastic
import org.meshtastic.feature.connections.model.AndroidUsbDeviceData
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import org.meshtastic.feature.connections.model.getMeshtasticShortName
import java.util.Locale

@Suppress("LongParameterList")
@Single
class AndroidGetDiscoveredDevicesUseCase(
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val usbRepository: UsbRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val usbManagerLazy: Lazy<UsbManager>,
) : GetDiscoveredDevicesUseCase {
    private val macSuffixLength = 8

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun invoke(showMock: Boolean): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum

        // Filter out non-Meshtastic peripherals (headphones, cars, watches, etc.).
        // BluetoothAdapter.bondedDevices returns every bonded device on the phone, so we
        // must restrict the picker to entries whose advertised name matches the
        // Meshtastic firmware pattern (see MeshtasticBleConstants.BLE_NAME_PATTERN).
        val bondedBleFlow =
            bluetoothRepository.state.map { ble ->
                ble.bondedDevices.filter { it.getMeshtasticShortName() != null }.map { DeviceListEntry.Ble(it) }
            }

        val processedTcpFlow =
            combine(networkRepository.resolvedList, recentAddressesDataSource.recentAddresses) {
                    tcpServices,
                    recentList,
                ->
                val defaultName = getString(Res.string.meshtastic)
                processTcpServices(tcpServices, recentList, defaultName)
            }

        val usbDevicesFlow =
            usbRepository.serialDevices.map { usb ->
                usb.map { (_, d) ->
                    DeviceListEntry.Usb(
                        usbData = AndroidUsbDeviceData(d),
                        name = d.device.deviceName,
                        fullAddress =
                        radioInterfaceService.toInterfaceAddress(
                            org.meshtastic.core.model.InterfaceId.SERIAL,
                            d.device.deviceName,
                        ),
                        bonded = usbManagerLazy.value.hasPermission(d.device),
                    )
                }
            }

        return combine(
            nodeDb,
            bondedBleFlow,
            processedTcpFlow,
            usbDevicesFlow,
            networkRepository.resolvedList,
            recentAddressesDataSource.recentAddresses,
        ) { args: Array<Any> ->
            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val db = args[0] as Map<Int, Node>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val bondedBle = args[1] as List<DeviceListEntry.Ble>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val processedTcp = args[2] as List<DeviceListEntry.Tcp>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val usbDevices = args[3] as List<DeviceListEntry.Usb>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val resolved = args[4] as List<DiscoveredService>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val recentList = args[5] as List<RecentAddress>

            // Android-specific: BLE node matching by MAC suffix and Meshtastic short name
            val bleForUi =
                bondedBle
                    .map { entry ->
                        val matchingNode =
                            if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                                db.values.find { node ->
                                    val macSuffix =
                                        entry.device.address
                                            .replace(":", "")
                                            .takeLast(macSuffixLength)
                                            .lowercase(Locale.ROOT)
                                    val nameSuffix = entry.device.getMeshtasticShortName()?.lowercase(Locale.ROOT)
                                    node.user.id.lowercase(Locale.ROOT).endsWith(macSuffix) ||
                                        (nameSuffix != null && node.user.id.lowercase(Locale.ROOT).endsWith(nameSuffix))
                                }
                            } else {
                                null
                            }
                        entry.copy(node = matchingNode)
                    }
                    .sortedBy { it.name }

            // Android-specific: USB node matching via shared helper
            val usbForUi =
                (
                    usbDevices +
                        if (showMock) listOf(DeviceListEntry.Mock(getString(Res.string.demo_mode))) else emptyList()
                    )
                    .map { entry ->
                        entry.copy(node = findNodeByNameSuffix(entry.name, entry.fullAddress, db, databaseManager))
                    }

            // Shared TCP logic via helpers
            val discoveredTcpForUi = matchDiscoveredTcpNodes(processedTcp, db, resolved, databaseManager)
            val discoveredTcpAddresses = processedTcp.map { it.fullAddress }.toSet()
            val recentTcpForUi = buildRecentTcpEntries(recentList, discoveredTcpAddresses, db, databaseManager)

            DiscoveredDevices(
                bleDevices = bleForUi,
                usbDevices = usbForUi,
                discoveredTcpDevices = discoveredTcpForUi,
                recentTcpDevices = recentTcpForUi,
            )
        }
    }
}
