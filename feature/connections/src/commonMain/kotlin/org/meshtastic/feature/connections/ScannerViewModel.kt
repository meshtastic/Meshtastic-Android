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
package org.meshtastic.feature.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanStartException
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.MeshtasticBleConstants
import org.meshtastic.core.common.util.safeCatchingAll
import org.meshtastic.core.datastore.FirmwareRecoveryDataSource
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.PendingFirmwareRecovery
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bluetooth_scan_start_failed
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.DiscoveredDevices
import org.meshtastic.feature.connections.model.GetDiscoveredDevicesUseCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal val BLE_SCAN_START_FAILURE_RETRY_COOLDOWN = 15.seconds
private const val BLE_SCAN_START_FAILURE_MESSAGE_FALLBACK =
    "Bluetooth scan couldn't start. Try again, or toggle Bluetooth if the problem continues."

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
    private val firmwareRecoveryDataSource: FirmwareRecoveryDataSource,
    private val bleScanner: BleScanner? = null,
) : ViewModel() {

    // ── Mock / demo transport ─────────────────────────────────────────────────────────────────
    private val _showMockTransport = MutableStateFlow(false)

    /** Whether the mock/demo transport is currently selected. */
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

    // ── BLE scanning ──────────────────────────────────────────────────────────────────────────
    private val _isBleScanning = MutableStateFlow(false)

    /** Whether a BLE scan is currently active. */
    val isBleScanning: StateFlow<Boolean> = _isBleScanning.asStateFlow()

    /** User preference that controls whether BLE scanning auto-starts when the Connections screen opens. */
    val bleAutoScan: StateFlow<Boolean> = uiPrefs.bleAutoScan

    private val scannedBleDevices = MutableStateFlow<Map<String, BleDevice>>(emptyMap())
    private val discoveryOrder = MutableStateFlow<List<String>>(emptyList())
    private var scanJob: Job? = null
    private var scanStartFailureCooldownJob: Job? = null
    private val scanStartFailureCooldownActive = MutableStateFlow(false)
    private var scanStartFailureCooldownGeneration = 0

    // Generation counter that owns the `_isBleScanning` flag's cleanup. The scan coroutine's `finally` block may run
    // asynchronously on the IO dispatcher after a stop+restart has already kicked off a new scan; without this guard
    // the old job's finally would reset the flag on the new scan's state. Bumped on each start and each stop so that
    // only the current generation's finally may clear the flag.
    private val scanGeneration = MutableStateFlow(0)

    // ── Network scanning (NSD gating) ─────────────────────────────────────────────────────────
    private val _isNetworkScanning = MutableStateFlow(false)

    /** Whether an NSD network scan is currently active. */
    val isNetworkScanning: StateFlow<Boolean> = _isNetworkScanning.asStateFlow()

    /** User preference that controls whether NSD network scanning auto-starts when the Connections screen opens. */
    val networkAutoScan: StateFlow<Boolean> = uiPrefs.networkAutoScan

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
        serviceRepository.connectionState
            .onEach { state ->
                if (state is ConnectionState.Connected) {
                    stopAllScans()
                    clearRecoveryIfConnectedDeviceReturned()
                }
            }
            .launchIn(viewModelScope)
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
     * BLE devices for the UI — restricted to those currently visible via an active scan.
     *
     * Previously bonded / system-paired peripherals that aren't advertising right now are intentionally excluded so the
     * list reflects what's actually nearby. The currently-selected device is the one exception: it's always kept so the
     * active connection stays visible (a connected radio stops advertising and would otherwise drop out).
     *
     * Sorted for stability to prevent "shifting" as advertisements arrive: bonded devices appear first (sorted by
     * name), followed by unbonded scanned devices in the order they were first discovered. RSSI updates are reflected
     * on the cards but do not trigger a re-sort.
     */
    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(
            discoveredDevicesFlow,
            scannedBleDevices,
            discoveryOrder,
            radioInterfaceService.currentDeviceAddressFlow,
        ) { discovered, scannedMap, order, selectedAddress ->
            // Surface a bonded device only when it's currently visible via scan (advertising) or it's the selected
            // device — this hides stale system-bonded peripherals that aren't nearby.
            val bonded =
                discovered.bleDevices.filterIsInstance<DeviceListEntry.Ble>().filter {
                    it.address in scannedMap || it.fullAddress == selectedAddress
                }
            val bondedAddresses = bonded.mapTo(mutableSetOf()) { it.address }

            // Scanned-but-not-bonded devices are explicitly flagged unbonded so the UI routes through
            // requestBonding() — which on Android triggers createBond() for the pairing dialog before connecting.
            // Preserves discovery order to prevent items jumping around during the scan burst.
            val unbondedScanned =
                order
                    .filter { it !in bondedAddresses }
                    .mapNotNull { address ->
                        scannedMap[address]?.let { DeviceListEntry.Ble(device = it, bonded = false) }
                    }

            // For bonded devices, attach the latest scan RSSI (if we've seen an advertisement this session) so the
            // UI can show the signal indicator, but keep them sorted by name for stability.
            val bondedForUi =
                bonded
                    .map { entry ->
                        val scanned = scannedMap[entry.address]
                        if (scanned != null && scanned.rssi != null) entry.copy(device = scanned) else entry
                    }
                    .sortedBy { it.name }

            bondedForUi + unbondedScanned
        }
            .flowOn(dispatchers.default)
            .distinctUntilChanged()
            .stateInWhileSubscribed(initialValue = emptyList())

    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.usbDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    /** Discovered (NSD) TCP devices for the Connections device list, gated by the network-scan flag. */
    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.discoveredTcpDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    /** Recently-used TCP addresses for the Connections device list. */
    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        discoveredDevicesFlow.map { it.recentTcpDevices }.distinctUntilChanged().stateInWhileSubscribed(emptyList())

    /**
     * A firmware update that didn't finish, leaving a device stranded in bootloader mode. Non-null drives the recovery
     * banner on the Connections screen, whose tap deep-links into the Firmware Update screen to re-flash the device.
     */
    val pendingRecovery: StateFlow<PendingFirmwareRecovery?> =
        firmwareRecoveryDataSource.pending.distinctUntilChanged().stateInWhileSubscribed(initialValue = null)

    // ── Current selection ────────────────────────────────────────────────────────────────────

    /** The currently-selected device address, or `null` when nothing is selected. */
    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    /** The persisted device name from the last selection, for use as a UI fallback. */
    val persistedDeviceName: StateFlow<String?> = radioPrefs.devName

    /** Non-null variant of [selectedAddressFlow] that substitutes [NO_DEVICE_SELECTED] for `null`. */
    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateInWhileSubscribed(initialValue = selectedAddressFlow.value ?: NO_DEVICE_SELECTED)

    // ── Active transport pane ────────────────────────────────────────────────────────────────

    /** The single transport pane currently rendered by the Connections screen. */
    val activeTransport: StateFlow<DeviceType> =
        combine(uiPrefs.selectedConnectionTransport, selectedAddressFlow) { preferred, selectedAddress ->
            resolveActiveTransport(preferred, selectedAddress)
        }
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue =
                resolveActiveTransport(uiPrefs.selectedConnectionTransport.value, selectedAddressFlow.value),
            )

    /** Selects one Connections transport pane and stops scans that cannot belong to that pane. */
    fun selectTransport(type: DeviceType) {
        when (type) {
            DeviceType.BLE -> stopNetworkScan()
            DeviceType.TCP -> stopBleScan()
            DeviceType.USB -> stopAllScans()
        }
        if (activeTransport.value != type) uiPrefs.setSelectedConnectionTransport(type)
    }

    // ── Scan commands ────────────────────────────────────────────────────────────────────────

    /**
     * Starts BLE scanning. Enforces mutual exclusion (cancels any active network scan first). No-op if already scanning
     * or if [bleScanner] is null.
     *
     * The `finally` that clears [_isBleScanning] is guarded by a generation counter so a stale cancellation from a
     * prior scan cannot reset the flag on this new scan's state.
     */
    fun startBleScan() {
        if (_isBleScanning.value || bleScanner == null || scanStartFailureCooldownActive.value) return
        // Cancel the other scan first so only one flag is ever true. Both stop methods are idempotent.
        stopNetworkScan()

        _isBleScanning.value = true
        val generation = scanGeneration.value + 1
        scanGeneration.value = generation

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
                            if (device.address !in discoveryOrder.value) {
                                discoveryOrder.update { it + device.address }
                            }
                        }
                } catch (ex: BleScanStartException) {
                    handleBleScanStartFailure(ex, generation)
                } finally {
                    if (scanGeneration.value == generation) {
                        _isBleScanning.value = false
                        scanJob = null
                    }
                }
            }
    }

    /**
     * Starts BLE scanning for screen-entry auto-scan only when no device is already selected. Manual scan toggles call
     * [startBleScan] directly so users can still discover/switch devices while connected.
     */
    fun startBleAutoScan() {
        if (activeTransport.value != DeviceType.BLE) return
        val selectedAddress = selectedAddressFlow.value
        if (selectedAddress != null && selectedAddress != NO_DEVICE_SELECTED) return
        startBleScan()
    }

    /**
     * Cancels the active BLE scan and resets the scanning flag. Idempotent.
     *
     * Bumps [scanGeneration] so any in-flight `finally` from the cancelled job cannot reset `_isBleScanning` after a
     * subsequent [startBleScan] has flipped it back to `true`.
     */
    fun stopBleScan() {
        scanJob?.cancel()
        scanJob = null
        scanGeneration.value = scanGeneration.value + 1
        _isBleScanning.value = false
    }

    /**
     * Toggles BLE scanning. Persists the auto-scan preference only when the scan actually activates, and clears the
     * opposite [networkAutoScan] preference to keep persisted state consistent with the runtime mutual-exclusion
     * invariant.
     */
    fun toggleBleScan() {
        if (_isBleScanning.value) {
            stopBleScan()
            uiPrefs.setBleAutoScan(false)
        } else {
            startBleScan()
            // Only persist enable-intent (and clear the opposite pref) if start actually worked — e.g. not
            // blocked by a null bleScanner.
            if (_isBleScanning.value) {
                uiPrefs.setBleAutoScan(true)
                uiPrefs.setNetworkAutoScan(false)
            }
        }
    }

    private suspend fun handleBleScanStartFailure(exception: BleScanStartException, generation: Int) {
        if (scanGeneration.value != generation) return

        scanGeneration.value = generation + 1
        scanJob = null
        _isBleScanning.value = false
        uiPrefs.setBleAutoScan(false)
        startBleScanRetryCooldown()

        Logger.w(exception) {
            "BLE scan could not start: ${exception.reason.androidCode} (${exception.reason.description})"
        }
        val errorMessage =
            safeCatchingAll { getStringSuspend(Res.string.bluetooth_scan_start_failed) }
                .getOrDefault(BLE_SCAN_START_FAILURE_MESSAGE_FALLBACK)
        serviceRepository.setErrorMessage(text = errorMessage, severity = Severity.Warn)
    }

    private fun startBleScanRetryCooldown() {
        val generation = ++scanStartFailureCooldownGeneration
        scanStartFailureCooldownActive.value = true
        scanStartFailureCooldownJob?.cancel()
        scanStartFailureCooldownJob =
            viewModelScope.launch {
                try {
                    delay(BLE_SCAN_START_FAILURE_RETRY_COOLDOWN)
                } finally {
                    if (scanStartFailureCooldownGeneration == generation) {
                        scanStartFailureCooldownActive.value = false
                        scanStartFailureCooldownJob = null
                    }
                }
            }
    }

    /**
     * Starts NSD network scanning. Enforces the same mutual-exclusion invariant as [startBleScan]; starting Network
     * cancels any active BLE scan first.
     */
    fun startNetworkScan() {
        if (_isNetworkScanning.value) return
        // Cancel the other scan first so only one flag is ever true. Both stop methods are idempotent.
        stopBleScan()
        _isNetworkScanning.value = true
    }

    /** Starts NSD scanning for screen-entry auto-scan only when the Network pane is active. */
    fun startNetworkAutoScan() {
        if (activeTransport.value != DeviceType.TCP) return
        val selectedAddress = selectedAddressFlow.value
        if (selectedAddress != null && selectedAddress != NO_DEVICE_SELECTED) return
        startNetworkScan()
    }

    /** Cancels the active network scan and resets the scanning flag. Idempotent. */
    fun stopNetworkScan() {
        _isNetworkScanning.value = false
    }

    /** Stops both BLE and network scans. Idempotent — safe to call when neither scan is active. */
    private fun stopAllScans() {
        stopBleScan()
        stopNetworkScan()
    }

    /**
     * Toggles network scanning. Persists the auto-scan preference only when the scan actually activates, and clears the
     * opposite [bleAutoScan] preference to keep persisted state consistent with the runtime mutual-exclusion invariant.
     */
    fun toggleNetworkScan() {
        if (_isNetworkScanning.value) {
            stopNetworkScan()
            uiPrefs.setNetworkAutoScan(false)
        } else {
            startNetworkScan()
            // Only persist enable-intent (and clear the opposite pref) if start actually worked.
            if (_isNetworkScanning.value) {
                uiPrefs.setNetworkAutoScan(true)
                uiPrefs.setBleAutoScan(false)
            }
        }
    }

    /**
     * Persists the user's intent to auto-scan the network on next screen entry without flipping the active scan flag.
     * Used by the Connections screen when it must defer the actual scan start until after the system permission grant
     * dialog resolves. When [enabled] is `true`, also clears [bleAutoScan] so persisted state mirrors the runtime
     * mutual-exclusion invariant (at most one of [bleAutoScan] / [networkAutoScan] may be true).
     */
    fun persistNetworkAutoScanIntent(enabled: Boolean) {
        uiPrefs.setNetworkAutoScan(enabled)
        if (enabled) uiPrefs.setBleAutoScan(false)
    }

    // ── Device selection / disconnect ───────────────────────────────────────────────────────

    /** Asynchronously tells the radio controller to connect to [address]. */
    fun changeDeviceAddress(address: String) {
        Logger.i { "Attempting to change device address to ${address.anonymize()}" }
        safeLaunch(tag = "changeDeviceAddress") { radioController.setDeviceAddress(address) }
    }

    /**
     * Persists [address] in the recent-TCP list under [name]. No-op when [address] does not start with
     * [TCP_DEVICE_PREFIX].
     */
    fun addRecentAddress(address: String, name: String) {
        if (!address.startsWith(TCP_DEVICE_PREFIX)) return
        safeLaunch(tag = "addRecentAddress") { recentAddressesDataSource.add(RecentAddress(address, name)) }
    }

    /** Removes [address] from the recent-TCP list. */
    fun removeRecentAddress(address: String) {
        safeLaunch(tag = "removeRecentAddress") { recentAddressesDataSource.remove(address) }
    }

    /**
     * Connects to a manually-entered TCP address. Wraps the manual-entry flow with the same scan-cancel invariant as
     * [onSelected]: stops discovery before connection setup so the manual connect does not race an in-progress
     * BLE/network scan for radio resources.
     */
    fun connectToManualAddress(fullAddress: String) {
        val displayAddress = fullAddress.removePrefix(TCP_DEVICE_PREFIX)
        stopAllScans()
        uiPrefs.setSelectedConnectionTransport(DeviceType.TCP)
        addRecentAddress(fullAddress, displayAddress)
        changeDeviceAddress(fullAddress)
    }

    /**
     * Called by the UI when a device has been tapped. BLE and USB entries may still need bonding/permission — the
     * concrete return value tells the caller whether the connection was initiated immediately.
     *
     * @return `true` if the connection has been initiated; `false` if bonding/permission is pending.
     */
    fun onSelected(entry: DeviceListEntry): Boolean {
        // Stop discovery the moment the user picks a device, before any connection setup runs. The connect
        // attempt (BLE GATT or TCP) contends with an active BLE scan for the same radio resources during the
        // handshake; cancelling here keeps the lifecycle ordered: scan → stop → connect.
        stopAllScans()
        recordSelectedTransport(entry.fullAddress)
        radioPrefs.setDevName(entry.name)
        addRecentAddress(entry.fullAddress, entry.name)
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
                safeLaunch(tag = "onSelectedTcp") { changeDeviceAddress(entry.fullAddress) }
                true
            }

            is DeviceListEntry.Mock -> {
                changeDeviceAddress(entry.fullAddress)
                true
            }

            is DeviceListEntry.Replay -> {
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

    /** Platform hook for requesting USB permission before connecting; default is a no-op. */
    protected open fun requestPermission(entry: DeviceListEntry.Usb) = Unit

    /** Clears the persisted device name and tells the radio controller to disconnect. */
    fun disconnect() {
        radioPrefs.setDevName(null)
        changeDeviceAddress(NO_DEVICE_SELECTED)
    }

    // ponytail: the recovery banner has no manual dismiss — it clears on reconnect or successful recovery. Add a
    // dismiss action if a persistently-failing recovery (truly bricked device) proves to nag users.

    /**
     * If the device that had a pending recovery record has since reconnected normally (e.g. its bootloader timed out
     * and booted a valid app, or a prior update actually succeeded), retire the record so the banner doesn't linger.
     */
    private fun clearRecoveryIfConnectedDeviceReturned() {
        safeLaunch(tag = "clearRecovery") {
            val recovery = firmwareRecoveryDataSource.pending.first() ?: return@safeLaunch
            if (recovery.fullAddress == radioInterfaceService.currentDeviceAddressFlow.value) {
                firmwareRecoveryDataSource.clear()
            }
        }
    }

    private fun resolveActiveTransport(preferred: DeviceType?, selectedAddress: String?): DeviceType =
        preferred ?: selectedAddress?.let(DeviceType::fromAddress) ?: DeviceType.BLE

    private fun recordSelectedTransport(fullAddress: String) {
        DeviceType.fromAddress(fullAddress)?.let(uiPrefs::setSelectedConnectionTransport)
    }
}
