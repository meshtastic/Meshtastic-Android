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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.native
import no.nordicsemi.kotlin.ble.core.WriteType
import org.meshtastic.core.analytics.platform.PlatformAnalytics
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
    private val analytics: PlatformAnalytics,
    @Assisted val address: String,
) : IRadioInterface {

    private var peripheral: Peripheral? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val centralManager = CentralManager.native(context, scope)

    /**
     * Store a reference to the characteristic to avoid re-discovering it for every write. This is more efficient and
     * reliable.
     */
    private var toRadioCharacteristic: RemoteCharacteristic? = null
    private var fromRadioCharacteristic: RemoteCharacteristic? = null

    companion object {
        val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val BTM_TORADIO_CHARACTER: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val BTM_FROMNUM_CHARACTER: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
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
        scope.launch {
            try {
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
                service.onDisconnect(true)
                return@launch
            }

            peripheral?.requestConnectionPriority(ConnectionPriority.HIGH)

            peripheral?.phy?.onEach { phy -> Timber.d("PHY changed to $phy") }?.launchIn(scope)

            peripheral
                ?.connectionParameters
                ?.onEach { Timber.d("Connection parameters changed to $it") }
                ?.launchIn(scope)

            // Observe peripheral state primarily for disconnection events.
            peripheral
                ?.state
                ?.onEach { state ->
                    Timber.d("Peripheral state changed to $state")
                    if (!state.isConnected) {
                        toRadioCharacteristic = null // Clear characteristic on disconnect
                        service.onDisconnect(true)
                    }
                }
                ?.launchIn(scope)

            centralManager.state.onEach { state -> Timber.d("CentralManager state changed to $state") }.launchIn(scope)

            // Discover the Meshtastic service. Once found, set up the reader,
            // cache the writer, and then notify the service that we are fully connected.
            peripheral
                ?.services(listOf(BTM_SERVICE_UUID.toKotlinUuid()))
                ?.onEach { services ->
                    val meshtasticService = services?.find { it.uuid == BTM_SERVICE_UUID.toKotlinUuid() }
                    if (meshtasticService != null) {
                        // Find both required characteristics
                        toRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_TORADIO_CHARACTER.toKotlinUuid() }

                        fromRadioCharacteristic =
                            meshtasticService.characteristics.find { it.uuid == BTM_FROMNUM_CHARACTER.toKotlinUuid() }

                        // CRITICAL: Ensure we found ALL necessary characteristics before proceeding
                        if (toRadioCharacteristic == null || fromRadioCharacteristic == null) {
                            Timber.e("Critical: Meshtastic characteristics not found! Cannot connect.")
                            service.onDisconnect(true)
                            return@onEach // Stop processing this service discovery event
                        }

                        // Step 1: Start collecting notifications. This sets up the listener on our end.
                        // The flow will not emit data until notifications are enabled on the device.
                        fromRadioCharacteristic
                            ?.subscribe()
                            ?.onEach {
                                Timber.d("Received ${it.size} bytes from radio.")
                                service.handleFromRadio(p = it)
                            }
                            ?.launchIn(scope = scope)

                        // Step 2: Synchronously enable notifications on the device and wait for it to confirm.
                        try {
                            Timber.d("Enabling notifications...")
                            fromRadioCharacteristic?.setNotifying(true)
                            Timber.d("Notifications enabled and confirmed.")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to enable notifications")
                            service.onDisconnect(true)
                            return@onEach
                        }

                        // Step 3: NOW, with everything fully ready, declare the connection open.
                        // This resolves the final race condition.
                        Timber.d("BLE services discovered and reader subscribed. Connection is ready.")
                        service.onConnect()
                    }
                }
                ?.launchIn(scope)
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
        scope.launch {
            try {
                Timber.d("Writing to characteristic...")
                characteristic.write(p, writeType = WriteType.WITHOUT_RESPONSE)
                Timber.d("Write operation completed without throwing an exception.")
            } catch (e: Exception) {
                Timber.e(e, "Error writing to characteristic")
            }
        }
    }

    override fun close() {
        Timber.d("Closing NordicBleInterface")
        scope.launch { peripheral?.disconnect() }
    }
}
