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
package org.meshtastic.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import org.meshtastic.core.ble.BleCharacteristic
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleConnectionState
import org.meshtastic.core.ble.BleDevice
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.BluetoothState
import org.meshtastic.core.ble.DisconnectReason
import kotlin.time.Duration
import kotlin.uuid.Uuid

class FakeBleDevice(
    override val address: String,
    override val name: String? = "Fake Device",
    initialState: BleConnectionState = BleConnectionState.Disconnected(),
    override val rssi: Int? = null,
) : BaseFake(),
    BleDevice {
    private val _state = mutableStateFlow(initialState)
    override val state: StateFlow<BleConnectionState> = _state.asStateFlow()

    private val _isBonded = mutableStateFlow(false)
    override val isBonded: Boolean
        get() = _isBonded.value

    override val isConnected: Boolean
        get() = _state.value == BleConnectionState.Connected

    override suspend fun readRssi(): Int = DEFAULT_RSSI

    override suspend fun bond() {
        _isBonded.value = true
    }

    fun setState(newState: BleConnectionState) {
        _state.value = newState
    }

    companion object {
        private const val DEFAULT_RSSI = -60
    }
}

class FakeBleScanner :
    BaseFake(),
    BleScanner {
    private val foundDevices = mutableSharedFlow<BleDevice>(replay = 10)

    var lastScanServiceUuid: Uuid? = null
        private set

    var lastScanAddress: String? = null
        private set

    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        lastScanServiceUuid = serviceUuid
        lastScanAddress = address
        return flow { emitAll(foundDevices) }
    }

    fun emitDevice(device: BleDevice) {
        foundDevices.tryEmit(device)
    }
}

class FakeBleConnection :
    BaseFake(),
    BleConnection {
    private val _device = mutableStateFlow<BleDevice?>(null)
    override val device: BleDevice?
        get() = _device.value

    override val deviceFlow: StateFlow<BleDevice?> = _device.asStateFlow()

    private val _connectionState = mutableStateFlow<BleConnectionState>(BleConnectionState.Disconnected())
    override val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    /** When > 0, the next [failNextN] calls to [connectAndAwait] return [BleConnectionState.Disconnected]. */
    var failNextN: Int = 0

    /** When non-null, [connectAndAwait] throws this exception instead of connecting. */
    var connectException: Exception? = null

    /** Negotiated write length exposed to callers; `null` means unknown / not negotiated. */
    var maxWriteValueLength: Int? = null

    /** Result returned by [invalidateServiceCache]; defaults to `false` to match the [BleConnection] default. */
    var invalidateServiceCacheResult: Boolean = false

    /** Number of times [invalidateServiceCache] has been invoked. */
    var invalidateServiceCacheCalls: Int = 0

    /** Optional callback invoked at the end of [disconnect] (e.g. seed freshly-discovered services on reconnect). */
    var onDisconnect: (() -> Unit)? = null

    /** Number of times [disconnect] has been invoked. */
    var disconnectCalls: Int = 0

    /** Number of times [connectAndAwait] has been invoked (including failures). */
    var connectAndAwaitCalls: Int = 0

    /** Number of times [profile] has been invoked. */
    var profileCalls: Int = 0

    /** Externally simulate a remote disconnect (e.g. node power-cycle) for tests that exercise reconnect. */
    fun simulateRemoteDisconnect(reason: DisconnectReason = DisconnectReason.Timeout) {
        _connectionState.value = BleConnectionState.Disconnected(reason)
    }

    /** Service UUIDs that should appear missing — `profile()` throws `NoSuchElementException` for these. */
    val missingServices: MutableSet<Uuid> = mutableSetOf()

    val service = FakeBleService()

    override suspend fun connect(device: BleDevice) {
        _device.value = device
        _connectionState.value = BleConnectionState.Connecting
        if (device is FakeBleDevice) {
            device.setState(BleConnectionState.Connecting)
        }
        _connectionState.value = BleConnectionState.Connected
        if (device is FakeBleDevice) {
            device.setState(BleConnectionState.Connected)
        }
    }

    override suspend fun connectAndAwait(device: BleDevice, timeout: Duration): BleConnectionState {
        connectAndAwaitCalls++
        connectException?.let { throw it }
        if (failNextN > 0) {
            failNextN--
            return BleConnectionState.Disconnected()
        }
        connect(device)
        return BleConnectionState.Connected
    }

    override suspend fun disconnect() {
        disconnectCalls++
        val currentDevice = _device.value
        _connectionState.value = BleConnectionState.Disconnected()
        if (currentDevice is FakeBleDevice) {
            currentDevice.setState(BleConnectionState.Disconnected())
        }
        _device.value = null
        onDisconnect?.invoke()
    }

    override suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T {
        profileCalls++
        if (serviceUuid in missingServices) {
            throw NoSuchElementException("Service $serviceUuid not found")
        }
        // Use Dispatchers.Unconfined so notification emissions are delivered synchronously to
        // collectors (write → immediate notification). This matches the original FakeBleConnection
        // contract and the auto-responding pattern used by DFU/OTA transport tests.
        return CoroutineScope(Dispatchers.Unconfined).setup(service)
    }

    override fun maximumWriteValueLength(writeType: BleWriteType): Int? = maxWriteValueLength

    override fun invalidateServiceCache(): Boolean {
        invalidateServiceCacheCalls++
        return invalidateServiceCacheResult
    }
}

