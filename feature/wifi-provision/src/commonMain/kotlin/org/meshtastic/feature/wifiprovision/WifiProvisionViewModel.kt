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
package org.meshtastic.feature.wifiprovision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.normalizeBleAddress
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.NO_RESPONSE_CODE
import org.meshtastic.feature.wifiprovision.domain.ConnectionSession
import org.meshtastic.feature.wifiprovision.domain.DeviceDiscovery
import org.meshtastic.feature.wifiprovision.domain.DeviceNotFoundException
import org.meshtastic.feature.wifiprovision.domain.DeviceResponseTimeoutException
import org.meshtastic.feature.wifiprovision.domain.NoDevicePickedException
import org.meshtastic.feature.wifiprovision.domain.NymeaWifiService
import org.meshtastic.feature.wifiprovision.model.DiscoveredDevice
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

data class WifiProvisionUiState(
    val phase: Phase = Phase.Idle,
    val networks: List<WifiNetwork> = emptyList(),
    val error: WifiProvisionError? = null,
    /** Name of the BLE device we connected to, shown in the DeviceFound confirmation. */
    val deviceName: String? = null,
    /** Address of the BLE device we connected to, shown beside [deviceName] before any password is sent. */
    val deviceAddress: String? = null,
    /** Provisioning-capable devices seen by the current scan, in the order they are listed. */
    val devices: List<DiscoveredDevice> = emptyList(),
    /** Address a link or caller asked for, held in [Phase.ConfirmDevice] until the user connects or cancels. */
    val pendingAddress: String? = null,
    /** Name the pending address advertises, once a scan has seen it. */
    val pendingName: String? = null,
    /**
     * True where a device's address is a hardware address the user can compare with a label. False where it is an
     * identifier only this host understands, which the screen does not ask the user to check.
     */
    val addressIsHardware: Boolean = true,
    /** A link named a device by hardware address this platform cannot look up, so the user chooses the device. */
    val linkAddressIgnored: Boolean = false,
    /** IPv4 address reported by nymea after successful provisioning (if available). */
    val ipAddress: String? = null,
    /** Provisioning outcome shown as inline status (matches web flasher pattern). */
    val provisionStatus: ProvisionStatus = ProvisionStatus.Idle,
    /** The browser's device chooser closed without a pick, so the screen offers it again. */
    val pickDismissed: Boolean = false,
) {
    enum class Phase {
        /** No operation running — initial state before BLE connect. */
        Idle,

        /** Scanning BLE and listing the provisioning devices found so far; nothing is connected. */
        Discovering,

        /** The scan window ended; the list (possibly empty) waits for the user to pick or rescan. */
        DeviceList,

        /** An address was supplied from outside the screen; the user confirms it before any connection. */
        ConfirmDevice,

        /** Connecting to the chosen device. */
        ConnectingBle,

        /** BLE device found and connected; waiting for user to proceed. */
        DeviceFound,

        /** Fetching visible WiFi networks from the device. */
        LoadingNetworks,

        /** Connected and networks loaded — the main configuration screen. */
        Connected,

        /** Sending WiFi credentials to the device. */
        Provisioning,
    }

    enum class ProvisionStatus {
        Idle,
        Success,
        Failed,
    }
}

/**
 * Typed error categories for the WiFi provisioning flow.
 *
 * Formatted into user-visible strings in the UI layer using string resources, keeping the ViewModel free of
 * locale-specific text.
 */
sealed interface WifiProvisionError {
    /** The device to connect to did not advertise in time. */
    data object DeviceNotFound : WifiProvisionError

    /** The connected device did not answer a command in time. */
    data object DeviceNotResponding : WifiProvisionError

    /** BLE connection to the provisioning device failed; [detail] is the underlying exception's message. */
    data class ConnectFailed(val detail: String) : WifiProvisionError

    /** WiFi network scan on the device failed; [detail] is the underlying exception's message. */
    data class ScanFailed(val detail: String) : WifiProvisionError

