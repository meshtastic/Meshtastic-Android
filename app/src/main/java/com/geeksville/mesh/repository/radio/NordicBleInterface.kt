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
import android.app.Application
import com.geeksville.mesh.service.RadioNotConnectedException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.native
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
 * @param context The application context.
 * @param service The [RadioInterfaceService] to use for handling radio events.
 * @param address The BLE address of the device to connect to.
 */
@SuppressLint("MissingPermission")
class NordicBleInterface
@AssistedInject
constructor(
    private val context: Application,
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface {

    private var peripheral: Peripheral? = null
    private val localScope: CoroutineScope
        get() = service.serviceScope

    private lateinit var centralManager: CentralManager

    private var toRadioCharacteristic: RemoteCharacteristic? = null
    private var fromNumCharacteristic: RemoteCharacteristic? = null
    private var fromRadioCharacteristic: RemoteCharacteristic? = null

    private fun packetQueueFlow(): Flow<ByteArray> = channelFlow {
        while (isActive) {
            val packet = fromRadioCharacteristic?.read()
            if (packet == null || packet.isEmpty()) {
                break
            }
            send(packet)
            delay(INTER_READ_DELAY_MS)
        }
    }

    private fun drainPacketQueueAndDispatch(source: String) {
        var drainedCount = 0
        packetQueueFlow()
            .onEach { packet ->
                drainedCount++
                Timber.d(
                    "[$source] Read packet queue returned ${packet.size} bytes: ${
                        packet.joinToString(
                            prefix = "[",
                            postfix = "]",
                        ) { b ->
                            String.format("0x%02x", b)
                        }
                    } - dispatching to service.handleFromRadio()",
                )
                dispatchPacket(packet, source)
            }
            .catch { ex -> Timber.w(ex, "Exception while draining packet queue (source=$source)") }
            .onCompletion {
                if (drainedCount > 0) {
                    Timber.d("[$source] Drained $drainedCount packets from packet queue")
                }
            }
            .launchIn(localScope)
    }

    private fun dispatchPacket(packet: ByteArray, source: String) {
        try {
            if (service.serviceScope.coroutineContext[Job]?.isActive == true) {
                service.serviceScope.launch { service.handleFromRadio(p = packet) }
            } else {
                Timber.w(
                    "service.serviceScope not active while dispatching from packet queue (source=$source); using localScope as fallback",
                )
                localScope.launch { service.handleFromRadio(p = packet) }
            }
        } catch (t: Throwable) {
            Timber.e(t, "Failed to schedule service.handleFromRadio (source=$source)")
        }
    }

    companion object {
        val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val BTM_TORADIO_CHARACTER: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val BTM_FROMNUM_CHARACTER: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
        val BTM_FROMRADIO_CHARACTER: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")

        private const val INTER_READ_DELAY_MS: Long = 5L
        private const val POST_WRITE_DELAY_MS: Long = 25L
    }

    init {
        connect()
    }

    private suspend fun findPeripheral(): Peripheral =
        centralManager.scan().mapNotNull { it.peripheral }.firstOrNull { it.address == address }
            ?: throw RadioNotConnectedException("Device not found")

    private fun connect() {
        localScope.launch {
            try {
                centralManager = CentralManager.native(context, localScope)
                peripheral = findAndConnectPeripheral()
                peripheral?.let {
                    observePeripheralChanges()
                    discoverServicesAndSetupCharacteristics(it)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during connection setup")
                service.onDisconnect(false)
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

    private fun observePeripheralChanges() {
        peripheral?.let { p ->
            p.phy.onEach { phy -> Timber.d("PHY changed to $phy") }.launchIn(localScope)
            p.connectionParameters.onEach { Timber.d("Connection parameters changed to $it") }.launchIn(localScope)
            p.state
                .onEach { state ->
                    Timber.d("Peripheral state changed to $state")
                    if (!state.isConnected) {
                        toRadioCharacteristic = null
                        service.onDisconnect(false)
                    }
                }
                .launchIn(localScope)
        }
        centralManager.state.onEach { state -> Timber.d("CentralManager state changed to $state") }.launchIn(localScope)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun discoverServicesAndSetupCharacteristics(peripheral: Peripheral) {
        localScope.launch {
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

                        if (
                            toRadioCharacteristic == null ||
                            fromNumCharacteristic == null ||
                            fromRadioCharacteristic == null
                        ) {
                            Timber.e("Critical: Meshtastic characteristics not found! Cannot connect.")
                            service.onDisconnect(false)
                        } else {
                            logCharacteristicInfo()
                            setupNotifications()
                        }
                    }
                }
                .launchIn(localScope)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun logCharacteristicInfo() {
        try {
            Timber.d(
                "toRadioCharacteristic discovered: uuid=${toRadioCharacteristic?.uuid} instanceId=${toRadioCharacteristic?.instanceId}",
            )
        } catch (_: Throwable) {
            Timber.d("toRadioCharacteristic discovered (minimal info)")
        }
        try {
            Timber.d(
                "fromNumCharacteristic discovered: uuid=${fromNumCharacteristic?.uuid} instanceId=${fromNumCharacteristic?.instanceId}",
            )
            Timber.d(
                "fromRadioCharacteristic discovered (packet queue): uuid=${fromRadioCharacteristic?.uuid} instanceId=${fromRadioCharacteristic?.instanceId}",
            )
        } catch (_: Throwable) {
            Timber.d("fromRadioCharacteristic discovered (minimal info)")
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun setupNotifications() {
        localScope.launch {
            fromNumCharacteristic
                ?.subscribe()
                ?.onEach { notifyBytes ->
                    try {
                        Timber.d(
                            "FROMNUM notify, ${notifyBytes.size} bytes: ${
                                notifyBytes.joinToString(
                                    prefix = "[",
                                    postfix = "]",
                                ) { b -> String.format("0x%02x", b) }
                            } - reading packet queue",
                        )
                        drainPacketQueueAndDispatch("notify")
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error handling incoming FROMNUM notify")
                    }
                }
                ?.catch { e -> Timber.e(e, "Error in subscribe flow for fromNumCharacteristic") }
                ?.onCompletion { cause -> Timber.d("fromNum subscribe flow completed, cause=$cause") }
                ?.launchIn(scope = localScope)
        }

        localScope.launch {
            try {
                fromNumCharacteristic?.setNotifying(true)
                drainPacketQueueAndDispatch("initial")
            } catch (e: Exception) {
                Timber.e(e, "Failed to enable notifications or perform initial drain")
                service.onDisconnect(false)
            }
        }
        service.onConnect()
    }

    /**
     * Sends a packet to the radio.
     *
     * @param p The packet to send.
     */
    override fun handleSendToRadio(p: ByteArray) {
        val characteristic = toRadioCharacteristic
        if (peripheral == null || characteristic == null) {
            return
        }

        localScope.launch {
            try {
                characteristic.write(p, writeType = WriteType.WITHOUT_RESPONSE)
                localScope.launch {
                    delay(POST_WRITE_DELAY_MS)
                    drainPacketQueueAndDispatch("post-write")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing to characteristic")
            }
        }
    }

    /** Closes the connection to the device. */
    override fun close() {
        val fn = fromNumCharacteristic
        localScope.launch {
            try {
                fn?.setNotifying(false)
            } catch (ex: Exception) {
                Timber.w(ex, "Error disabling notifications on close")
            }
            try {
                peripheral?.disconnect()
            } catch (ex: Exception) {
                Timber.w(ex, "Error while closing NordicBleInterface")
            }
        }
        toRadioCharacteristic = null
        fromNumCharacteristic = null
        fromRadioCharacteristic = null
    }
}
