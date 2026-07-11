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
package org.meshtastic.core.network.repository

import android.app.Application
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.registerReceiverCompat
import org.meshtastic.core.di.CoroutineDispatchers

/** Repository responsible for maintaining and updating the state of USB connectivity. */
@OptIn(ExperimentalCoroutinesApi::class)
@Single
class UsbRepository(
    private val application: Application,
    private val dispatchers: CoroutineDispatchers,
    @Named("ProcessLifecycle") private val processLifecycle: Lifecycle,
    private val usbBroadcastReceiverLazy: Lazy<UsbBroadcastReceiver>,
    private val usbManagerLazy: Lazy<UsbManager?>,
    private val usbSerialProberLazy: Lazy<UsbSerialProber>,
) {
    private val _serialDevices = MutableStateFlow(emptyMap<String, UsbDevice>())

    val serialDevices =
        _serialDevices
            .mapLatest { serialDevices ->
                val serialProber = usbSerialProberLazy.value
                // Key by a stable identity, NOT the raw deviceList key (the /dev/bus/usb/BBB/DDD path). Android
                // reassigns that path on every re-enumeration — each reboot or replug bumps it (…/002 → …/008) — so a
                // path-keyed map can't recognise the same physical node across a firmware-update reboot, and the
                // auto-reconnect in SharedRadioInterfaceService (which matches the saved address against these keys)
                // never re-arms. usbSerialStableKey() survives re-enumeration.
                buildMap {
                    serialDevices.values.forEach { device ->
                        val driver = serialProber.probeDevice(device) ?: return@forEach
                        put(driver.device.usbSerialStableKey(), driver)
                    }
                }
            }
            .stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            // Register the attach/detach receiver first so that events fired while we are
            // scanning the current device list are not dropped. The receiver resolution must
            // happen off the construction thread to avoid a Koin cycle
            // (UsbRepository <-> UsbBroadcastReceiver).
            usbBroadcastReceiverLazy.value.let { receiver ->
                application.registerReceiverCompat(receiver, receiver.intentFilter)
            }
            refreshStateInternal()
        }
    }

    /**
     * Creates a USB serial connection to the specified USB device. State changes and data arrival result in async
     * callbacks on the supplied listener.
     */
    fun createSerialConnection(device: UsbSerialDriver, listener: SerialConnectionListener): SerialConnection =
        SerialConnectionImpl(usbManagerLazy, device, listener)

    fun requestPermission(device: UsbDevice): Flow<Boolean> =
        usbManagerLazy.value?.requestPermission(application, device) ?: emptyFlow()

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) { refreshStateInternal() }
    }

    private suspend fun refreshStateInternal() = withContext(dispatchers.default) {
        val devices = usbManagerLazy.value?.deviceList ?: emptyMap()
        _serialDevices.emit(devices)
    }
}

/**
 * Stable identity for a USB serial device that survives re-enumeration.
 *
 * Uses vendor + product id, which is permission-free and identical before and after a reboot/replug (and before vs
 * after the firmware-update DFU bounce, since the app firmware re-enumerates with the same VID/PID). Deliberately does
 * NOT use [UsbDevice.getSerialNumber] — that requires an active USB permission grant, which is exactly what is missing
 * immediately after a re-enumeration, so a serial-based key would read back null mid-recovery and break the very
 * reconnect this is meant to enable.
 */
internal fun UsbDevice.usbSerialStableKey(): String = "$vendorId-$productId"