class FakeBleWrite(val characteristic: BleCharacteristic, val data: ByteArray, val writeType: BleWriteType) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FakeBleWrite) return false
        return characteristic == other.characteristic && data.contentEquals(other.data) && writeType == other.writeType
    }

    override fun hashCode(): Int = 31 * (31 * characteristic.hashCode() + data.contentHashCode()) + writeType.hashCode()
}

class FakeBleService : BleService {
    private val availableCharacteristics = mutableSetOf<Uuid>()
    private val notificationFlows = mutableMapOf<Uuid, MutableSharedFlow<ByteArray>>()
    private val readQueues = mutableMapOf<Uuid, MutableList<ByteArray>>()

    val writes = mutableListOf<FakeBleWrite>()

    /** When non-null, [write] throws this exception on every call until explicitly cleared. */
    var writeException: Exception? = null

    /**
     * When non-null, [read] throws this exception instead of returning data. Reset to null before throwing (in the same
     * call).
     */
    var readException: Exception? = null

    /**
     * When non-null, [observe] returns a flow that immediately throws. Reset to null when observe() is called (before
     * flow collection).
     */
    var observeException: Exception? = null

    /** Characteristic-specific observe failures that occur when the returned flow is collected. */
    val observeExceptionsByCharacteristic: MutableMap<Uuid, Exception> = mutableMapOf()

    /** Characteristic-specific observe failures that occur before [BleService.observe]'s onSubscription callback. */
    val observeBeforeSubscriptionExceptionByCharacteristic: MutableMap<Uuid, Exception> = mutableMapOf()

    /** Characteristics whose 2-arg [observe] never invokes onSubscription. Notifications can still be emitted. */
    val observeNeverSubscribeCharacteristics: MutableSet<Uuid> = mutableSetOf()

    override fun hasCharacteristic(characteristic: BleCharacteristic): Boolean =
        availableCharacteristics.contains(characteristic.uuid)

    override fun observe(characteristic: BleCharacteristic): Flow<ByteArray> {
        val failure =
            observeExceptionsByCharacteristic.remove(characteristic.uuid)
                ?: observeException?.also { observeException = null }
        if (failure != null) {
            return flow { throw failure }
        }
        return notificationFlows.getOrPut(characteristic.uuid) { MutableSharedFlow(extraBufferCapacity = 16) }
    }

    /**
     * Overrides the 2-arg observe to prevent false subscriptionReady when testing pre-readiness failures.
     *
     * The default BleService implementation calls `observe(characteristic).onStart { onSubscription() }`, which would
     * invoke [onSubscription] BEFORE any pre-collection failure flow throws. This override throws BEFORE invoking
     * [onSubscription], correctly simulating "observe failed before CCCD/subscription readiness."
     *
     * Pre-readiness failures are sourced (in priority order) from:
     * - [observeBeforeSubscriptionExceptionByCharacteristic] (2-arg-specific, one-shot per uuid)
     * - [observeExceptionsByCharacteristic] (shared with 1-arg observe, one-shot per uuid; defensively treated as a
     *   pre-subscription failure here so the bare onStart wrap cannot swallow it after [onSubscription] runs)
     * - [observeException] (global, one-shot)
     *
     * For characteristics in [observeNeverSubscribeCharacteristics], [onSubscription] is never invoked but
     * notifications are still exposed — the returned flow is the bare SharedFlow with no [onStart] wrap, so
     * [emitNotification] still reaches active collectors.
     */
    override fun observe(characteristic: BleCharacteristic, onSubscription: suspend () -> Unit): Flow<ByteArray> {
        val failure =
            observeBeforeSubscriptionExceptionByCharacteristic.remove(characteristic.uuid)
                ?: observeExceptionsByCharacteristic.remove(characteristic.uuid)
                ?: observeException?.also { observeException = null }
        if (failure != null) {
            // onSubscription is NOT invoked — simulates failure before CCCD/subscription readiness.
            return flow { throw failure }
        }
        val base = observe(characteristic)
        return if (characteristic.uuid in observeNeverSubscribeCharacteristics) {
            base
        } else {
            base.onStart { onSubscription() }
        }
    }

    override suspend fun read(characteristic: BleCharacteristic): ByteArray {
        readException?.let {
            readException = null
            throw it
        }
        return readQueues[characteristic.uuid]?.removeFirstOrNull() ?: ByteArray(0)
    }

