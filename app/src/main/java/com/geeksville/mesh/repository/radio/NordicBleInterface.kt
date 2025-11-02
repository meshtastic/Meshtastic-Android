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
import kotlinx.coroutines.flow.catch
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

    /**
     * Drain the FROMRADIO packet queue until empty, dispatching each non-empty payload to the service. This function
     * serializes access with [packetQueueMutex] to avoid concurrent reads which can confuse some peripherals. It yields
     * between reads to allow cooperative cancellation via the service scope.
     *
     * @param source A short label indicating why we're draining ("notify", "initial", "post-write")
     */
    private suspend fun drainPacketQueueAndDispatch(source: String) {
        packetQueueMutex.withLock {
            var drainedCount = 0
            try {
                while (true) {
                    // Respect coroutine cancellation (service stop should cancel this operation)
                    if (!localScope.isActive) {
                        Timber.d("Draining aborted due to coroutine cancellation (source=$source)")
                        break
                    }

                    val packet = fromRadioCharacteristic?.read()
                    if (packet == null) {
                        Timber.w("Packet queue read returned null (source=$source)")
                        break
                    }

                    if (packet.isEmpty()) {
                        Timber.d("Packet queue read returned no data (empty) (source=$source) - done draining")
                        break
                    }

                    Timber.d(
                        "[$source] Read packet queue returned ${packet.size} bytes: ${packet.joinToString(
                            prefix = "[",
                            postfix = "]",
                        ) { b ->
                            String.format("0x%02x", b)
                        }} - dispatching to service.handleFromRadio()",
                    )

                    // Prefer dispatching on the service scope; fallback to localScope if the service scope is not
                    // active
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

                    // Yield to allow cancellation and give the peripheral time to update packet queue
                    delay(INTER_READ_DELAY_MS)
                    drainedCount++
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Exception while draining packet queue (source=$source)")
            } finally {
                if (drainedCount > 0) {
                    Timber.d("[$source] Drained $drainedCount packets from packet queue")
                }
            }
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
     * Establishes a connection to the BLE peripheral. The connection process involves scanning, connecting, discovering
     * services, and setting up characteristics for reading and writing.
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun connect() {
        localScope.launch {
            try {
                // Initialize central manager bound to the service's lifecycle scope.
                centralManager = CentralManager.native(context, localScope)

                peripheral =
                    findPeripheral().also {
                        centralManager.connect(
                            peripheral = it,
                            options =
                            CentralManager.ConnectionOptions.AutoConnect(
                                automaticallyRequestHighestValueLength = true,
                            ),
                        )
                    }
            } catch (e: Exception) {
                Timber.e(e, "Error connecting to device")
                service.onDisconnect(false)
                return@launch
            }

            peripheral?.requestConnectionPriority(ConnectionPriority.HIGH)

            peripheral?.phy?.onEach { phy -> Timber.d("PHY changed to $phy") }?.launchIn(localScope)

            peripheral
                ?.connectionParameters
                ?.onEach { Timber.d("Connection parameters changed to $it") }
                ?.launchIn(localScope)

            // Observe peripheral state primarily for disconnection events.
            peripheral
                ?.state
                ?.onEach { state ->
                    Timber.d("Peripheral state changed to $state")
                    if (!state.isConnected) {
                        toRadioCharacteristic = null // Clear characteristic on disconnect
                        service.onDisconnect(false)
                    }
                }
                ?.launchIn(localScope)

            centralManager.state
                .onEach { state -> Timber.d("CentralManager state changed to $state") }
                .launchIn(localScope)

            // Discover the Meshtastic service. Once found, set up the reader,
            // cache the writer, and then notify the service that we are fully connected.
            peripheral
                ?.services(listOf(BTM_SERVICE_UUID.toKotlinUuid()))
                ?.onEach { services ->
                    val meshtasticService = services?.find { it.uuid == BTM_SERVICE_UUID.toKotlinUuid() }
                    if (meshtasticService != null) {
                        // Find both required characteristics
                        // Writer characteristic
                        toRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_TORADIO_CHARACTER.toKotlinUuid() }

                        // The device exposes two related chars. Find BOTH:
                        // - FROMNUM: notify/indicate counter that signals new data is available
                        fromNumCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_FROMNUM_CHARACTER.toKotlinUuid() }

                        // - FROMRADIO: read-only packet queue containing the full packet bytes; read this when FROMNUM
                        // notifies
                        fromRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_FROMRADIO_CHARACTER.toKotlinUuid() }

                        // CRITICAL: Ensure we found ALL necessary characteristics before proceeding
                        if (
                            toRadioCharacteristic == null ||
                            fromNumCharacteristic == null ||
                            fromRadioCharacteristic == null
                        ) {
                            Timber.e("Critical: Meshtastic characteristics not found! Cannot connect.")
                            service.onDisconnect(false)
                            return@onEach // Stop processing this service discovery event
                        }

                        // Log characteristic information to help debug notification problems
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

                        // Step 1: Start collecting notifications. This sets up the listener on our end.
                        // The flow will not emit data until notifications are enabled on the device.
                        // Subscribe to FROMNUM (notify). When we receive any notify bytes we will attempt to read the
                        // FROMRADIO packet queue characteristic to retrieve the full packet payload.
                        fromNumCharacteristic
                            ?.subscribe()
                            ?.onEach { notifyBytes ->
                                try {
                                    // Notification arrived — read packet queue and dispatch it to the service.
                                    // We call the suspendable drain directly so the collector waits for the drain
                                    // to complete before handling subsequent notifications. This avoids races and
                                    // ensures continuous reading.
                                    Timber.d(
                                        "FROMNUM notify, ${notifyBytes.size} bytes: ${
                                            notifyBytes.joinToString(
                                                prefix = "[",
                                                postfix = "]",
                                            ) { b -> String.format("0x%02x", b) }
                                        } - reading packet queue",
                                    )

                                    // Synchronously drain packet queue in the collector's coroutine
                                    drainPacketQueueAndDispatch("notify")
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Error handling incoming FROMNUM notify")
                                }
                            }
                            ?.catch { e -> Timber.e(e, "Error in subscribe flow for fromNumCharacteristic") }
                            ?.onCompletion { cause -> Timber.d("fromNum subscribe flow completed, cause=$cause") }
                            ?.launchIn(scope = localScope)

                        // Step 2: Synchronously enable notifications on the device and wait for it to confirm.
                        try {
                            Timber.d("Enabling notifications...")
                            fromNumCharacteristic?.setNotifying(true)
                            Timber.d("Notifications enabled and confirmed.")

                            // One-time initial packet queue read: many devices queue packets while the phone connects.
                            // Read the FROMRADIO packet queue once to flush any queued packets present at connect time.
                            localScope.launch {
                                try {
                                    // Perform an initial packet-queue drain to flush queued packets at connect time
                                    drainPacketQueueAndDispatch("initial")
                                } catch (re: Exception) {
                                    Timber.w(re, "Initial packet-queue read failed (non-fatal)")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to enable notifications")
                            service.onDisconnect(false)
                            return@onEach
                        }

                        // We no longer need the watchdog and fallbacks: rely on FROMNUM notify -> read FROMRADIO flow.
                        // The subscription above will read the packet queue whenever a FROMNUM notify arrives and
                        // dispatch
                        // to the service. This simplifies the connection flow and avoids redundant reads.

                        // Step 3: NOW, with everything fully ready, declare the connection open.
                        // This resolves the final race condition.
                        Timber.d("BLE services discovered and reader subscribed. Connection is ready.")
                        service.onConnect()
                    }
                }
                ?.launchIn(localScope)
        }
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
                    try {
                        delay(POST_WRITE_DELAY_MS)
                        drainPacketQueueAndDispatch("post-write")
                    } catch (re: Exception) {
                        Timber.w(re, "Post-write packet-queue single read failed (non-fatal)")
                    }
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
