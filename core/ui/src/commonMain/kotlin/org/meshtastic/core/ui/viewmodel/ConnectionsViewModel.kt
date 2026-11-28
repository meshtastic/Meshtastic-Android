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
package org.meshtastic.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.DeviceAddress
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareRelease
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.FirmwareUpdateNoticePolicy
import org.meshtastic.core.model.FirmwareUpdateTransport
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NodeRestartTracker
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig
import org.meshtastic.sdk.ConnectionState

@KoinViewModel
class ConnectionsViewModel(
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
    nodeRepository: NodeRepository,
    nodeManager: NodeManager,
    nodeRestartTracker: NodeRestartTracker,
    private val uiPrefs: UiPrefs,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val radioPrefs: RadioPrefs,
    private val serviceNotifications: MeshNotificationManager,
) : ViewModel() {

    private val scheduledFirmwareUpdateNotificationKeys = mutableSetOf<String>()

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig.Builder().build())

    val connectionState = serviceRepository.connectionState
    val lockdownState = serviceRepository.lockdownState

    val myNodeInfo: StateFlow<MyNodeInfo?> = nodeRepository.myNodeInfo

    val ourNodeInfo: StateFlow<Node?> = nodeRepository.ourNodeInfo

    /**
     * Filtered [ourNodeInfo] that only emits when display-relevant fields change, preventing continuous recomposition
     * from lastHeard/snr updates.
     */
    val ourNodeForDisplay: StateFlow<Node?> =
        nodeRepository.ourNodeInfo
            .distinctUntilChanged { old, new ->
                old?.num == new?.num &&
                    old?.user == new?.user &&
                    old?.batteryLevel == new?.batteryLevel &&
                    old?.voltage == new?.voltage &&
                    old?.metadata?.firmware_version == new?.metadata?.firmware_version
            }
            .stateInWhileSubscribed(initialValue = nodeRepository.ourNodeInfo.value)

    /** Whether the LoRa region is UNSET and needs to be configured. */
    val regionUnset: StateFlow<Boolean> =
        radioConfigRepository.localConfigFlow
            .map { it.lora?.region == Config.LoRaConfig.RegionCode.UNSET }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = false)

    // The preference write lands later; this session's offers are consumed at once so a return to Connections before
    // it lands cannot open the setup a second time.
    private val radioSetupOfferedNow = MutableStateFlow<Set<Int>>(emptySet())

    /** The connected radio's transport address, the key the radio setup binds to. */
    val connectionAddress: StateFlow<String?> =
        nodeManager.connectionIdentity.map { it?.address }.distinctUntilChanged().stateInWhileSubscribed(null)

    /**
     * The connected radio's node number while its region is UNSET and the radio setup has not opened for it yet. The
     * screen opens the setup once per emission and marks the radio offered, so a radio the user closed is never
     * reopened on its own. Bound to the active session the same way as [activeNodeInfoReady].
     */
    val radioSetupOffer: StateFlow<Int?> =
        combine(
            nodeManager.connectionIdentity,
            nodeRepository.ourNodeInfo,
            regionUnset,
            uiPrefs.radioSetupOfferedNodes,
            radioSetupOfferedNow,
        ) { identity, ourNode, unset, offered, offeredNow ->
            radioSetupOfferFor(identity?.nodeNum, ourNode?.num, unset, offered + offeredNow)
        }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = null)

    fun markRadioSetupOffered(nodeNum: Int) {
        radioSetupOfferedNow.update { it + nodeNum }
        uiPrefs.markRadioSetupOffered(nodeNum)
    }

    /**
     * Whether the device is configured not to transmit. Null-safe against the pre-config [LocalConfig] so the notice
     * cannot flash before the device's config arrives.
     */
    val txDisabled: StateFlow<Boolean> =
        radioConfigRepository.localConfigFlow
            .map { it.lora?.tx_enabled == false }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = false)

    /**
     * Whether [ourNodeInfo] belongs to the active transport session rather than cached state from an earlier session.
     * Warning presentation uses this boundary so a non-null repository node alone cannot make configuration actionable.
     */
    val activeNodeInfoReady: StateFlow<Boolean> =
        combine(nodeManager.connectionIdentity, nodeRepository.ourNodeInfo) { connectionIdentity, ourNode ->
            connectionIdentity != null && ourNode?.num == connectionIdentity.nodeNum
        }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = false)

    /**
     * Whether the node is applying a change that reboots it, so a drop and the reconnect that follows present as a
     * restart rather than a failure.
     */
    val restartExpected: StateFlow<Boolean> = nodeRestartTracker.restartExpected

    private val _hasShownNotPairedWarning = MutableStateFlow(uiPrefs.hasShownNotPairedWarning.value)
    val hasShownNotPairedWarning: StateFlow<Boolean> = _hasShownNotPairedWarning.asStateFlow()

    fun suppressNoPairedWarning() {
        _hasShownNotPairedWarning.value = true
        uiPrefs.setHasShownNotPairedWarning(true)
    }

    private val localHardware =
        combine(nodeRepository.myNodeInfo, nodeRepository.ourNodeInfo) { myNode, ourNode ->
            val hardwareModel = ourNode?.user?.hw_model?.value ?: return@combine null
            val target = myNode?.pioEnv?.takeIf { it.isNotBlank() }
            hardwareModel to target
        }
            .flatMapLatest { query ->
                query?.let { (hardwareModel, target) ->
                    deviceHardwareRepository.observeDeviceHardware(hardwareModel, target)
                } ?: flowOf(null)
            }

    private val firmwareUpdateInputs =
        combine(
            connectionState,
            nodeRepository.myId,
            nodeRepository.myNodeInfo,
            firmwareReleaseRepository.stableRelease,
            radioPrefs.devAddr,
        ) { state, nodeIdentity, myNode, stableRelease, address ->
            FirmwareUpdateInputs(
                connectionState = state,
                nodeIdentity = nodeIdentity,
                currentVersion = myNode?.firmwareVersion,
                // A stale (or failed-to-refresh) release catalog must not prompt users. The repository updates this
                // timestamp only when it writes a current catalog; its bundled seed and a successful refresh are
                // both valid sources, while an old cache fails closed.
                stableRelease =
                stableRelease?.takeIf { it.lastUpdated >= nowMillis - TimeConstants.ONE_HOUR.inWholeMilliseconds },
                address = address,
            )
        }

    private val firmwareUpdateCandidate =
        combine(firmwareUpdateInputs, localHardware) { inputs, hardware ->
            val state = inputs.connectionState
            if (state !is ConnectionState.Connected) return@combine null
            val transport =
                DeviceAddress.parse(inputs.address)?.interfaceId?.toFirmwareUpdateTransport() ?: return@combine null
            val stableRelease = inputs.stableRelease ?: return@combine null
            val deviceHardware = hardware ?: return@combine null
            FirmwareUpdateCandidate(
                nodeIdentity = inputs.nodeIdentity,
                currentVersion = inputs.currentVersion,
                stableRelease = stableRelease,
                hardware = deviceHardware,
                transport = transport,
            )
        }

    val firmwareUpdateNotice: StateFlow<FirmwareUpdateNotice?> =
        firmwareUpdateCandidate
            .flatMapLatest { candidate ->
                candidate?.let {
                    flow {
                        val releaseTargets =
                            firmwareReleaseRepository.getManifestTargets(it.stableRelease) ?: emptySet()
                        emit(
                            FirmwareUpdateNoticePolicy.createNotice(
                                nodeIdentity = it.nodeIdentity,
                                currentVersion = it.currentVersion,
                                stableVersion = it.stableRelease.id,
                                hardware = it.hardware,
                                transport = it.transport,
                                releaseTargets = releaseTargets,
                            ),
                        )
                    }
                        .catch { emit(null) }
                } ?: flowOf(null)
            }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = null)

    init {
        firmwareUpdateNotice
            .map { notice ->
                notice?.takeIf {
                    FirmwareUpdateNoticePolicy.shouldSchedule(
                        it.notificationKey,
                        uiPrefs.firmwareUpdateNotificationKeys.value,
                    ) && it.notificationKey !in scheduledFirmwareUpdateNotificationKeys
                }
            }
            .filterNotNull()
            .onEach { notice ->
                if (serviceNotifications.showFirmwareUpdateNotification(notice)) {
                    scheduledFirmwareUpdateNotificationKeys += notice.notificationKey
                    uiPrefs.recordFirmwareUpdateNotificationKey(notice.notificationKey)
                }
            }
            .launchIn(viewModelScope)
    }
}