    override fun preferredWriteType(characteristic: BleCharacteristic): BleWriteType = BleWriteType.WITH_RESPONSE

    override suspend fun write(characteristic: BleCharacteristic, data: ByteArray, writeType: BleWriteType) {
        writeException?.let { ex -> throw ex }
        availableCharacteristics += characteristic.uuid
        writes += FakeBleWrite(characteristic = characteristic, data = data.copyOf(), writeType = writeType)
    }

    fun addCharacteristic(uuid: Uuid) {
        availableCharacteristics += uuid
    }

    fun emitNotification(uuid: Uuid, data: ByteArray) {
        availableCharacteristics += uuid
        notificationFlows.getOrPut(uuid) { MutableSharedFlow(extraBufferCapacity = 16) }.tryEmit(data)
    }

    fun enqueueRead(uuid: Uuid, data: ByteArray) {
        availableCharacteristics += uuid
        readQueues.getOrPut(uuid) { mutableListOf() }.add(data)
    }
}

class FakeBleConnectionFactory(private val fakeConnection: FakeBleConnection = FakeBleConnection()) :
    BleConnectionFactory {
    override fun create(scope: CoroutineScope, tag: String): BleConnection = fakeConnection
}

@Suppress("EmptyFunctionBlock")
class FakeBluetoothRepository :
    BaseFake(),
    BluetoothRepository {
    private val _state = mutableStateFlow(BluetoothState(hasPermissions = true, enabled = true))
    override val state: StateFlow<BluetoothState> = _state.asStateFlow()

    /**
     * Controls what [bond] does. Defaults to [BondOutcome.Success] so existing tests that never touch this knob keep
     * the historical "bonding always succeeds" behavior. Set it (or use the [failBondWith] /
     * [failBondWithSecurityException] helpers) to drive the failure paths of consumers such as
     * `AndroidScannerViewModel.requestBonding`.
     */
    var bondOutcome: BondOutcome = BondOutcome.Success

    /** Every device passed to [bond], in call order — lets tests assert that bonding was (or was not) attempted. */
    val bondCalls = mutableListOf<BleDevice>()

    init {
        registerResetAction {
            bondOutcome = BondOutcome.Success
            bondCalls.clear()
        }
    }

    override fun refreshState() {}

    override fun isValid(bleAddress: String): Boolean = bleAddress.isNotBlank()

    override fun isBonded(address: String): Boolean = _state.value.bondedDevices.any { it.address == address }

    override suspend fun bond(device: BleDevice) {
        bondCalls += device
        val error =
            when (val outcome = bondOutcome) {
                is BondOutcome.Security -> outcome.error

                is BondOutcome.Fail -> outcome.error

                is BondOutcome.FailAfterBond -> {
                    addBondedDevice(device)
                    outcome.error
                }

                BondOutcome.Success -> {
                    addBondedDevice(device)
                    null
                }
            }
        error?.let { throw it }
    }

    private fun addBondedDevice(device: BleDevice) {
        val currentState = _state.value
        if (!currentState.bondedDevices.contains(device)) {
            _state.value = currentState.copy(bondedDevices = currentState.bondedDevices + device)
        }
    }

    fun setBluetoothEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setHasPermissions(hasPermissions: Boolean) {
        _state.value = _state.value.copy(hasPermissions = hasPermissions)
    }

    /**
     * The outcome [FakeBluetoothRepository.bond] produces. [Fail] and [Security] both simply throw their wrapped error;
     * the distinct cases exist only to document caller intent (via [failBondWith] vs [failBondWithSecurityException])
     * and leave a seam should the fake ever need to branch on permission failures.
     */
    sealed interface BondOutcome {
        /** bond() completes normally and records the device as bonded (pre-existing default behavior). */
        data object Success : BondOutcome

        /** bond() throws [error] — models a generic/flaky bonding failure (timeout, dropped broadcast, etc.). */
        data class Fail(val error: Throwable) : BondOutcome

        /** bond() records the device as bonded, then throws [error] — models a lost terminal bond signal. */
        data class FailAfterBond(val error: Throwable) : BondOutcome

        /** bond() throws [error] — models a missing-permission (BLUETOOTH_CONNECT) failure. */
        data class Security(val error: Throwable) : BondOutcome
    }
}

/** Make the next [FakeBluetoothRepository.bond] call throw a generic [error] (the flaky/interrupted-bonding path). */
fun FakeBluetoothRepository.failBondWith(error: Throwable = Exception("bond failed")) {
    bondOutcome = FakeBluetoothRepository.BondOutcome.Fail(error)
}

/** Make the next [FakeBluetoothRepository.bond] call record the device as bonded and then throw [error]. */
fun FakeBluetoothRepository.failBondAfterRecording(error: Throwable = Exception("bond failed after pairing")) {
    bondOutcome = FakeBluetoothRepository.BondOutcome.FailAfterBond(error)
}