    /** Sending WiFi credentials to the device failed; [detail] is the device's or exception's message. */
    data class ProvisionFailed(val detail: String) : WifiProvisionError
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for the WiFi provisioning flow.
 *
 * Uses [KoinViewModel] so the instance is scoped to the navigation entry's [ViewModelStoreOwner]. A fresh
 * [NymeaWifiService] (and its own [BleConnectionFactory]-backed [org.meshtastic.core.ble.BleConnection]) is created
 * lazily for each provisioning session and cleaned up via [onCleared]. Device discovery and ordering live in
 * [DeviceDiscovery]; nothing connects until the user taps a listed device or confirms an address.
 */
@KoinViewModel
class WifiProvisionViewModel(
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
    private val dispatchers: CoroutineDispatchers,
    applicationScope: ApplicationCoroutineScope,
) : ViewModel() {

    /**
     * True where the scan is the browser's device chooser, which opens only from a click: the screen waits for one
     * instead of connecting on arrival.
     */
    val picksDevice: Boolean
        get() = bleScanner.picksDevice

    private val initialState = WifiProvisionUiState(addressIsHardware = bleScanner.addressIsHardware)
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<WifiProvisionUiState> = _uiState.asStateFlow()

    private val discovery = DeviceDiscovery(bleScanner)

    /**
     * Every coroutine the screen starts runs in [session], which [disconnect], [onCleared] and each new attempt cancel
     * as a whole, so no scan, connect or request outlives the connection it belongs to.
     */
    private val session = ConnectionSession(viewModelScope, applicationScope)

    /** The state after a device went quiet: the connection is dropped and the screen offers a retry. */
    private val notRespondingState =
        initialState.copy(
            phase = WifiProvisionUiState.Phase.Idle,
            error = WifiProvisionError.DeviceNotResponding,
        )

    // region Public actions (called from UI)

    /**
     * Start the screen for [address], which a link or caller may supply. Nothing connects here:
     * - an address this platform can look up waits in [WifiProvisionUiState.Phase.ConfirmDevice] for the user's
     *   Connect;
     * - an address it cannot look up (see [BleScanner.addressIsHardware]) is set aside, and the user chooses the
     *   device;
     * - where the app scans itself, the user taps a device in the list; where the browser supplies a chooser, the
     *   screen waits for its button.
     *
     * A no-op unless the screen is idle, so a re-run of the screen's effect cannot discard a pending choice.
     */
    fun open(address: String?) {
        if (_uiState.value.phase != WifiProvisionUiState.Phase.Idle) return
        val ignored = address != null && !bleScanner.addressIsHardware
        when {
            address != null && !ignored -> {
                _uiState.update {
                    it.copy(
                        phase = WifiProvisionUiState.Phase.ConfirmDevice,
                        pendingAddress = address,
                        pendingName = null,
                    )
                }
                session.restart().launch {
                    session.awaitRelease()
                    safeCatching { discovery.advertisedName(address) }
                        .onSuccess { name ->
                            _uiState.update {
                                if (it.phase == WifiProvisionUiState.Phase.ConfirmDevice && name != null) {
                                    it.copy(pendingName = name)
                                } else {
                                    it
                                }
                            }
                        }
                        .onFailure { Logger.w(it) { "$TAG: advertised name lookup failed" } }
                }
            }

            picksDevice -> _uiState.update { it.copy(linkAddressIgnored = ignored) }

            else -> beginDiscovery(linkIgnored = ignored)
        }
    }

    /** Clear the list and scan again, from the list or after a failed scan or connect. */
    fun rescan() {
        val state = _uiState.value
        if (state.phase != WifiProvisionUiState.Phase.DeviceList && state.phase != WifiProvisionUiState.Phase.Idle) {
            return
        }
        beginDiscovery(linkIgnored = state.linkAddressIgnored)
    }

    /** The user accepted the address held in [WifiProvisionUiState.pendingAddress]. */
    fun confirmConnect() {
        val state = _uiState.value
        val address = state.pendingAddress
        if (state.phase != WifiProvisionUiState.Phase.ConfirmDevice || address == null) return
        connectToDevice(address)
    }

    /** Connect to the listed device with [address], which the user tapped. */
    fun connectToDiscovered(address: String) {
        // Only from the list: a late tap on a stale row must not restart a connection that is already under way.
        val phase = _uiState.value.phase
        if (phase != WifiProvisionUiState.Phase.Discovering && phase != WifiProvisionUiState.Phase.DeviceList) return
        val device = discovery.deviceFor(address) ?: return
        launchConnect { it.connect(device) }
    }

    /**
     * Connect to the device with [address], or to the one the user picks in the browser's own chooser when [address] is
     * null there. Where the app scans itself, a null [address] starts the device list instead: the connection never
     * goes to whichever device advertises first. Pauses at [WifiProvisionUiState.Phase.DeviceFound] so the user can
     * check the device before proceeding.
     *
     * @param address Address of the device to connect to.
     */
    fun connectToDevice(address: String? = null) {
        if (address == null && !picksDevice) {
            beginDiscovery(linkIgnored = _uiState.value.linkAddressIgnored)
            return
        }
        launchConnect { it.connect(address) }
    }

    private fun beginDiscovery(linkIgnored: Boolean) {
        discovery.clear()
        _uiState.value =
            initialState.copy(phase = WifiProvisionUiState.Phase.Discovering, linkAddressIgnored = linkIgnored)
        session.restart().launch {
            session.awaitRelease()
            safeCatching { discovery.scan { devices -> _uiState.update { it.copy(devices = devices) } } }
                .onSuccess { _uiState.update { state -> state.copy(phase = WifiProvisionUiState.Phase.DeviceList) } }
                .onFailure { e ->
                    Logger.e(e) { "$TAG: device scan failed" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Idle,
                            error = WifiProvisionError.ConnectFailed(e.message ?: "Unknown error"),
                        )
                    }
                }
        }
    }

