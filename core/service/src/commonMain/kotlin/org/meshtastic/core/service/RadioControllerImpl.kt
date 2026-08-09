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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.repository.AdminController
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.ConnectionIdentity
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
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.ClientNotification

private data class AssociationSnapshot(
    val identity: ConnectionIdentity?,
    val selectedAddress: String?,
    val sessionGeneration: Long,
    val activeSession: RadioSessionContext?,
)

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

    private val deviceSwitchMutex = Mutex()

    init {
        // Reconcile the connection-session identity at every transport-session boundary (stop/start cycle), not only
        // when the selected address changes. Without this, a same-address same-device reconnect can retain the old
        // ConnectionIdentity. Capture the initial generation before subscribing instead of blindly dropping the first
        // emission: if a transport starts between capture and collector subscription, the collector's first replay is
        // the real boundary. The generation-aware atomic clear removes the old identity without erasing a fresh one
        // that the new session may already have published while this collector was awaiting dispatch.
        val initialSessionGeneration = radioInterfaceService.sessionGeneration.value
        radioInterfaceService.sessionGeneration
            .onEach { generation ->
                if (generation == initialSessionGeneration) return@onEach
                Logger.d { "[DeviceAssociation] reconciling identity at session-generation boundary gen=$generation" }
                nodeManager.clearStaleConnectionIdentity(generation)
            }
            .launchIn(scope)

        // Unify per-device databases across transports. When a fresh connection-handshake identity
        // arrives (nodeNum + deviceId from the same MyNodeInfo), tell the DatabaseManager to claim
        // (or merge into) that device's canonical DB, so the same device reached over BLE, TCP, or
        // USB shares one stored history. The hardware device id (when reported) is the durable claim
        // key — firmware 2.8 renumbers devices (num = crc32(public_key)) on upgrade/erase/re-key —
        // with the node number as fallback. Keyed on (address, identity) so a second transport for
        // the same device re-fires even though the identity itself is unchanged.
        //
        // Uses the dedicated connectionIdentity source (not the general myNodeNum/myDeviceId flows)
        // so that a stale cached identity from a previous transport cannot associate the new address
        // with the old node number. Each identity carries the address selected for its handshake, so correctness does
        // not depend on delivery order between independent StateFlows. The selected address is checked again at the
        // point of association to discard a session that became stale while queued for collection.
        //
        // Collect the nullable identity together with both active-session coordinates. The admitted lifecycle lease
        // closes new work on teardown but delays session rollover until association and any destination transaction
        // commit return. collectLatest still cancels work whose identity or selected address changes independently.
        // CancellationException preserves structured cancellation; recoverable Exceptions are logged, and fatal
        // Errors propagate.
        @Suppress("TooGenericExceptionCaught")
        scope.launch {
            combine(
                nodeManager.connectionIdentity,
                meshPrefs.deviceAddress,
                radioInterfaceService.sessionGeneration,
                radioInterfaceService.activeSession,
            ) { identity, selectedAddress, sessionGeneration, activeSession ->
                AssociationSnapshot(identity, selectedAddress, sessionGeneration, activeSession)
            }
                .distinctUntilChanged()
                .collectLatest { snapshot ->
                    val identity = snapshot.identity ?: return@collectLatest
                    val authorizingSession =
                        RadioSessionContext(generation = identity.sessionGeneration, address = identity.address)
                    if (
                        snapshot.selectedAddress != identity.address ||
                        snapshot.sessionGeneration != identity.sessionGeneration ||
                        snapshot.activeSession != authorizingSession
                    ) {
                        Logger.d { "[DeviceAssociation] skip stale-session node=${identity.nodeNum}" }
                        return@collectLatest
                    }
                    Logger.d {
                        "[DeviceAssociation] associate address=... node=${identity.nodeNum}" +
                            " deviceIdPresent=${identity.deviceId != null}"
                    }
                    try {
                        val admitted =
                            radioInterfaceService.runWithSessionLease(authorizingSession) { lease ->
                                databaseManager.associateDevice(
                                    identity.address,
                                    identity.nodeNum,
                                    identity.deviceId,
                                    lease::isCurrent,
                                )
                            }
                        if (!admitted) {
                            Logger.d { "[DeviceAssociation] skip revoked-session node=${identity.nodeNum}" }
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (failure: Exception) {
                        Logger.w(failure) { "Device association failed for current radio session" }
                    }
                }
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

    @Suppress("TooGenericExceptionCaught")
    override suspend fun setDeviceAddress(address: String) {
        deviceSwitchMutex.withLock {
            // The transport service updates its selected-address flow synchronously, while MeshPrefs persists on an
            // asynchronous DataStore scope. Prefer the transport snapshot so back-to-back selections cannot roll back
            // to a stale preference value; fall back to MeshPrefs during startup before the transport has a selection.
            val transportAddress = radioInterfaceService.getDeviceAddress()
            val previousAddress = transportAddress ?: meshPrefs.deviceAddress.value
            if (address == previousAddress) {
                // This is intentionally more than a preference no-op: when the selected transport is disconnected,
                // setDeviceAddress() cycles it as a repair attempt; when it is already connected, the transport
                // reports a no-op and the postcondition below confirms that the requested address remains selected.
                val accepted = radioInterfaceService.setDeviceAddress(address)
                // SharedRadioInterfaceService reports an already-connected no-op as false; confirm that postcondition
                // without allowing a genuine rejection to persist preferences or publish a success callback.
                check(accepted || radioInterfaceService.getDeviceAddress() == address) {
                    "Transport rejected the existing device address"
                }
                meshPrefs.setDeviceAddress(address)
            } else {
                // Revoke the old transport session before publishing a different database. Otherwise the old transport
                // can deliver one last frame after switchDevice() has moved currentDb, writing stale-session data into
                // the new device's database.
                radioInterfaceService.disconnect()
                try {
                    switchDevice(address)
                    check(radioInterfaceService.setDeviceAddress(address)) {
                        "Transport rejected the new device address"
                    }
                } catch (failure: Exception) {
                    withContext(NonCancellable) { rollbackDeviceSwitch(previousAddress, failure) }
                    throw failure
                }
            }
        }
        // Keep callbacks outside the transition mutex so observers cannot deadlock by scheduling another selection.
        onDeviceAddressChanged?.invoke()
    }

    override fun requestGattCacheInvalidationOnNextConnect() {
        radioInterfaceService.requestGattCacheInvalidationOnNextConnect()
    }

    private suspend fun switchDevice(deviceAddr: String) {
        Logger.i { "Device address changed, switching database and clearing node DB" }
        // Revoke identity and persistence before publishing the new database. A node write already admitted against
        // the old pool drains through DatabaseManager; a queued write rechecks allowNodeDbWrites and cannot start on
        // the new pool with the old node snapshot.
        nodeManager.clearConnectionIdentity()
        nodeManager.setAllowNodeDbWrites(false)
        Logger.d { "[DeviceAssociation] session-cleared before address switch" }
        messageProcessor.value.clearEarlyPackets()
        databaseManager.switchActiveDatabase(deviceAddr)
        nodeManager.clear()
        notificationManager.cancelAll()
        nodeManager.loadCachedNodeDB()
        // Commit the persisted selection last. MeshPrefs writes asynchronously, so the transport's synchronous
        // selected-address snapshot remains the rollback authority for a rapid subsequent selection.
        meshPrefs.setDeviceAddress(deviceAddr)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun rollbackDeviceSwitch(previousAddress: String?, originalFailure: Exception) {
        suspend fun attemptRollback(description: String, block: suspend () -> Unit): Boolean = try {
            block()
            true
        } catch (rollbackFailure: Exception) {
            originalFailure.addSuppressed(rollbackFailure)
            Logger.w(rollbackFailure) { "Failed to roll back $description after device-switch failure" }
            false
        }

        // Database ownership is the safety boundary. Never restore a transport until the database that belongs to it
        // is active again; otherwise old-radio frames can resume against the new device's pool.
        val databaseRestored =
            attemptRollback("database selection") { databaseManager.switchActiveDatabase(previousAddress) }
        if (!databaseRestored) {
            // Keep each cleanup independent: failure in one best-effort reset must not prevent transport deselection.
            attemptRollback("fail-closed node-write disable") { nodeManager.setAllowNodeDbWrites(false) }
            attemptRollback("fail-closed connection-identity clear") { nodeManager.clearConnectionIdentity() }
            attemptRollback("fail-closed node-state clear") { nodeManager.clear() }
            attemptRollback("fail-closed early-packet clear") { messageProcessor.value.clearEarlyPackets() }
            attemptRollback("fail-closed notification clear") { notificationManager.cancelAll() }
            attemptRollback("fail-closed persisted selection") { meshPrefs.setDeviceAddress(null) }
            attemptRollback("fail-closed transport selection") {
                check(radioInterfaceService.setDeviceAddress(null)) { "Transport rejected fail-closed deselection" }
            }
            return
        }

        attemptRollback("persisted device selection") { meshPrefs.setDeviceAddress(previousAddress) }
        attemptRollback("node state") {
            nodeManager.clearConnectionIdentity()
            nodeManager.clear()
            messageProcessor.value.clearEarlyPackets()
            notificationManager.cancelAll()
            nodeManager.loadCachedNodeDB()
        }
        attemptRollback("transport selection") {
            check(radioInterfaceService.setDeviceAddress(previousAddress)) {
                "Transport rejected the previous device address"
            }
        }
    }
}
