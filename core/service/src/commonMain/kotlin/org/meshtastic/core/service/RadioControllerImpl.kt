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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NodeController
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.QueryController
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.ClientNotification

/**
 * Platform-agnostic [RadioController] composition root for any target where the service runs in-process (Desktop, iOS,
 * or Android in single-process mode).
 *
 * Rather than implementing every command itself, this class **assembles** four focused collaborators — one per
 * sub-interface — and delegates to them via Kotlin interface delegation, mirroring the SDK's layered API design
 * ([AdminController] → `AdminApi`, [MessagingController] → `RadioClient.send*`, [NodeController]/[QueryController] →
 * `AdminApi`/`TelemetryApi`/`RoutingApi`). When the SDK is adopted, each collaborator becomes a thin adapter and this
 * class is the seam where they are wired together.
 *
 * Only the cross-cutting concerns that don't belong to any single sub-interface live here directly: connection-state
 * surfacing, packet-id generation, location provisioning, and device-address switching.
 */
@Suppress("LongParameterList")
class RadioControllerImpl(
    private val serviceRepository: ServiceRepository,
    nodeRepository: NodeRepository,
    private val commandSender: CommandSender,
    private val nodeManager: NodeManager,
    private val radioInterfaceService: RadioInterfaceService,
    private val locationManager: MeshLocationManager,
    packetRepository: Lazy<PacketRepository>,
    dataHandler: Lazy<MeshDataHandler>,
    analytics: PlatformAnalytics,
    private val meshPrefs: MeshPrefs,
    uiPrefs: UiPrefs,
    private val databaseManager: DatabaseManager,
    private val notificationManager: NotificationManager,
    private val messageProcessor: Lazy<MeshMessageProcessor>,
    radioConfigRepository: RadioConfigRepository,
    scope: CoroutineScope,
    private val onDeviceAddressChanged: (() -> Unit)? = null,
) : RadioController,
    AdminController by AdminControllerImpl(commandSender, nodeManager, radioConfigRepository, scope),
    MessagingController by MessagingControllerImpl(
        commandSender,
        nodeManager,
        nodeRepository,
        dataHandler,
        analytics,
        packetRepository,
    ),
    NodeController by NodeControllerImpl(commandSender, nodeManager, packetRepository, scope),
    QueryController by QueryControllerImpl(commandSender, nodeManager, uiPrefs) {

    init {
        // Unify per-node databases across transports. When the handshake reports our node number, tell the
        // DatabaseManager to claim (or merge into) that node's canonical DB, so the same node reached over BLE, TCP,
        // or USB shares one stored history. Keyed on (address, node) so a second transport for the same node re-fires
        // even though the node number itself is unchanged.
        scope.launch {
            combine(meshPrefs.deviceAddress, nodeManager.myNodeNum) { address, nodeNum -> address to nodeNum }
                .distinctUntilChanged()
                .collect { (_, nodeNum) -> nodeNum?.let { databaseManager.associateNode(it) } }
        }
    }

    // ── Connection State ────────────────────────────────────────────────────

    override val connectionState: StateFlow<ConnectionState>
        get() = serviceRepository.connectionState

    override val clientNotification: StateFlow<ClientNotification?>
        get() = serviceRepository.clientNotification

    override fun clearClientNotification() {
        serviceRepository.clearClientNotification()
    }

    // ── Packet ID & Location ────────────────────────────────────────────────

    override fun generatePacketId(): Int = commandSender.generatePacketId()

    override fun startProvideLocation() {
        locationManager.restart()
    }

    override fun stopProvideLocation() {
        locationManager.stop()
    }

    // ── Device Address ──────────────────────────────────────────────────────

    override suspend fun setDeviceAddress(address: String) {
        switchDevice(address)
        radioInterfaceService.setDeviceAddress(address)
        onDeviceAddressChanged?.invoke()
    }

    override fun requestGattCacheInvalidationOnNextConnect() {
        radioInterfaceService.requestGattCacheInvalidationOnNextConnect()
    }

    private suspend fun switchDevice(deviceAddr: String) {
        val currentAddr = meshPrefs.deviceAddress.value
        if (deviceAddr != currentAddr) {
            Logger.i { "Device address changed, switching database and clearing node DB" }
            meshPrefs.setDeviceAddress(deviceAddr)
            nodeManager.clear()
            messageProcessor.value.clearEarlyPackets()
            databaseManager.switchActiveDatabase(deviceAddr)
            notificationManager.cancelAll()
            nodeManager.loadCachedNodeDB()
        }
    }
}