    private fun launchConnect(connect: suspend (NymeaWifiService) -> Result<String>) {
        _uiState.update {
            it.copy(
                phase = WifiProvisionUiState.Phase.ConnectingBle,
                error = null,
                pickDismissed = false,
                pendingAddress = null,
                pendingName = null,
            )
        }

        val nymeaService = NymeaWifiService(bleScanner, bleConnectionFactory, dispatchers.default)
        session.restart(nymeaService).launch {
            session.awaitRelease()

            connect(nymeaService)
                .onSuccess { deviceName ->
                    Logger.i { "$TAG: BLE connected to: $deviceName" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.DeviceFound,
                            deviceName = deviceName,
                            deviceAddress = nymeaService.deviceAddress?.let(::normalizeBleAddress),
                        )
                    }
                }
                .onFailure { e ->
                    if (e is NoDevicePickedException) {
                        Logger.i { "$TAG: device chooser dismissed" }
                        _uiState.update {
                            it.copy(phase = WifiProvisionUiState.Phase.Idle, error = null, pickDismissed = true)
                        }
                        return@onFailure
                    }
                    Logger.e(e) { "$TAG: BLE connect failed" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Idle,
                            error =
                            if (e is DeviceNotFoundException) {
                                WifiProvisionError.DeviceNotFound
                            } else {
                                WifiProvisionError.ConnectFailed(e.message ?: "Unknown error")
                            },
                        )
                    }
                }
        }
    }

    /** Called when the user confirms they want to scan networks after device discovery. */
    fun scanNetworks() {
        // Only once connected, and not while another request is running: a second tap must not send a second scan.
        val phase = _uiState.value.phase
        if (phase != WifiProvisionUiState.Phase.DeviceFound && phase != WifiProvisionUiState.Phase.Connected) return
        val nymeaService = session.service ?: return
        session.scope.launch {
            _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.LoadingNetworks) }

            nymeaService
                .scanNetworks()
                .onSuccess { networks ->
                    _uiState.update {
                        it.copy(phase = WifiProvisionUiState.Phase.Connected, networks = deduplicateBySsid(networks))
                    }
                }
                .onFailure { e ->
                    Logger.e(e) { "$TAG: scanNetworks failed" }
                    if (e is DeviceResponseTimeoutException) {
                        // A late reply could answer the next command, so this connection is done.
                        session.end()
                        _uiState.value = notRespondingState
                        return@onFailure
                    }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Connected,
                            error = WifiProvisionError.ScanFailed(e.message ?: "Unknown error"),
                        )
                    }
                }
        }
    }

    /**
     * Send WiFi credentials to the device.
     *
     * @param ssid The target network SSID.
     * @param password The network password (empty string for open networks).
     */
    fun provisionWifi(ssid: String, password: String, hidden: Boolean = false) {
        // Connected only: a second tap while the first is in flight must not send the credentials twice.
        if (ssid.isBlank() || _uiState.value.phase != WifiProvisionUiState.Phase.Connected) return
        val nymeaService = session.service ?: return

        _uiState.update {
            it.copy(
                phase = WifiProvisionUiState.Phase.Provisioning,
                error = null,
                ipAddress = null,
                provisionStatus = WifiProvisionUiState.ProvisionStatus.Idle,
            )
        }

        session.scope.launch {
            when (val result = nymeaService.provision(ssid, password, hidden)) {
                is ProvisionResult.Success -> {
                    Logger.i { "$TAG: Provisioned successfully" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Connected,
                            ipAddress = result.ipAddress,
                            provisionStatus = WifiProvisionUiState.ProvisionStatus.Success,
                        )
                    }
                }

                is ProvisionResult.Failure -> {
                    Logger.w { "$TAG: Provision failed: ${result.message}" }
                    if (result.errorCode == NO_RESPONSE_CODE) {
                        session.end()
                        _uiState.value = notRespondingState
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Connected,
                            provisionStatus = WifiProvisionUiState.ProvisionStatus.Failed,
                            error = WifiProvisionError.ProvisionFailed(result.message),
                        )
                    }
                }
            }
        }
    }

    /** Cancel any scan or pending confirmation, close any active BLE connection and return to the initial state. */
    fun disconnect() {
        // Ending the session first means nothing still in flight can write after the reset, which is the final state.
        session.end()
        discovery.clear()
        _uiState.value = initialState
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled; the session closes its service from the application scope.
        session.end()
    }

    companion object {
        private const val TAG = "WifiProvisionViewModel"

        /**
         * Deduplicate networks by SSID, keeping the entry with the strongest signal for each. Since we only send SSID
         * (not BSSID) to the device, showing duplicates is confusing.
         */
        internal fun deduplicateBySsid(networks: List<WifiNetwork>): List<WifiNetwork> = networks
            .groupBy { it.ssid }
            .map { (_, entries) -> entries.maxBy { it.signalStrength } }
            .sortedByDescending { it.signalStrength }
    }
}