private data class FirmwareUpdateInputs(
    val connectionState: ConnectionState,
    val nodeIdentity: String?,
    val currentVersion: String?,
    val stableRelease: FirmwareRelease?,
    val address: String?,
)

private data class FirmwareUpdateCandidate(
    val nodeIdentity: String?,
    val currentVersion: String?,
    val stableRelease: FirmwareRelease,
    val hardware: DeviceHardware,
    val transport: FirmwareUpdateTransport,
)

private fun InterfaceId.toFirmwareUpdateTransport(): FirmwareUpdateTransport? = when (this) {
    InterfaceId.BLUETOOTH -> FirmwareUpdateTransport.Bluetooth

    InterfaceId.SERIAL -> FirmwareUpdateTransport.Serial

    InterfaceId.TCP -> FirmwareUpdateTransport.Tcp

    // Firmware updates go over the radio's own TCP, BLE or serial path, not the WebSocket.
    InterfaceId.WEBSOCKET,
    InterfaceId.MOCK,
    InterfaceId.NOP,
    InterfaceId.REPLAY,
    -> null
}

/**
 * The node to open the radio setup for, or null. The session's node and the repository's node must agree, so a cached
 * node from an earlier session never triggers the setup, and an unarrived config reads as a null `lora`, not UNSET.
 */
internal fun radioSetupOfferFor(sessionNodeNum: Int?, ourNodeNum: Int?, regionUnset: Boolean, offered: Set<Int>): Int? =
    sessionNodeNum?.takeIf {
        it == ourNodeNum && regionUnset && it !in offered
    }
