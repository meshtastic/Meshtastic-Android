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
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.FirmwareUpdateNoticePolicy
import org.meshtastic.core.model.FirmwareUpdateTransport
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.TimeConstants
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.FirmwareReleaseRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.Notification
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.firmware_update_available
import org.meshtastic.core.resources.firmware_update_notification_android
import org.meshtastic.core.resources.firmware_update_notification_flasher
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.proto.Config
import org.meshtastic.proto.LocalConfig

/**
 * Derived, UI-friendly summary of the device connection state. Combines [ServiceRepository.connectionState] with
 * "region unset" and the [ServiceRepository.RECONNECTING_PROGRESS_TEXT] handshake-recovery signal to surface cases
 * (MUST_SET_REGION, RECONNECTING) that otherwise need separate boolean flags in the UI layer.
 */
enum class ConnectionStatus {
    /** No device has been selected or we are otherwise disconnected. */
    NOT_CONNECTED,

    /** A device has been selected and we are working through bonding/handshake. */
    CONNECTING,

    /**
     * Transport is recovering from a WiFi/TCP handshake stall (the watchdog tore the link down and is bringing it back
     * up). Distinct from [NOT_CONNECTED] so the UI can show an in-progress recovery instead of a final failure.
     */
    RECONNECTING,

    /** Connected with node info available. */
    CONNECTED,

    /** Connected but the device is in deep sleep. */
    CONNECTED_SLEEPING,

    /** Connected and active, but LoRa region is UNSET — user action required. */
    MUST_SET_REGION,
}

@KoinViewModel
class ConnectionsViewModel(
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
    nodeRepository: NodeRepository,
    private val uiPrefs: UiPrefs,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val radioPrefs: RadioPrefs,
    private val notificationManager: NotificationManager,
) : ViewModel() {

    private val scheduledFirmwareUpdateNotificationKeys = mutableSetOf<String>()

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val connectionState = serviceRepository.connectionState
    val lockdownState = serviceRepository.lockdownState
    val sessionAuthorized = serviceRepository.sessionAuthorized

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

    /**
     * Single source of truth for the UI's "connection status" pill/banner. Derived from [connectionState],
     * [ServiceRepository.connectionProgress], and [regionUnset]; kept here rather than in the composable so the mapping
     * is observable and testable.
     *
     * The [ConnectionStatus.RECONNECTING] case is signalled by the WiFi/TCP handshake watchdog writing
     * [ServiceRepository.RECONNECTING_PROGRESS_TEXT] to [ServiceRepository.connectionProgress] immediately before its
     * recovery sibling transitions to [ConnectionState.Disconnected]. See
     * [ServiceRepository.RECONNECTING_PROGRESS_TEXT] for the cross-track contract.
     */
    val connectionStatus: StateFlow<ConnectionStatus> =
        combine(connectionState, regionUnset, serviceRepository.connectionProgress) { state, unset, progress ->
            when (state) {
                is ConnectionState.Connected ->
                    if (unset) ConnectionStatus.MUST_SET_REGION else ConnectionStatus.CONNECTED

                ConnectionState.Connecting -> ConnectionStatus.CONNECTING

                ConnectionState.Disconnected ->
                    if (progress == ServiceRepository.RECONNECTING_PROGRESS_TEXT) {
                        ConnectionStatus.RECONNECTING
                    } else {
                        ConnectionStatus.NOT_CONNECTED
                    }

                ConnectionState.DeviceSleep -> ConnectionStatus.CONNECTED_SLEEPING
            }
        }
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = ConnectionStatus.NOT_CONNECTED)

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
            val transport = inputs.address?.firstOrNull()?.toFirmwareUpdateTransport() ?: return@combine null
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
                val message =
                    when (notice.destination) {
                        org.meshtastic.core.model.FirmwareUpdateDestination.AndroidUpdate ->
                            getStringSuspend(
                                Res.string.firmware_update_notification_android,
                                notice.currentVersion,
                                notice.stableVersion,
                            )

                        org.meshtastic.core.model.FirmwareUpdateDestination.MeshtasticFlasher ->
                            getStringSuspend(
                                Res.string.firmware_update_notification_flasher,
                                notice.currentVersion,
                                notice.stableVersion,
                            )
                    }
                if (
                    notificationManager.dispatch(
                        Notification(
                            id = notice.notificationKey.hashCode(),
                            title = getStringSuspend(Res.string.firmware_update_available),
                            message = message,
                            type = Notification.Type.Info,
                            category = Notification.Category.NodeEvent,
                            deepLinkUri =
                            if (
                                notice.destination ==
                                org.meshtastic.core.model.FirmwareUpdateDestination.AndroidUpdate
                            ) {
                                "meshtastic:///firmware/update"
                            } else {
                                "https://flasher.meshtastic.org"
                            },
                        ),
                    )
                ) {
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

private fun Char.toFirmwareUpdateTransport(): FirmwareUpdateTransport? = when (this) {
    'x' -> FirmwareUpdateTransport.Bluetooth
    's' -> FirmwareUpdateTransport.Serial
    't' -> FirmwareUpdateTransport.Tcp
    else -> null
}
