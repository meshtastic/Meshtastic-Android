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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.native
import no.nordicsemi.kotlin.ble.core.WriteType
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

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

    // CentralManager must be created using the service scope. Create it lazily in connect() so that
    // it binds to the current `service.serviceScope` for this interface lifecycle.
    private lateinit var centralManager: CentralManager

    /**
     * Store a reference to the characteristic to avoid re-discovering it for every write. This is more efficient and
     * reliable.
     */
    private var toRadioCharacteristic: RemoteCharacteristic? = null

    /**
     * NOTE: The mesh hardware exposes two related characteristics:
     * - FROMRADIO (packet queue) - a read-only packet queue containing the next packet bytes
     * - FROMNUM (notify/read/write) - a small counter/notify characteristic; when it notifies the phone should read
     *   FROMRADIO to fetch the full payload.
     *
     * Keep both cached here: "fromNumCharacteristic" is the notify side, and "fromRadioCharacteristic" is the packet
     * queue that we will read when notified.
     */
    private var fromNumCharacteristic: RemoteCharacteristic? = null
    private var fromRadioCharacteristic: RemoteCharacteristic? = null

    // Ensure only one coroutine performs packet-queue reads at a time to avoid races with the peripheral
    private val packetQueueMutex = Mutex()

    /** Creates a flow that reads from the packet queue until it's empty. */
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

    /**
     * Drains the FROMRADIO packet queue and dispatches each packet to the service. This function serializes access with
     * [packetQueueMutex] to avoid concurrent reads.
     *
     * @param source A short label indicating why we're draining ("notify", "initial", "post-write").
     */
    private suspend fun drainPacketQueueAndDispatch(source: String) {
        packetQueueMutex.withLock {
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

        // packet queue characteristic that must be read after a FROMNUM notify
        val BTM_FROMRADIO_CHARACTER: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")

        // Tuning constants for packet-queue reads
        // Time to wait between successive packet-queue reads (ms)
        private const val INTER_READ_DELAY_MS: Long = 5L

        // Delay after a write before attempting a post-write packet-queue read (ms)
        private const val POST_WRITE_DELAY_MS: Long = 200L
    }

    init {
        connect()
    }

    private suspend fun findPeripheral(): Peripheral =
        centralManager.scan(timeout = 5.seconds).mapNotNull { it.peripheral }.firstOrNull { it.address == address }
            ?: throw RadioNotConnectedException("Device not found")

    /**
     * Establishes a connection to the BLE peripheral.
     *
     * This function launches a coroutine to handle the entire connection lifecycle, including scanning, connecting,
     * service discovery, and setting up notifications. It also monitors the connection state and handles disconnection
     * events.
     */
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

    /**
     * Scans for the peripheral and establishes a connection.
     *
     * @return The connected [Peripheral].
     * @throws RadioNotConnectedException if the device is not found.
     */
    private suspend fun findAndConnectPeripheral(): Peripheral {
        val p = findPeripheral()
        centralManager.connect(
            peripheral = p,
            options = CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
        )
        p.requestConnectionPriority(ConnectionPriority.HIGH)
        return p
    }

    /**
     * Observes changes in the peripheral's state, PHY, and connection parameters. It also handles disconnection events.
     */
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

    /**
     * Discovers services and sets up the required characteristics for communication.
     *
     * @param peripheral The connected [Peripheral].
     */
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

    /** Logs information about the discovered characteristics. */
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

    /** Sets up notifications for the 'fromNum' characteristic and performs an initial packet queue drain. */
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
                Timber.d("Enabling notifications...")
                fromNumCharacteristic?.setNotifying(true)
                Timber.d("Notifications enabled and confirmed.")
                drainPacketQueueAndDispatch("initial")
            } catch (e: Exception) {
                Timber.e(e, "Failed to enable notifications or perform initial drain")
                service.onDisconnect(false)
            }
        }
        Timber.d("BLE services discovered and reader subscribed. Connection is ready.")
        service.onConnect()
    }

    /** Writes data to the cached 'toRadio' characteristic. */
    override fun handleSendToRadio(p: ByteArray) {
        Timber.d("handleSendToRadio called with ${p.size} bytes.")
        val characteristic = toRadioCharacteristic
        if (peripheral == null || characteristic == null) {
            Timber.w(
                "Peripheral not ready, cannot send data. Peripheral is ${if (peripheral == null) "null" else "not null"}. Characteristic is ${if (characteristic == null) "null" else "not null"}.",
            )
            return
        }

        Timber.d("Peripheral and characteristic are ready, proceeding to write.")
        localScope.launch {
            try {
                Timber.d("Writing to characteristic...")
                characteristic.write(p, writeType = WriteType.WITHOUT_RESPONSE)
                Timber.d("Write operation completed without throwing an exception.")

                // Single quick post-write read: some peripherals queue FROMRADIO after accepting a ToRadio
                // write but may not immediately notify. Try a single delayed read to capture that queued data
                // without resorting to full polling.
                localScope.launch {
                    delay(POST_WRITE_DELAY_MS)
                    drainPacketQueueAndDispatch("post-write")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error writing to characteristic")
            }
        }
    }

    override fun close() {
        Timber.d("Closing NordicBleInterface")
        // Best-effort: disable notifications and clear cached characteristics, then disconnect
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
        // Clear cached references immediately to prevent accidental use
        toRadioCharacteristic = null
        fromNumCharacteristic = null
        fromRadioCharacteristic = null
    }
}
