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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
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
) : ViewModel() {

    val localConfig: StateFlow<LocalConfig> =
        radioConfigRepository.localConfigFlow.stateInWhileSubscribed(initialValue = LocalConfig())

    val connectionState = serviceRepository.connectionState

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
}
