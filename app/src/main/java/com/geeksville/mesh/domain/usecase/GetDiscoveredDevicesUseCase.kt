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
package com.geeksville.mesh.domain.usecase

import android.hardware.usb.UsbManager
import android.net.nsd.NsdServiceInfo
import com.geeksville.mesh.model.DeviceListEntry
import com.geeksville.mesh.model.getMeshtasticShortName
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.repository.network.NetworkRepository.Companion.toAddressString
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.usb.UsbRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.DatabaseManager
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.meshtastic
import java.util.Locale
import javax.inject.Inject

data class DiscoveredDevices(
    val bleDevices: List<DeviceListEntry>,
    val usbDevices: List<DeviceListEntry>,
    val discoveredTcpDevices: List<DeviceListEntry>,
    val recentTcpDevices: List<DeviceListEntry>,
)

@Suppress("LongParameterList")
class GetDiscoveredDevicesUseCase
@Inject
constructor(
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val nodeRepository: NodeRepository,
    private val databaseManager: DatabaseManager,
    private val usbRepository: UsbRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val usbManagerLazy: dagger.Lazy<UsbManager>,
) {
    private val suffixLength = 4

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun invoke(showMock: Boolean): Flow<DiscoveredDevices> {
        val nodeDb = nodeRepository.nodeDBbyNum

        val bondedBleFlow = bluetoothRepository.state.map { ble -> ble.bondedDevices.map { DeviceListEntry.Ble(it) } }

        val processedTcpFlow =
            combine(networkRepository.resolvedList, recentAddressesDataSource.recentAddresses) {
                    tcpServices,
                    recentList,
                ->
                val recentMap = recentList.associateBy({ it.address }) { it.name }
                tcpServices
                    .map { service ->
                        val address = "t${service.toAddressString()}"
                        val txtRecords = service.attributes
                        val shortNameBytes = txtRecords["shortname"]
                        val idBytes = txtRecords["id"]

                        val shortName =
                            shortNameBytes?.let { String(it, Charsets.UTF_8) } ?: getString(Res.string.meshtastic)
                        val deviceId = idBytes?.let { String(it, Charsets.UTF_8) }?.replace("!", "")
                        var displayName = recentMap[address] ?: shortName
                        if (deviceId != null && (displayName.split("_").none { it == deviceId })) {
                            displayName += "_$deviceId"
                        }
                        DeviceListEntry.Tcp(displayName, address)
                    }
                    .sortedBy { it.name }
            }

        val usbDevicesFlow =
            usbRepository.serialDevices.map { usb ->
                usb.map { (_, d) -> DeviceListEntry.Usb(radioInterfaceService, usbManagerLazy.get(), d) }
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
            val resolved = args[4] as List<NsdServiceInfo>

            @Suppress("UNCHECKED_CAST", "MagicNumber")
            val recentList = args[5] as List<RecentAddress>

            val bleForUi =
                bondedBle
                    .map { entry ->
                        val matchingNode =
                            if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                                db.values.find { node ->
                                    val suffix = entry.peripheral.getMeshtasticShortName()?.lowercase(Locale.ROOT)
                                    suffix != null && node.user.id.lowercase(Locale.ROOT).endsWith(suffix)
                                }
                            } else {
                                null
                            }
                        entry.copy(node = matchingNode)
                    }
                    .sortedBy { it.name }

            val usbForUi =
                (usbDevices + if (showMock) listOf(DeviceListEntry.Mock("Demo Mode")) else emptyList()).map { entry ->
                    val matchingNode =
                        if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                            db.values.find { node ->
                                val suffix = entry.name.split("_").lastOrNull()?.lowercase(Locale.ROOT)
                                suffix != null &&
                                    suffix.length >= suffixLength &&
                                    node.user.id.lowercase(Locale.ROOT).endsWith(suffix)
                            }
                        } else {
                            null
                        }
                    entry.copy(node = matchingNode)
                }

            val discoveredTcpForUi =
                processedTcp.map { entry ->
                    val matchingNode =
                        if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                            val resolvedService = resolved.find { "t${it.toAddressString()}" == entry.fullAddress }
                            val deviceId = resolvedService?.attributes?.get("id")?.let { String(it, Charsets.UTF_8) }
                            db.values.find { node ->
                                node.user.id == deviceId || (deviceId != null && node.user.id == "!$deviceId")
                            }
                        } else {
                            null
                        }
                    entry.copy(node = matchingNode)
                }

            val discoveredTcpAddresses = processedTcp.map { it.fullAddress }.toSet()
            val recentTcpForUi =
                recentList
                    .filterNot { discoveredTcpAddresses.contains(it.address) }
                    .map { DeviceListEntry.Tcp(it.name, it.address) }
                    .map { entry ->
                        val matchingNode =
                            if (databaseManager.hasDatabaseFor(entry.fullAddress)) {
                                val suffix = entry.name.split("_").lastOrNull()?.lowercase(Locale.ROOT)
                                db.values.find { node ->
                                    suffix != null &&
                                        suffix.length >= suffixLength &&
                                        node.user.id.lowercase(Locale.ROOT).endsWith(suffix)
                                }
                            } else {
                                null
                            }
                        entry.copy(node = matchingNode)
                    }
                    .sortedBy { it.name }

            DiscoveredDevices(
                bleDevices = bleForUi,
                usbDevices = usbForUi,
                discoveredTcpDevices = discoveredTcpForUi,
                recentTcpDevices = recentTcpForUi,
            )
        }
    }
}
