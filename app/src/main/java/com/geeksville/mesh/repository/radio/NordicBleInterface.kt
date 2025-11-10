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
import com.geeksville.mesh.repository.radio.BleUuidConstants.BTM_FROMNUM_CHARACTER
import com.geeksville.mesh.repository.radio.BleUuidConstants.BTM_FROMRADIO_CHARACTER
import com.geeksville.mesh.repository.radio.BleUuidConstants.BTM_SERVICE_UUID
import com.geeksville.mesh.repository.radio.BleUuidConstants.BTM_TORADIO_CHARACTER
import com.geeksville.mesh.service.RadioNotConnectedException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
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
import no.nordicsemi.kotlin.ble.core.WriteType
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

/**
 * A [IRadioInterface] implementation for BLE devices using Nordic Kotlin BLE Library.
 * https://github.com/NordicSemiconductor/Kotlin-BLE-Library.
 *
 * This class is responsible for connecting to and communicating with a Meshtastic device over BLE.
 *
 * @param localScope The coroutine scope to use for launching coroutines.
 * @param centralManager The central manager provided by Nordic BLE Library.
 * @param service The [RadioInterfaceService] to use for handling radio events.
 * @param address The BLE address of the device to connect to.
 */
@SuppressLint("MissingPermission")
class NordicBleInterface
@AssistedInject
constructor(
    private val localScope: CoroutineScope,
    private val centralManager: CentralManager,
    private val service: RadioInterfaceService,
    @Assisted val address: String,
) : IRadioInterface {

    private var peripheral: Peripheral? = null

    private var toRadioCharacteristic: RemoteCharacteristic? = null
    private var fromNumCharacteristic: RemoteCharacteristic? = null
    private var fromRadioCharacteristic: RemoteCharacteristic? = null

    init {
        connect()
    }

    // --- Packet Flow Management ---

    private fun packetQueueFlow(): Flow<ByteArray> = channelFlow {
        while (isActive) {
            // Use safe call and Elvis operator for cleaner loop termination if read fails or returns empty
            val packet = fromRadioCharacteristic?.read()?.takeIf { it.isNotEmpty() } ?: break
            send(packet)
            delay(INTER_READ_DELAY_MS)
        }
    }

    private fun dispatchPacket(packet: ByteArray, source: String) {
        localScope.launch {
            try {
                service.handleFromRadio(p = packet)
            } catch (t: Throwable) {
                Timber.e(t, "Failed to schedule service.handleFromRadio (source=$source)")
            }
        }
    }

    private fun drainPacketQueueAndDispatch(source: String) {
        var drainedCount = 0
        packetQueueFlow()
            .onEach { packet ->
                drainedCount++
                logPacketRead(source, packet)
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

    // --- Connection & Discovery Logic ---

    private suspend fun findPeripheral(): Peripheral =
        centralManager.scan(5.seconds).mapNotNull { it.peripheral }.firstOrNull { it.address == address }
            ?: throw RadioNotConnectedException("Device not found at address $address")

    private fun connect() {
        localScope.launch {
            try {
                peripheral = findAndConnectPeripheral()
                peripheral?.let {
                    observePeripheralChanges()
                    discoverServicesAndSetupCharacteristics(it)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect to peripheral $address")
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
            p.phy.onEach { phy -> Timber.d("Peripheral $address: PHY changed to $phy") }.launchIn(localScope)
            p.connectionParameters
                .onEach { Timber.d("Peripheral $address: Connection parameters changed to $it") }
                .launchIn(localScope)
            p.state.onEach { state -> Timber.d("Peripheral $address: State changed to $state") }.launchIn(localScope)
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

                        logCharacteristicInfo()
                        setupNotifications()
                        service.onConnect()
                    } else {
                        Timber.w("Meshtastic service not found on peripheral $address")
                    }
                }
                .launchIn(localScope)
        }
    }

    // --- Notification Setup ---

    @OptIn(ExperimentalUuidApi::class)
    private suspend fun setupNotifications() {
        fromNumCharacteristic
            ?.subscribe()
            ?.onEach { notifyBytes ->
                logFromNumNotification(notifyBytes)
                drainPacketQueueAndDispatch("notify")
            }
            ?.catch { e -> Timber.e(e, "Error in subscribe flow for fromNumCharacteristic") }
            ?.onCompletion { cause -> Timber.d("fromNum subscribe flow completed, cause=$cause") }
            ?.launchIn(scope = localScope)
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

            localScope.launch {
                try {
                    characteristic.write(p, writeType = WriteType.WITHOUT_RESPONSE)
                    // Post-write action initiation
                    localScope.launch {
                        delay(POST_WRITE_DELAY_MS)
                        drainPacketQueueAndDispatch("post-write")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to write packet to $address")
                }
            }
        } ?: Timber.w("toRadioCharacteristic not available when attempting to send data to $address")
    }

    /** Closes the connection to the device. */
    override fun close() {
        localScope.launch { peripheral?.disconnect() }
    }

    // --- Logging Helpers ---

    @OptIn(ExperimentalUuidApi::class)
    private fun logCharacteristicInfo() {
        Timber.d(
            "toRadioCharacteristic discovered: uuid=${toRadioCharacteristic?.uuid} instanceId=${toRadioCharacteristic?.instanceId}",
        )
        Timber.d(
            "fromNumCharacteristic discovered: uuid=${fromNumCharacteristic?.uuid} instanceId=${fromNumCharacteristic?.instanceId}",
        )
        Timber.d(
            "fromRadioCharacteristic discovered (packet queue): uuid=${fromRadioCharacteristic?.uuid} instanceId=${fromRadioCharacteristic?.instanceId}",
        )
    }

    private fun logPacketRead(source: String, packet: ByteArray) {
        val hexString = packet.joinToString(prefix = "[", postfix = "]") { b -> String.format("0x%02x", b) }
        Timber.d(
            "[$source] Read packet queue returned ${packet.size}" +
                " bytes: $hexString - dispatching to service.handleFromRadio()",
        )
    }

    private fun logFromNumNotification(notifyBytes: ByteArray) {
        val hexString = notifyBytes.joinToString(prefix = "[", postfix = "]") { b -> String.format("0x%02x", b) }
        Timber.d("FROMNUM notify, ${notifyBytes.size} bytes: $hexString - reading packet queue")
    }

    companion object {
        private const val INTER_READ_DELAY_MS: Long = 5L
        private const val POST_WRITE_DELAY_MS: Long = 5L
    }
}

object BleUuidConstants {
    val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    val BTM_TORADIO_CHARACTER: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
    val BTM_FROMNUM_CHARACTER: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    val BTM_FROMRADIO_CHARACTER: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
}
