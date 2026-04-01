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
import org.koin.core.annotation.Factory
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.feature.wifiprovision.domain.NymeaWifiService
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import org.meshtastic.feature.wifiprovision.model.WifiNetwork

// ---------------------------------------------------------------------------
// UI State
// ---------------------------------------------------------------------------

data class WifiProvisionUiState(
    val phase: Phase = Phase.Idle,
    val networks: List<WifiNetwork> = emptyList(),
    val selectedNetwork: WifiNetwork? = null,
    val errorMessage: String? = null,
    val provisionSuccess: Boolean = false,
) {
    enum class Phase {
        /** No operation running. */
        Idle,
        /** Scanning BLE for a nymea device. */
        ConnectingBle,
        /** Fetching visible WiFi networks from the device. */
        LoadingNetworks,
        /** Sending WiFi credentials to the device. */
        Provisioning,
    }
}

// ---------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------

/**
 * ViewModel for the WiFi provisioning flow.
 *
 * Uses [Factory] scope so a fresh [NymeaWifiService] (and its own [BleConnectionFactory]-backed
 * [org.meshtastic.core.ble.BleConnection]) is created for each provisioning session.
 */
@Factory
class WifiProvisionViewModel(
    private val bleScanner: BleScanner,
    private val bleConnectionFactory: BleConnectionFactory,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WifiProvisionUiState())
    val uiState: StateFlow<WifiProvisionUiState> = _uiState.asStateFlow()

    /** Lazily-created service; reset on [reset]. */
    private var service: NymeaWifiService? = null

    // region Public actions (called from UI)

    /**
     * Scan for the nearest nymea-networkmanager device and connect to it, then immediately
     * fetch the list of available WiFi networks.
     *
     * @param address Optional MAC address to target a specific device.
     */
    fun connectAndScanNetworks(address: String? = null) {
        _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.ConnectingBle, errorMessage = null) }

        viewModelScope.launch {
            val nymeaService = NymeaWifiService(bleScanner, bleConnectionFactory)
            service = nymeaService

            nymeaService.connect(address).onFailure { e ->
                Logger.e(e) { "$TAG: BLE connect failed" }
                _uiState.update {
                    it.copy(
                        phase = WifiProvisionUiState.Phase.Idle,
                        errorMessage = "Could not connect: ${e.message}",
                    )
                }
                return@launch
            }

            loadNetworks(nymeaService)
        }
    }

    /** Select a network from the scanned list (called when the user taps a row). */
    fun selectNetwork(network: WifiNetwork) {
        _uiState.update { it.copy(selectedNetwork = network, errorMessage = null) }
    }

    /**
     * Send WiFi credentials for the [WifiProvisionUiState.selectedNetwork] to the device.
     *
     * @param password The network password (empty string for open networks).
     */
    fun provision(password: String) {
        val network = _uiState.value.selectedNetwork ?: return
        val nymeaService = service ?: return

        _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.Provisioning, errorMessage = null) }

        viewModelScope.launch {
            when (val result = nymeaService.provision(network.ssid, password)) {
                is ProvisionResult.Success -> {
                    Logger.i { "$TAG: Provisioned successfully" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Idle,
                            provisionSuccess = true,
                        )
                    }
                }
                is ProvisionResult.Failure -> {
                    Logger.w { "$TAG: Provision failed: ${result.message}" }
                    _uiState.update {
                        it.copy(
                            phase = WifiProvisionUiState.Phase.Idle,
                            errorMessage = result.message,
                        )
                    }
                }
            }
        }
    }

    /** Re-scan networks without re-connecting. */
    fun refreshNetworks() {
        val nymeaService = service ?: run {
            connectAndScanNetworks()
            return
        }
        viewModelScope.launch { loadNetworks(nymeaService) }
    }

    /** Reset to initial state and close any active BLE connection. */
    fun reset() {
        viewModelScope.launch {
            service?.close()
            service = null
            _uiState.value = WifiProvisionUiState()
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { service?.close() }
    }

    // region Private helpers

    private suspend fun loadNetworks(nymeaService: NymeaWifiService) {
        _uiState.update { it.copy(phase = WifiProvisionUiState.Phase.LoadingNetworks) }

        nymeaService.scanNetworks()
            .onSuccess { networks ->
                _uiState.update {
                    it.copy(
                        phase = WifiProvisionUiState.Phase.Idle,
                        networks = networks.sortedByDescending { n -> n.signalStrength },
                    )
                }
            }
            .onFailure { e ->
                Logger.e(e) { "$TAG: scanNetworks failed" }
                _uiState.update {
                    it.copy(
                        phase = WifiProvisionUiState.Phase.Idle,
                        errorMessage = "Failed to load networks: ${e.message}",
                    )
                }
            }
    }

    // endregion

    companion object {
        private const val TAG = "WifiProvisionViewModel"
    }
}
