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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.MeshtasticBleConstants.SERVICE_UUID
import org.meshtastic.core.ble.retryBleOperation
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.model.RadioNotConnectedException
import org.meshtastic.core.repository.RadioInterfaceService
import kotlin.time.Duration.Companion.seconds

private const val SCAN_RETRY_COUNT = 3
private const val SCAN_RETRY_DELAY_MS = 1000L
private const val CONNECTION_TIMEOUT_MS = 15_000L
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
        val (isPermanent, msg) = throwable.toDisconnectReason()
        service.onDisconnect(isPermanent, errorMessage = msg)
    }

    private val connectionScope: CoroutineScope =
        CoroutineScope(serviceScope.coroutineContext + SupervisorJob() + exceptionHandler)
    private val bleConnection: BleConnection = BleConnection(centralManager, connectionScope, address)
    private val writeMutex: Mutex = Mutex()

    private var connectionStartTime: Long = 0
    private var packetsReceived: Int = 0
    private var packetsSent: Int = 0
    private var bytesReceived: Long = 0
    private var bytesSent: Long = 0

    init {
        connect()
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
                connectionStartTime = nowMillis
                Logger.i { "[$address] BLE connection attempt started" }

                bleConnection.connectionState
                    .onEach { state ->
                        if (state is ConnectionState.Disconnected) {
                            onDisconnected(state)
                        }
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] bleConnection.connectionState flow crashed!" }
                        val (isPermanent, msg) = e.toDisconnectReason()
                        service.onDisconnect(isPermanent, errorMessage = msg)
                    }
                    .launchIn(connectionScope)

                val p = retryBleOperation(tag = address) { findPeripheral() }
                val state = bleConnection.connectAndAwait(p, CONNECTION_TIMEOUT_MS)
                if (state !is ConnectionState.Connected) {
                    throw RadioNotConnectedException("Failed to connect to device at address $address")
                }

                onConnected()
                discoverServicesAndSetupCharacteristics()
            } catch (e: Exception) {
                val failureTime = nowMillis - connectionStartTime
                Logger.w(e) { "[$address] Failed to connect to peripheral after ${failureTime}ms" }
                val (isPermanent, msg) = e.toDisconnectReason()
                service.onDisconnect(isPermanent, errorMessage = msg)
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
        radioService = null

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
        val (isPermanent, msg) = when (val reason = state.reason) {
            is ConnectionState.Disconnected.Reason.InsufficientAuthentication -> Pair(true, "Insufficient authentication: please unpair and repair the device")
            is ConnectionState.Disconnected.Reason.RequiredServiceNotFound -> Pair(false, "Required characteristic missing")
            else -> Pair(false, reason.toString())
        }
        service.onDisconnect(isPermanent, errorMessage = msg)
    }

    private suspend fun discoverServicesAndSetupCharacteristics() {
        try {
            val peripheral = bleConnection.peripheral
            if (peripheral == null) {
                Logger.w { "[$address] Peripheral is null during discovery" }
                return
            }

            peripheral.profile(serviceUuid = SERVICE_UUID, required = true) { service ->
                val radioService = MeshtasticRadioServiceImpl(service, connectionScope)

                // Wire up notifications
                radioService.fromRadio
                    .onEach { packet ->
                        Logger.d { "[$address] Received packet fromRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in fromRadio flow" }
                        val (isPermanent, msg) = e.toDisconnectReason()
                        this@NordicBleInterface.service.onDisconnect(isPermanent, errorMessage = msg)
                    }
                    .launchIn(connectionScope)

                radioService.logRadio
                    .onEach { packet ->
                        Logger.d { "[$address] Received packet logRadio (${packet.size} bytes)" }
                        dispatchPacket(packet)
                    }
                    .catch { e ->
                        Logger.w(e) { "[$address] Error in logRadio flow" }
                        val (isPermanent, msg) = e.toDisconnectReason()
                        this@NordicBleInterface.service.onDisconnect(isPermanent, errorMessage = msg)
                    }
                    .launchIn(connectionScope)

                // Store reference for handleSendToRadio
                this@NordicBleInterface.radioService = radioService

                Logger.i { "[$address] Profile service active and characteristics subscribed" }
                this@NordicBleInterface.service.onConnect()

                // Keep the profile active
                kotlinx.coroutines.awaitCancellation()
            }
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Profile service discovery or operation failed" }
            bleConnection.disconnect()
            val (isPermanent, msg) = e.toDisconnectReason()
            this@NordicBleInterface.service.onDisconnect(isPermanent, errorMessage = msg)
        }
    }

    private var radioService: MeshtasticRadioProfile.State? = null

    // --- IRadioInterface Implementation ---

    /**
     * Sends a packet to the radio with retry support.
     *
     * @param p The packet to send.
     */
    override fun handleSendToRadio(p: ByteArray) {
        val radioService = radioService
        if (radioService != null) {
            connectionScope.launch {
                writeMutex.withLock {
                    try {
                        retryBleOperation(tag = address) { radioService.sendToRadio(p) }
                        packetsSent++
                        bytesSent += p.size
                        Logger.d {
                            "[$address] Successfully wrote packet #$packetsSent " +
                                "to toRadioCharacteristic - " +
                                "${p.size} bytes (Total TX: $bytesSent bytes)"
                        }
                    } catch (e: Exception) {
                        Logger.w(e) {
                            "[$address] Failed to write packet to toRadioCharacteristic after " +
                                "$packetsSent successful writes"
                        }
                        val (isPermanent, msg) = e.toDisconnectReason()
                        service.onDisconnect(isPermanent, errorMessage = msg)
                    }
                }
            }
        } else {
            Logger.w { "[$address] toRadio characteristic unavailable, can't send data" }
        }
    }

    override fun keepAlive() {
        Logger.d { "[$address] BLE keepAlive" }
    }

    /** Closes the connection to the device. */
    override fun close() {
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
        serviceScope.launch {
            connectionScope.cancel()
            bleConnection.disconnect()
            service.onDisconnect(true)
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
            service.handleFromRadio(packet)
        } catch (t: Throwable) {
            Logger.e(t) { "[$address] Failed to execute service.handleFromRadio()" }
        }
    }

    private fun Throwable.toDisconnectReason(): Pair<Boolean, String> {
        val isPermanent =
            this is no.nordicsemi.kotlin.ble.core.exception.BluetoothUnavailableException ||
                this is no.nordicsemi.kotlin.ble.core.exception.ManagerClosedException
        val msg = when (this) {
            is NoSuchElementException, is IllegalArgumentException -> "Required characteristic missing"
            else -> this.message ?: this.javaClass.simpleName
        }
        return Pair(isPermanent, msg)
    }
}
