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
package org.meshtastic.core.service

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.common.di.PROCESS_LIFECYCLE
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.DeviceAddress
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence

/** Watches what ends a client's reconnect wait and what takes the port, and reports both to the link supervisor. */
@Single
class LinkEnvironmentObserver(
    private val bluetoothRepository: BluetoothRepository,
    private val networkRepository: NetworkRepository,
    private val serialDevicePresence: SerialDevicePresence,
    private val radioOperationLock: RadioOperationLock,
    @Named(PROCESS_LIFECYCLE) private val processLifecycle: Lifecycle,
) {
    private val started = atomic(false)

    /**
     * Starts once. [onRecovered] runs when Bluetooth, the network or the selected USB device comes back;
     * [onTransportSuppressed] runs with true when a firmware-maintenance operation takes the port and false when it
     * ends.
     */
    fun start(
        selectedAddress: Flow<String?>,
        onRecovered: () -> Unit,
        onTransportSuppressed: suspend (Boolean) -> Unit,
    ) {
        if (!started.compareAndSet(expect = false, update = true)) return

        // The client waits for Bluetooth, a network or the USB device by itself; these end a backoff early.
        bluetoothRepository.state
            .map { it.enabled }
            .distinctUntilChanged()
            .filter { it }
            .onEach { onRecovered() }
            .catch { Logger.e(it) { "bluetoothRepository.state flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)

        networkRepository.networkAvailable
            .distinctUntilChanged()
            .filter { it }
            .onEach { onRecovered() }
            .catch { Logger.e(it) { "networkRepository.networkAvailable flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)

        combine(selectedAddress, serialDevicePresence.deviceKeys, ::selectedSerialPresent)
            .distinctUntilChanged()
            .drop(1)
            .filter { it }
            .onEach { onRecovered() }
            .catch { Logger.e(it) { "serialDevicePresence flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)

        // A firmware-maintenance operation owns the port: the link closes for it and reopens when it ends.
        radioOperationLock.holders
            .map { holders -> holders.values.any(RadioOperation::suppressesTransport) }
            .distinctUntilChanged()
            .drop(1)
            .onEach { suppressed -> onTransportSuppressed(suppressed) }
            .catch { Logger.e(it) { "radioOperationLock flow crashed" } }
            .launchIn(processLifecycle.coroutineScope)
    }
}

/** Whether the selected radio is a USB serial device that is attached now. */
private fun selectedSerialPresent(address: String?, keys: Set<String>): Boolean {
    val key =
        DeviceAddress.parse(address)
            ?.takeIf { it.interfaceId == InterfaceId.SERIAL }
            ?.identity
            ?.takeIf {
                it.isNotEmpty()
            }
    return key != null && key in keys
}
