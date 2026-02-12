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
package com.geeksville.mesh.repository.radio

import android.annotation.SuppressLint
import co.touchlab.kermit.Logger
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
import no.nordicsemi.kotlin.ble.client.exception.InvalidAttributeException
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import org.meshtastic.core.model.util.nowMillis
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
        Logger.w(throwable) { "[$address] Uncaught exception in connectionScope" }
        serviceScope.launch {
            try {
                peripheral?.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in exception handler" }
            }
        }
        service.onDisconnect(BleError.from(throwable))
    }

    private val connectionScope = CoroutineScope(serviceScope.coroutineContext + SupervisorJob() + exceptionHandler)
    private val drainMutex = Mutex()
    private val writeMutex = Mutex()

    private var peripheral: Peripheral? = null
    private var connectionStartTime: Long = 0
    private var packetsReceived: Int = 0
    private var packetsSent: Int = 0
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0

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
            val packet =
                try {
                    fromRadioCharacteristic?.read()?.takeIf { it.isNotEmpty() }
                } catch (e: InvalidAttributeException) {
                    Logger.w(e) { "[$address] Attribute invalidated during read, clearing characteristics" }
                    handleInvalidAttribute(e)
                    null
                } catch (e: Exception) {
                    Logger.w(e) { "[$address] Error reading fromRadioCharacteristic (likely disconnected)" }
                    null
                }

            if (packet == null) {
                Logger.d { "[$address] fromRadio queue drain complete or error reading characteristic" }
                break
            }
            send(packet)
        }
    }

    private fun dispatchPacket(packet: ByteArray) {
        packetsReceived++
        bytesReceived += packet.size
        Logger.d {
            "[$address] Dispatching packet to service.handleFromRadio() - " +
                "Packet #$packetsReceived, ${packet.size} bytes (Total: $bytesReceived bytes)"
        }
        try {
            service.handleFromRadio(p = packet)
        } catch (t: Throwable) {
            Logger.e(t) { "[$address] Failed to execute service.handleFromRadio()" }
        }
    }

    private suspend fun drainPacketQueueAndDispatch() {
        drainMutex.withLock {
            var drainedCount = 0
            fromRadioPacketFlow()
                .onEach { packet ->
                    drainedCount++
                    Logger.d { "[$address] Read packet from queue (${packet.size} bytes)" }
                    dispatchPacket(packet)
                }
                .catch { ex -> Logger.w(ex) { "[$address] Exception while draining packet queue" } }
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
                connectionStartTime = nowMillis
                Logger.i { "[$address] BLE connection attempt started at $connectionStartTime" }

                peripheral = retryCall { findAndConnectPeripheral() }
                peripheral?.let {
                    val connectionTime = nowMillis - connectionStartTime
                    Logger.i { "[$address] BLE peripheral connected in ${connectionTime}ms" }
                    onConnected()
                    observePeripheralChanges()
                    discoverServicesAndSetupCharacteristics(it)
                }
            } catch (e: Exception) {
                val failureTime = nowMillis - connectionStartTime
                // BLE connection errors are common and often transient
                Logger.w(e) { "[$address] Failed to connect to peripheral after ${failureTime}ms" }
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
                Logger.d { "[$address] Connection established. RSSI: $rssi dBm" }

                val phyInUse = retryCall { p.readPhy() }
                Logger.d { "[$address] PHY in use: $phyInUse" }
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Failed to read initial connection properties" }
        }
    }

    private fun observePeripheralChanges() {
        peripheral?.let { p ->
            p.phy.onEach { phy -> Logger.i { "[$address] BLE PHY changed to $phy" } }.launchIn(connectionScope)

            p.connectionParameters
                .onEach { params -> Logger.i { "[$address] BLE connection parameters changed to $params" } }
                .launchIn(connectionScope)

            p.state
                .onEach { state ->
                    Logger.i { "[$address] BLE connection state changed to $state" }
                    if (state is ConnectionState.Disconnected) {
                        clearCharacteristics()

                        val uptime =
                            if (connectionStartTime > 0) {
                                nowMillis - connectionStartTime
                            } else {
                                0
                            }
                        Logger.w {
                            "[$address] BLE disconnected - Reason: ${state.reason}, " +
                                "Uptime: ${uptime}ms, " +
                                "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                                "Packets TX: $packetsSent ($bytesSent bytes)"
                        }
                        service.onDisconnect(BleError.Disconnected(reason = state.reason))
                    }
                }
                .launchIn(connectionScope)
        }
        centralManager.state
            .onEach { state -> Logger.i { "[$address] CentralManager state changed to $state" } }
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
                            Logger.d {
                                "[$address] Found toRadio: ${toRadioCharacteristic?.uuid}, ${toRadioCharacteristic?.instanceId}"
                            }
                            Logger.d {
                                "[$address] Found fromNum: ${fromNumCharacteristic?.uuid}, ${fromNumCharacteristic?.instanceId}"
                            }
                            Logger.d {
                                "[$address] Found fromRadio: ${fromRadioCharacteristic?.uuid}, ${fromRadioCharacteristic?.instanceId}"
                            }
                            Logger.d {
                                "[$address] Found logRadio: ${logRadioCharacteristic?.uuid}, ${logRadioCharacteristic?.instanceId}"
                            }
                            setupNotifications()
                            service.onConnect()
                        } else {
                            Logger.w { "[$address] Discovery failed: missing required characteristics" }
                            service.onDisconnect(BleError.DiscoveryFailed("One or more characteristics not found"))
                        }
                    } else {
                        Logger.w { "[$address] Discovery failed: Meshtastic service not found" }
                        service.onDisconnect(BleError.DiscoveryFailed("Meshtastic service not found"))
                    }
                }
                .catch { e ->
                    Logger.w(e) { "[$address] Service discovery failed" }
                    try {
                        peripheral.disconnect()
                    } catch (e2: Exception) {
                        Logger.w(e2) { "[$address] Failed to disconnect in discovery catch" }
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
            ?.onStart { Logger.d { "[$address] Subscribing to fromNumCharacteristic" } }
            ?.onEach { notifyBytes ->
                Logger.d { "[$address] FromNum Notification (${notifyBytes.size} bytes), draining queue" }
                connectionScope.launch { drainPacketQueueAndDispatch() }
            }
            ?.catch { e ->
                Logger.w(e) { "[$address] Error subscribing to fromNumCharacteristic" }
                service.onDisconnect(BleError.from(e))
            }
            ?.launchIn(scope = connectionScope)

        retryCall { logRadioCharacteristic?.subscribe() }
            ?.onStart { Logger.d { "[$address] Subscribing to logRadioCharacteristic" } }
            ?.onEach { notifyBytes ->
                Logger.d { "[$address] LogRadio Notification (${notifyBytes.size} bytes), dispatching packet" }
                dispatchPacket(notifyBytes)
            }
            ?.catch { e ->
                Logger.w(e) { "[$address] Error subscribing to logRadioCharacteristic" }
                service.onDisconnect(BleError.from(e))
            }
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
                if (currentAttempt >= RETRY_COUNT) {
                    Logger.w(e) { "[$address] BLE operation failed after $RETRY_COUNT attempts, giving up" }
                    throw e
                }
                Logger.w(e) {
                    "[$address] BLE operation failed (attempt $currentAttempt/$RETRY_COUNT), " +
                        "retrying in ${RETRY_DELAY_MS}ms..."
                }
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
            if (peripheral == null) {
                Logger.w { "[$address] BLE peripheral is null, cannot send packet" }
                return@let
            }
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
                            packetsSent++
                            bytesSent += p.size
                            Logger.d {
                                "[$address] Writing packet #$packetsSent to toRadioCharacteristic with $writeType - " +
                                    "${p.size} bytes (Total TX: $bytesSent bytes)"
                            }
                            characteristic.write(p, writeType = writeType)
                        }
                        drainPacketQueueAndDispatch()
                    } catch (e: InvalidAttributeException) {
                        Logger.w(e) { "[$address] Attribute invalidated during write, clearing characteristics" }
                        handleInvalidAttribute(e)
                    } catch (e: Exception) {
                        Logger.w(e) {
                            "[$address] Failed to write packet to toRadioCharacteristic after " +
                                "$packetsSent successful writes"
                        }
                        service.onDisconnect(BleError.from(e))
                    }
                }
            }
        } ?: Logger.w { "[$address] toRadio characteristic unavailable, can't send data" }
    }

    override fun keepAlive() {
        Logger.d { "[$address] BLE keepAlive" }
    }

    /** Closes the connection to the device. */
    override fun close() {
        runBlocking {
            val uptime =
                if (connectionStartTime > 0) {
                    nowMillis - connectionStartTime
                } else {
                    0
                }
            Logger.i {
                "[$address] BLE close() called - " +
                    "Uptime: ${uptime}ms, " +
                    "Packets RX: $packetsReceived ($bytesReceived bytes), " +
                    "Packets TX: $packetsSent ($bytesSent bytes)"
            }
            connectionScope.cancel()
            peripheral?.disconnect()
            service.onDisconnect(true)
        }
    }

    private fun handleInvalidAttribute(e: InvalidAttributeException) {
        clearCharacteristics()
        service.onDisconnect(BleError.from(e))
    }

    private fun clearCharacteristics() {
        toRadioCharacteristic = null
        fromNumCharacteristic = null
        fromRadioCharacteristic = null
        logRadioCharacteristic = null
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
