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
import com.geeksville.mesh.service.RadioNotConnectedException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.exception.InvalidAttributeException
import no.nordicsemi.kotlin.ble.core.CharacteristicProperty
import no.nordicsemi.kotlin.ble.core.ConnectionState
import no.nordicsemi.kotlin.ble.core.WriteType
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleError
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMNUM_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.FROMRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.LOGRADIO_CHARACTERISTIC
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.MeshtasticBleConstants.TORADIO_CHARACTERISTIC
import org.meshtastic.core.ble.retryBleOperation
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private const val SCAN_RETRY_DELAY_MS = 1000L
private val SCAN_TIMEOUT = 5.seconds

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
                bleConnection.disconnect()
            } catch (e: Exception) {
                Logger.w(e) { "[$address] Failed to disconnect in exception handler" }
            }
        }
        service.onDisconnect(BleError.from(throwable))
    }

    private val connectionScope: CoroutineScope =
        CoroutineScope(serviceScope.coroutineContext + SupervisorJob() + exceptionHandler)
    private val bleConnection: BleConnection = BleConnection(centralManager, connectionScope, address)
    private val drainMutex: Mutex = Mutex()
    private val writeMutex: Mutex = Mutex()

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
            fromRadioPacketFlow()
                .onEach { packet ->
                    Logger.d { "[$address] Read packet from queue (${packet.size} bytes)" }
                    dispatchPacket(packet)
                }
                .catch { ex -> Logger.w(ex) { "[$address] Exception while draining packet queue" } }
                .collect()
        }
    }

    // --- Connection & Discovery Logic ---

    /** Robustly finds the peripheral. First checks bonded devices, then performs a short scan if not found. */
    private suspend fun findPeripheral(): Peripheral {
        centralManager
            .getBondedPeripherals()
            .firstOrNull { it.address == address }
            ?.let {
                return it
            }

        Logger.i { "[$address] Device not found in bonded list, scanning..." }
        val scanner = BleScanner(centralManager)

        repeat(SCAN_RETRY_COUNT) { attempt ->
            val p = scanner.scan(SCAN_TIMEOUT).firstOrNull { it.address == address }
            if (p != null) return p

            if (attempt < SCAN_RETRY_COUNT - 1) {
                delay(SCAN_RETRY_DELAY_MS)
            }
        }

        throw RadioNotConnectedException("Device not found at address $address")
    }

    private fun connect() {
        connectionScope.launch {
            try {
                connectionStartTime = System.currentTimeMillis()
                Logger.i { "[$address] BLE connection attempt started" }

                bleConnection.connectionState
                    .onEach { state ->
                        if (state is ConnectionState.Disconnected) {
                            onDisconnected(state)
                        }
                    }
                    .launchIn(connectionScope)

                val p = retryBleOperation(tag = address) { findPeripheral() }
                bleConnection.connect(p)

                onConnected()
                discoverServicesAndSetupCharacteristics()
            } catch (e: Exception) {
                val failureTime = System.currentTimeMillis() - connectionStartTime
                Logger.w(e) { "[$address] Failed to connect to peripheral after ${failureTime}ms" }
                service.onDisconnect(BleError.from(e))
            }
        }
    }

    private suspend fun onConnected() {
        try {
            bleConnection.peripheral?.let { p ->
                val rssi = retryBleOperation(tag = address) { p.readRssi() }
                Logger.d { "[$address] Connection confirmed. Initial RSSI: $rssi dBm" }
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Failed to read initial connection RSSI" }
        }
    }

    private fun onDisconnected(state: ConnectionState.Disconnected) {
        clearCharacteristics()

        val uptime =
            if (connectionStartTime > 0) {
                System.currentTimeMillis() - connectionStartTime
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

    private suspend fun discoverServicesAndSetupCharacteristics() {
        try {
            val chars =
                bleConnection.discoverCharacteristics(
                    SERVICE_UUID,
                    listOf(
                        TORADIO_CHARACTERISTIC,
                        FROMNUM_CHARACTERISTIC,
                        FROMRADIO_CHARACTERISTIC,
                        LOGRADIO_CHARACTERISTIC,
                    ),
                )

            if (chars != null) {
                toRadioCharacteristic = chars[TORADIO_CHARACTERISTIC]
                fromNumCharacteristic = chars[FROMNUM_CHARACTERISTIC]
                fromRadioCharacteristic = chars[FROMRADIO_CHARACTERISTIC]
                logRadioCharacteristic = chars[LOGRADIO_CHARACTERISTIC]

                Logger.d { "[$address] Characteristics discovered successfully" }
                setupNotifications()
                service.onConnect()
            } else {
                Logger.w { "[$address] Discovery failed: missing required characteristics" }
                service.onDisconnect(BleError.DiscoveryFailed("One or more characteristics not found"))
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Service discovery failed" }
            bleConnection.disconnect()
            service.onDisconnect(BleError.from(e))
        }
    }

    // --- Notification Setup ---

    private fun setupNotifications() {
        fromNumCharacteristic
            ?.subscribe()
            ?.onEach { notifyBytes ->
                Logger.d { "[$address] FromNum Notification (${notifyBytes.size} bytes), draining queue" }
                connectionScope.launch { drainPacketQueueAndDispatch() }
            }
            ?.catch { e ->
                Logger.w(e) { "[$address] Error in fromNumCharacteristic subscription" }
                service.onDisconnect(BleError.from(e))
            }
            ?.launchIn(scope = connectionScope)

        logRadioCharacteristic
            ?.subscribe()
            ?.onEach { notifyBytes ->
                Logger.d { "[$address] LogRadio Notification (${notifyBytes.size} bytes), dispatching packet" }
                dispatchPacket(notifyBytes)
            }
            ?.catch { e ->
                Logger.w(e) { "[$address] Error in logRadioCharacteristic subscription" }
                service.onDisconnect(BleError.from(e))
            }
            ?.launchIn(scope = connectionScope)
    }

    // --- IRadioInterface Implementation ---

    /**
     * Sends a packet to the radio with retry support.
     *
     * @param p The packet to send.
     */
    override fun handleSendToRadio(p: ByteArray) {
        toRadioCharacteristic?.let { characteristic ->
            connectionScope.launch {
                writeMutex.withLock {
                    try {
                        val writeType =
                            if (characteristic.properties.contains(CharacteristicProperty.WRITE_WITHOUT_RESPONSE)) {
                                WriteType.WITHOUT_RESPONSE
                            } else {
                                WriteType.WITH_RESPONSE
                            }

                        retryBleOperation(tag = address) { characteristic.write(p, writeType = writeType) }

                        packetsSent++
                        bytesSent += p.size
                        Logger.d {
                            "[$address] Successfully wrote packet #$packetsSent " +
                                "to toRadioCharacteristic with $writeType - " +
                                "${p.size} bytes (Total TX: $bytesSent bytes)"
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
                    System.currentTimeMillis() - connectionStartTime
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
            bleConnection.disconnect()
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
}
