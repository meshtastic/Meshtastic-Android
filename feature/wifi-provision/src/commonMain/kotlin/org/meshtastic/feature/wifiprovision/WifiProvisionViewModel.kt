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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.feature.wifiprovision.domain.NymeaWifiService
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
    /** Provisioning outcome shown as inline status (matches web flasher pattern). */
    val provisionStatus: ProvisionStatus = ProvisionStatus.Idle,
) {
    enum class Phase {
        /** No operation running — initial state before BLE connect. */
        Idle,

        /** Scanning BLE for a nymea device. */
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
    /** Detail message from the underlying exception (language-agnostic, typically from the BLE stack). */
    val detail: String

    /** BLE connection to the provisioning device failed. */
    data class ConnectFailed(override val detail: String) : WifiProvisionError

    /** WiFi network scan on the device failed. */
    data class ScanFailed(override val detail: String) : WifiProvisionError

    /** Sending WiFi credentials to the device failed. */
    data class ProvisionFailed(override val detail: String) : WifiProvisionError
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for the WiFi provisioning flow.
 *
 * Uses [KoinViewModel] so the instance is scoped to the navigation entry's [ViewModelStoreOwner]. A fresh
 * [NymeaWifiService] (and its own [BleConnectionFactory]-backed [org.meshtastic.core.ble.BleConnection]) is created
 * lazily for each provisioning session and cleaned up via [onCleared].
 */
@KoinViewModel
class WifiProvisionViewModel(
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
    private val dispatchers: CoroutineDispatchers,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiProvisionUiState())
    val uiState: StateFlow<WifiProvisionUiState> = _uiState.asStateFlow()

    /** Lazily-created service; reset on [reset]. */
    private var service: NymeaWifiService? = null

    // region Public actions (called from UI)

    /**
     * Scan for the nearest nymea-networkmanager device and connect to it. Pauses at the
     * [WifiProvisionUiState.Phase.DeviceFound] phase so the user can confirm before proceeding — this is the Android
     * analog of the web flasher's native BLE pairing dialog.
     *
     * @param address Optional MAC address to target a specific device.
     */
    fun connectToDevice(address: String? = null) {
        _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.ConnectingBle, error = null) }

        viewModelScope.launch {
            val nymeaService = NymeaWifiService(bleScanner, bleConnectionFactory, dispatchers.default)
            service = nymeaService

            nymeaService
                .connect(address)
                .onSuccess { deviceName ->
                    Logger.i { "$TAG: BLE connected to: $deviceName" }
                    _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.DeviceFound, deviceName = deviceName) }
                }
                .onFailure { e ->
                    Logger.e(e) { "$TAG: BLE connect failed" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Idle,
                            error = WifiProvisionError.ConnectFailed(e.message ?: "Unknown error"),
                        )
                    }
                }
        }
    }

    /** Called when the user confirms they want to scan networks after device discovery. */
    fun scanNetworks() {
        val nymeaService =
            service
                ?: run {
                    connectToDevice()
                    return
                }
        viewModelScope.launch { loadNetworks(nymeaService) }
    }

    /**
     * Send WiFi credentials to the device.
     *
     * @param ssid The target network SSID.
     * @param password The network password (empty string for open networks).
     */
    fun provisionWifi(ssid: String, password: String) {
        if (ssid.isBlank()) return
        val nymeaService = service ?: return

        _uiState.update {
            it.copy(
                phase = WifiProvisionUiState.Phase.Provisioning,
                error = null,
                provisionStatus = WifiProvisionUiState.ProvisionStatus.Idle,
            )
        }

        viewModelScope.launch {
            when (val result = nymeaService.provision(ssid, password)) {
                is ProvisionResult.Success -> {
                    Logger.i { "$TAG: Provisioned successfully" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Connected,
                            provisionStatus = WifiProvisionUiState.ProvisionStatus.Success,
                        )
                    }
                }
                is ProvisionResult.Failure -> {
                    Logger.w { "$TAG: Provision failed: ${result.message}" }
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

    /** Disconnect and close any active BLE connection. */
    fun disconnect() {
        viewModelScope.launch {
            service?.close()
            service = null
            _uiState.value = WifiProvisionUiState()
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        service?.cancel()
    }

    // region Private helpers

    private suspend fun loadNetworks(nymeaService: NymeaWifiService) {
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
                _uiState.update {
                    it.copy(
                        phase = WifiProvisionUiState.Phase.Connected,
                        error = WifiProvisionError.ScanFailed(e.message ?: "Unknown error"),
                    )
                }
            }
    }

    // endregion

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
