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
package org.meshtastic.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.MeshtasticBleConstants
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlin.time.Duration

/**
 * Platform-neutral ViewModel that drives the Connections screen: device discovery (BLE/USB/TCP), scan state, current
 * selection, and connection-progress chatter.
 *
 * Subclassed per-platform (see `AndroidScannerViewModel`, `JvmScannerViewModel`) to plug in platform-specific bonding /
 * permission flows.
 */
@Suppress("LongParameterList", "TooManyFunctions")
open class ScannerViewModel(
    protected val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    private val radioInterfaceService: RadioInterfaceService,
    private val radioPrefs: RadioPrefs,
    private val recentAddressesDataSource: RecentAddressesDataSource,
    private val getDiscoveredDevicesUseCase: GetDiscoveredDevicesUseCase,
    private val networkRepository: NetworkRepository,
    private val dispatchers: CoroutineDispatchers,
    private val uiPrefs: UiPrefs,
    private val bleScanner: BleScanner? = null,
) : ViewModel() {

    // ── Mock / demo transport ─────────────────────────────────────────────────────────────────
    private val _showMockTransport = MutableStateFlow(false)
    val showMockTransport: StateFlow<Boolean> = _showMockTransport.asStateFlow()

    // ── Connection-progress chatter (surfaced as the bottom status pill) ──────────────────────
    private val _connectionProgressText = MutableStateFlow<String?>(null)

    /**
     * Transient, fine-grained status text emitted during connect/bonding (e.g. "Bonding…", "Requesting config…").
     * Nullable because `serviceRepository.connectionProgress` does not emit during steady-state.
     *
     * Persistent "Not connected / Connecting / Connected" copy is derived separately in
     * `ConnectionsViewModel.connectionStatus` so the UI can choose `progress ?: status`.
     */
    val connectionProgressText: StateFlow<String?> = _connectionProgressText.asStateFlow()

    /**
     * Back-compat alias for [connectionProgressText]. Kept so existing screens/tests don't need a synchronised rename
     * in the same commit.
     */
    @Deprecated("Use connectionProgressText", ReplaceWith("connectionProgressText"))
    val errorText: StateFlow<String?>
        get() = connectionProgressText

    // ── BLE scanning ──────────────────────────────────────────────────────────────────────────
    private val _isBleScanning = MutableStateFlow(false)
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    /** User preference that controls whether BLE scanning auto-starts when the Connections screen opens. */
    val bleAutoScan: StateFlow<Boolean> = uiPrefs.bleAutoScan

    private val scannedBleDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    private var scanJob: Job? = null

    // ── Network scanning (NSD gating) ─────────────────────────────────────────────────────────
    private val _isNetworkScanning = MutableStateFlow(false)
    val isNetworkScanning: StateFlow<Boolean> = _isNetworkScanning.asStateFlow()

    /** User preference that controls whether NSD network scanning auto-starts when the Connections screen opens. */
    val networkAutoScan: StateFlow<Boolean> = uiPrefs.networkAutoScan

    // ── Transport-section visibility (filter chips) ───────────────────────────────────────────

    /** Whether the BLE section is visible in the Connections device list. Defaults to `true`. */
    val showBleTransport: StateFlow<Boolean> = uiPrefs.showBleTransport

    /** Whether the Network (TCP/NSD) section is visible in the Connections device list. Defaults to `true`. */
    val showNetworkTransport: StateFlow<Boolean> = uiPrefs.showNetworkTransport

    /** Whether the USB section is visible in the Connections device list. Defaults to `true`. */
    val showUsbTransport: StateFlow<Boolean> = uiPrefs.showUsbTransport

    fun setShowBleTransport(enabled: Boolean) = uiPrefs.setShowBleTransport(enabled)

    fun setShowNetworkTransport(enabled: Boolean) = uiPrefs.setShowNetworkTransport(enabled)

    fun setShowUsbTransport(enabled: Boolean) = uiPrefs.setShowUsbTransport(enabled)

    /**
     * Resolved NSD services flow, gated by [_isNetworkScanning]. When scanning is inactive, emits `emptyList()` so
     * `NsdManager.discoverServices()` is never triggered. Android 15+ shows a system consent dialog the first time
     * `resolvedList` is subscribed, so the gate ensures NSD only runs when the user explicitly requests it.
     */
    private val gatedResolvedList =
        _isNetworkScanning.flatMapLatest { scanning ->
            if (scanning) networkRepository.resolvedList else flowOf(emptyList())
        }

    private val discoveredDevicesFlow: StateFlow<DiscoveredDevices> =
        showMockTransport
            .flatMapLatest { showMock -> getDiscoveredDevicesUseCase.invoke(showMock, gatedResolvedList) }
            .stateInWhileSubscribed(initialValue = DiscoveredDevices())

    init {
        _showMockTransport.value = radioInterfaceService.isMockTransport()
        serviceRepository.connectionProgress.onEach { _connectionProgressText.value = it }.launchIn(viewModelScope)
        Logger.d { "ScannerViewModel created" }
    }

    override fun onCleared() {
        super.onCleared()
        stopBleScan()
        stopNetworkScan()
        Logger.d { "ScannerViewModel cleared" }
    }

    // ── Device lists for UI ──────────────────────────────────────────────────────────────────

    /**
     * Combined bonded + scanned BLE devices for the UI.
     *
     * Sorted by signal strength — scanned devices with a known RSSI appear first in descending order (strongest signal
     * at the top), followed by bonded-only devices without a scan RSSI, sorted by name.
     */
    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(discoveredDevicesFlow, scannedBleDevices) { discovered, scannedMap ->
            val bonded = discovered.bleDevices.filterIsInstance<DeviceListEntry.Ble>()
            val bondedAddresses = bonded.mapTo(mutableSetOf()) { it.address }

            // Scanned-but-not-bonded devices are explicitly flagged unbonded so the UI routes through
            // requestBonding() — which on Android triggers createBond() for the pairing dialog before connecting.
            val unbondedScanned =
                scannedMap.values
                    .asSequence()
                    .filter { it.address !in bondedAddresses }
                    .map { DeviceListEntry.Ble(device = it, bonded = false) }

            // For bonded devices, attach the latest scan RSSI (if we've seen an advertisement this session) so they
            // sort alongside unbonded entries by signal strength.
            val bondedWithRssi =
                bonded.asSequence().map { entry ->
                    val scanned = scannedMap[entry.address]
                    if (scanned != null && scanned.rssi != null) entry.copy(device = scanned) else entry
                }

            (bondedWithRssi + unbondedScanned)
                .sortedWith(
                    compareByDescending<DeviceListEntry.Ble> { it.device.rssi != null }
                        .thenByDescending { it.device.rssi ?: Int.MIN_VALUE }
                        .thenBy { it.name },
                )
                .toList()
        }
            .flowOn(dispatchers.default)
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = emptyList())

    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.usbDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.discoveredTcpDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.recentTcpDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    // ── Current selection ────────────────────────────────────────────────────────────────────

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    /** The persisted device name from the last selection, for use as a UI fallback. */
    val persistedDeviceName: StateFlow<String?> = radioPrefs.devName

    /** Non-null variant of [selectedAddressFlow] that substitutes [NO_DEVICE_SELECTED] for `null`. */
    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateInWhileSubscribed(initialValue = selectedAddressFlow.value ?: NO_DEVICE_SELECTED)

    // ── Scan commands ────────────────────────────────────────────────────────────────────────

    fun startBleScan() {
        if (_isBleScanning.value || bleScanner == null) return

        _isBleScanning.value = true
        scannedBleDevices.value = emptyMap()

        scanJob =
            safeLaunch(tag = "startBleScan") {
                try {
                    bleScanner
                        .scan(timeout = Duration.INFINITE, serviceUuid = MeshtasticBleConstants.SERVICE_UUID)
                        .flowOn(dispatchers.io)
                        .collect { device ->
                            scannedBleDevices.update { current ->
                                val existing = current[device.address]
                                // Replace if RSSI changed so the UI reflects the latest advertisement. Keep the same
                                // instance otherwise to avoid unnecessary recomposition.
                                if (existing != null && existing.rssi == device.rssi) {
                                    current
                                } else {
                                    current + (device.address to device)
                                }
                            }
                        }
                } finally {
                    _isBleScanning.value = false
                }
            }
    }

    fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        _isBleScanning.value = false
        // Drop cached advertisements so stale RSSI values don't linger in the UI after the scan ends.
        scannedBleDevices.value = emptyMap()
    }

    /** Convenience command: start scanning if idle, stop otherwise. Persists the resulting state to prefs. */
    fun toggleBleScan() {
        if (_isBleScanning.value) stopBleScan() else startBleScan()
        uiPrefs.setBleAutoScan(_isBleScanning.value)
    }

    fun startNetworkScan() {
        _isNetworkScanning.value = true
    }

    fun stopNetworkScan() {
        _isNetworkScanning.value = false
    }

    /** Convenience command: start scanning if idle, stop otherwise. Persists the resulting state to prefs. */
    fun toggleNetworkScan() {
        if (_isNetworkScanning.value) stopNetworkScan() else startNetworkScan()
        uiPrefs.setNetworkAutoScan(_isNetworkScanning.value)
    }

    /**
     * Persist the user's intent to auto-scan the network on next screen entry without flipping the active scan flag.
     * Used by the Connections screen when it must defer the actual scan start until after the system permission grant
     * dialog resolves — the persisted intent ensures auto-start fires once permission is granted.
     */
    fun persistNetworkAutoScanIntent(enabled: Boolean) {
        uiPrefs.setNetworkAutoScan(enabled)
    }

    // ── Device selection / disconnect ───────────────────────────────────────────────────────

    fun changeDeviceAddress(address: String) {
        Logger.i { "Attempting to change device address to ${address.anonymize()}" }
        radioController.setDeviceAddress(address)
    }

    fun addRecentAddress(address: String, name: String) {
        if (!address.startsWith(TCP_DEVICE_PREFIX)) return
        safeLaunch(tag = "addRecentAddress") { recentAddressesDataSource.add(RecentAddress(address, name)) }
    }

    fun removeRecentAddress(address: String) {
        safeLaunch(tag = "removeRecentAddress") { recentAddressesDataSource.remove(address) }
    }

    /**
     * Called by the UI when a device has been tapped. BLE and USB entries may still need bonding/permission — the
     * concrete return value tells the caller whether the connection was initiated immediately.
     *
     * @return `true` if the connection has been initiated; `false` if bonding/permission is pending.
     */
    fun onSelected(entry: DeviceListEntry): Boolean {
        radioPrefs.setDevName(entry.name)
        return when (entry) {
            is DeviceListEntry.Ble -> {
                if (entry.bonded) {
                    changeDeviceAddress(entry.fullAddress)
                    true
                } else {
                    requestBonding(entry)
                    false
                }
            }
            is DeviceListEntry.Usb -> {
                if (entry.bonded) {
                    changeDeviceAddress(entry.fullAddress)
                    true
                } else {
                    requestPermission(entry)
                    false
                }
            }
            is DeviceListEntry.Tcp -> {
                safeLaunch(tag = "onSelectedTcp") {
                    addRecentAddress(entry.fullAddress, entry.name)
                    changeDeviceAddress(entry.fullAddress)
                }
                true
            }
            is DeviceListEntry.Mock -> {
                changeDeviceAddress(entry.fullAddress)
                true
            }
        }
    }

    /**
     * Initiates the bonding process and connects to the device upon success.
     *
     * The default implementation connects directly without explicit bonding, which is correct for Desktop/JVM where the
     * OS Bluetooth stack handles pairing during the GATT connection. Android overrides this to call `createBond()`
     * first.
     */
    protected open fun requestBonding(entry: DeviceListEntry.Ble) {
        changeDeviceAddress(entry.fullAddress)
    }

    protected open fun requestPermission(entry: DeviceListEntry.Usb) = Unit

    fun disconnect() {
        radioPrefs.setDevName(null)
        changeDeviceAddress(NO_DEVICE_SELECTED)
    }
}
