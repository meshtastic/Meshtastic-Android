package com.geeksville.mesh.repository.usb

import android.app.Application
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.CoroutineDispatchers
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for maintaining and updating the state of USB connectivity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class UsbRepository @Inject constructor(
    private val application: Application,
    private val dispatchers: CoroutineDispatchers,
    private val processLifecycle: Lifecycle,
    private val usbBroadcastReceiverLazy: dagger.Lazy<UsbBroadcastReceiver>,
    private val usbManagerLazy: dagger.Lazy<UsbManager?>,
    private val usbSerialProberLazy: dagger.Lazy<UsbSerialProber>
) : Logging {
    private val _serialDevices = MutableStateFlow(emptyMap<String, UsbDevice>())

    @Suppress("unused") // Retained as public API
    val serialDevices = _serialDevices
        .asStateFlow()

    @Suppress("unused") // Retained as public API
    val serialDevicesWithDrivers = _serialDevices
        .mapLatest {  serialDevices ->
            val serialProber = usbSerialProberLazy.get()
            buildMap {
                serialDevices.forEach { (k, v) ->
                    serialProber.probeDevice(v)?.let { driver ->
                        put(k, driver)
                    }
                }
            }
        }.stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    @Suppress("unused") // Retained as public API
    val serialDevicesWithPermission = _serialDevices
        .mapLatest { serialDevices ->
            usbManagerLazy.get()?.let { usbManager ->
                serialDevices.filterValues { device ->
                    usbManager.hasPermission(device)
                }
            } ?: emptyMap()
        }.stateIn(processLifecycle.coroutineScope, SharingStarted.Eagerly, emptyMap())

    init {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            refreshStateInternal()
            usbBroadcastReceiverLazy.get().let { receiver ->
                application.registerReceiver(receiver, receiver.intentFilter)
            }
        }
    }

    /**
     * Creates a USB serial connection to the specified USB device.  State changes and data arrival
     * result in async callbacks on the supplied listener.
     */
    fun createSerialConnection(device: UsbSerialDriver, listener: SerialConnectionListener) : SerialConnection {
        return SerialConnectionImpl(usbManagerLazy, device, listener)
    }

    fun refreshState() {
        processLifecycle.coroutineScope.launch(dispatchers.default) {
            refreshStateInternal()
        }
    }

    private suspend fun refreshStateInternal() = withContext(dispatchers.default) {
        _serialDevices.emit(usbManagerLazy.get()?.deviceList ?: emptyMap())
    }
}