/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.radio

import android.annotation.SuppressLint
import com.geeksville.mesh.repository.radio.BleConstants.BTM_FROMNUM_CHARACTER
import com.geeksville.mesh.repository.radio.BleConstants.BTM_FROMRADIO_CHARACTER
import com.geeksville.mesh.repository.radio.BleConstants.BTM_LOGRADIO_CHARACTER
import com.geeksville.mesh.repository.radio.BleConstants.BTM_SERVICE_UUID
import com.geeksville.mesh.repository.radio.BleConstants.BTM_TORADIO_CHARACTER
import com.geeksville.mesh.service.RadioNotConnectedException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import timber.log.Timber
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/**
 * A [IRadioInterface] implementation for BLE devices using Nordic Kotlin BLE Library.
 * https://github.com/NordicSemiconductor/Kotlin-BLE-Library.
 *
 * This class is responsible for connecting to and communicating with a Meshtastic device over BLE.
 *
 * @param serviceScope The coroutine scope to use for launching coroutines.
 * @param centralManager The central manager provided by Nordic BLE Library.
 * @param service The [RadioInterfaceService] to use for handling radio events.
 * @param address The BLE address of the device to connect to.
 */
@SuppressLint("MissingPermission")
class NordicBleInterface
@AssistedInject
constructor(
    private val serviceScope: CoroutineScope,
    private val centralManager: CentralManager,
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface {

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "[$address] Uncaught exception in connectionScope")
        serviceScope.launch {
            try {
                peripheral?.disconnect()
            } catch (e: Exception) {
                Timber.e(e, "[$address] Failed to disconnect in exception handler")
            }
        }
        service.onDisconnect(BleError.from(throwable))
    }

    private val connectionScope = CoroutineScope(serviceScope.coroutineContext + SupervisorJob() + exceptionHandler)
    private val drainMutex = Mutex()
    private val writeMutex = Mutex()

    private var peripheral: Peripheral? = null

    private var toRadioCharacteristic: RemoteCharacteristic? = null
    private var fromNumCharacteristic: RemoteCharacteristic? = null
    private var fromRadioCharacteristic: RemoteCharacteristic? = null
    private var logRadioCharacteristic: RemoteCharacteristic? = null

    init {
        connect()
    }

    // --- Packet Flow Management ---

    private fun fromRadioPacketFlow(): Flow<ByteArray> = channelFlow {
        while (isActive) {
            // Use safe call and Elvis operator for cleaner loop termination if read fails or returns empty
            val packet =
                fromRadioCharacteristic?.read()?.takeIf { it.isNotEmpty() }
                    ?: run {
                        Timber.d("[$address] fromRadio queue drain complete (read empty/null)")
                        break
                    }
            send(packet)
        }
    }

    private fun dispatchPacket(packet: ByteArray) {
        Timber.d("[$address] Dispatching packet to service.handleFromRadio()")
        try {
            service.handleFromRadio(p = packet)
        } catch (t: Throwable) {
            Timber.e(t, "[$address] Failed to execute service.handleFromRadio()")
        }
    }

    private suspend fun drainPacketQueueAndDispatch() {
        drainMutex.withLock {
            var drainedCount = 0
            fromRadioPacketFlow()
                .onEach { packet ->
                    drainedCount++
                    Timber.d("[$address] Read packet from queue (${packet.size} bytes)")
                    dispatchPacket(packet)
                }
                .catch { ex -> Timber.w(ex, "[$address] Exception while draining packet queue") }
                .onCompletion {
                    if (drainedCount > 0) {
                        Timber.d("[$address] Drained $drainedCount packets from packet queue")
                    }
                }
                .collect()
        }
    }

    // --- Connection & Discovery Logic ---

    private fun findPeripheral(): Peripheral =
        centralManager.getBondedPeripherals().firstOrNull { it.address == address }
            ?: throw RadioNotConnectedException("Device not found at address $address")

    private fun connect() {
        connectionScope.launch {
            try {
                peripheral = retryCall { findAndConnectPeripheral() }
                peripheral?.let {
                    onConnected()
                    observePeripheralChanges()
                    discoverServicesAndSetupCharacteristics(it)
                }
            } catch (e: Exception) {
                Timber.e(e, "[$address] Failed to connect to peripheral")
                service.onDisconnect(BleError.from(e))
            }
        }
    }

    private suspend fun findAndConnectPeripheral(): Peripheral {
        val p = findPeripheral()
        centralManager.connect(
            peripheral = p,
            options = CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
        )
        p.requestConnectionPriority(ConnectionPriority.HIGH)
        return p
    }

    private suspend fun onConnected() {
        try {
            peripheral?.let { p ->
                val rssi = retryCall { p.readRssi() }
                Timber.d("[$address] Connection established. RSSI: $rssi dBm")

                val phyInUse = retryCall { p.readPhy() }
                Timber.d("[$address] PHY in use: $phyInUse")
            }
        } catch (e: Exception) {
            Timber.w(e, "[$address] Failed to read initial connection properties")
        }
    }

    private fun observePeripheralChanges() {
        peripheral?.let { p ->
            p.phy.onEach { phy -> Timber.d("[$address] PHY changed to $phy") }.launchIn(connectionScope)
            p.connectionParameters
                .onEach { Timber.d("[$address] Connection parameters changed to $it") }
                .launchIn(connectionScope)
            p.state
                .onEach { state ->
                    Timber.d("[$address] State changed to $state")
                    if (state is ConnectionState.Disconnected) {
                        service.onDisconnect(BleError.Disconnected(reason = state.reason))
                    }
                }
                .launchIn(connectionScope)
        }
        centralManager.state
            .onEach { state -> Timber.d("CentralManager state changed to $state") }
            .launchIn(connectionScope)
    }

    @Suppress("TooGenericExceptionCaught")
    @OptIn(ExperimentalUuidApi::class)
    private fun discoverServicesAndSetupCharacteristics(peripheral: Peripheral) {
        connectionScope.launch {
            peripheral
                .services(listOf(BTM_SERVICE_UUID.toKotlinUuid()))
                .onEach { services ->
                    val meshtasticService = services?.find { it.uuid == BTM_SERVICE_UUID.toKotlinUuid() }

                    if (meshtasticService != null) {
                        toRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_TORADIO_CHARACTER.toKotlinUuid() }
                        fromNumCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_FROMNUM_CHARACTER.toKotlinUuid() }
                        fromRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_FROMRADIO_CHARACTER.toKotlinUuid() }
                        logRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_LOGRADIO_CHARACTER.toKotlinUuid() }

                        if (
                            listOf(toRadioCharacteristic, fromNumCharacteristic, fromRadioCharacteristic).all {
                                it != null
                            }
                        ) {
                            Timber.d(
                                "[$address] Found toRadio: ${toRadioCharacteristic?.uuid}, ${toRadioCharacteristic?.instanceId}",
                            )
                            Timber.d(
                                "[$address] Found fromNum: ${fromNumCharacteristic?.uuid}, ${fromNumCharacteristic?.instanceId}",
                            )
                            Timber.d(
                                "[$address] Found fromRadio: ${fromRadioCharacteristic?.uuid}, ${fromRadioCharacteristic?.instanceId}",
                            )
                            Timber.d(
                                "[$address] Found logRadio: ${logRadioCharacteristic?.uuid}, ${logRadioCharacteristic?.instanceId}",
                            )
                            setupNotifications()
                            service.onConnect()
                        } else {
                            Timber.w("[$address] Discovery failed: missing required characteristics")
                            service.onDisconnect(BleError.DiscoveryFailed("One or more characteristics not found"))
                        }
                    } else {
                        Timber.w("[$address] Discovery failed: Meshtastic service not found")
                        service.onDisconnect(BleError.DiscoveryFailed("Meshtastic service not found"))
                    }
                }
                .catch { e ->
                    Timber.e(e, "[$address] Service discovery failed")
                    try {
                        peripheral.disconnect()
                    } catch (e2: Exception) {
                        Timber.e(e2, "[$address] Failed to disconnect in discovery catch")
                    }
                    service.onDisconnect(BleError.from(e))
                }
                .launchIn(connectionScope)
        }
    }

    // --- Notification Setup ---

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun setupNotifications() {
        retryCall { fromNumCharacteristic?.subscribe() }
            ?.onStart { Timber.d("[$address] Subscribing to fromNumCharacteristic") }
            ?.onEach { notifyBytes ->
                Timber.d("[$address] FromNum Notification (${notifyBytes.size} bytes), draining queue")
                connectionScope.launch { drainPacketQueueAndDispatch() }
            }
            ?.catch { e ->
                Timber.e(e, "[$address] Error subscribing to fromNumCharacteristic")
                service.onDisconnect(BleError.from(e))
            }
            ?.onCompletion { cause -> Timber.d("[$address] fromNum sub flow completed, cause=$cause") }
            ?.launchIn(scope = connectionScope)

        retryCall { logRadioCharacteristic?.subscribe() }
            ?.onStart { Timber.d("[$address] Subscribing to logRadioCharacteristic") }
            ?.onEach { notifyBytes ->
                Timber.d("[$address] LogRadio Notification (${notifyBytes.size} bytes), dispatching packet")
                dispatchPacket(notifyBytes)
            }
            ?.catch { e ->
                Timber.e(e, "[$address] Error subscribing to logRadioCharacteristic")
                service.onDisconnect(BleError.from(e))
            }
            ?.onCompletion { cause -> Timber.d("[$address] logRadio sub flow completed, cause=$cause") }
            ?.launchIn(scope = connectionScope)
    }

    private suspend fun <T> retryCall(block: suspend () -> T): T {
        var currentAttempt = 0
        while (true) {
            try {
                return block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                currentAttempt++
                if (currentAttempt >= RETRY_COUNT) throw e
                Timber.w(e, "[$address] Operation failed, retrying ($currentAttempt/$RETRY_COUNT)...")
                delay(RETRY_DELAY_MS)
            }
        }
    }

    // --- IRadioInterface Implementation ---

    /**
     * Sends a packet to the radio.
     *
     * @param p The packet to send.
     */
    override fun handleSendToRadio(p: ByteArray) {
        toRadioCharacteristic?.let { characteristic ->
            if (peripheral == null) return@let
            connectionScope.launch {
                writeMutex.withLock {
                    try {
                        val writeType =
                            if (characteristic.properties.contains(CharacteristicProperty.WRITE_WITHOUT_RESPONSE)) {
                                WriteType.WITHOUT_RESPONSE
                            } else {
                                WriteType.WITH_RESPONSE
                            }
                        retryCall {
                            Timber.d("[$address] Writing packet to toRadioCharacteristic with $writeType")
                            characteristic.write(p, writeType = writeType)
                        }
                        drainPacketQueueAndDispatch()
                    } catch (e: Exception) {
                        Timber.e(e, "[$address] Failed to write packet to toRadioCharacteristic")
                        service.onDisconnect(BleError.from(e))
                    }
                }
            }
        } ?: Timber.w("[$address] toRadio unavailable, can't send data")
    }

    /** Closes the connection to the device. */
    override fun close() {
        runBlocking {
            connectionScope.cancel()
            peripheral?.disconnect()
            service.onDisconnect(true)
        }
    }

    companion object {
        private const val RETRY_COUNT = 3
        private const val RETRY_DELAY_MS = 500L
    }
}

object BleConstants {
    const val BLE_NAME_PATTERN = "^.*_([0-9a-fA-F]{4})$"
    val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    val BTM_TORADIO_CHARACTER: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
    val BTM_FROMNUM_CHARACTER: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    val BTM_FROMRADIO_CHARACTER: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
    val BTM_LOGRADIO_CHARACTER: UUID = UUID.fromString("5a3d6e49-06e6-4423-9944-e9de8cdf9547")
}
