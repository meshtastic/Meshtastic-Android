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

@file:Suppress("MissingPermission")

package com.geeksville.mesh.service

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.geeksville.mesh.concurrent.CallbackContinuation
import com.geeksville.mesh.concurrent.Continuation
import com.geeksville.mesh.concurrent.SyncContinuation
import com.geeksville.mesh.logAssert
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import timber.log.Timber
import java.io.Closeable
import java.util.UUID

private val Context.bluetoothManager
    get() = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

// / Return a standard BLE 128 bit UUID from the short 16 bit versions
fun longBLEUUID(hexFour: String): UUID = UUID.fromString("0000$hexFour-0000-1000-8000-00805f9b34fb")

/**
 * Uses coroutines to safely access a bluetooth GATT device with a synchronous API
 *
 * The BTLE API on android is dumb. You can only have one outstanding operation in flight to the device. If you try to
 * do something when something is pending, the operation just returns false. You are expected to chain your operations
 * from the results callbacks.
 *
 * This class fixes the API by using coroutines to let you safely do a series of BTLE operations.
 */
class SafeBluetooth(
    private val context: Context,
    private val device: BluetoothDevice,
    private val analytics: PlatformAnalytics,
) : Closeable {

    // / Users can access the GATT directly as needed
    @Volatile var gatt: BluetoothGatt? = null

    @Volatile var state = BluetoothProfile.STATE_DISCONNECTED

    internal val workQueue = BluetoothWorkQueue()

    // Called for reconnection attemps
    @Volatile private var connectionCallback: ((Result<Unit>) -> Unit)? = null

    @Volatile private var lostConnectCallback: (() -> Unit)? = null

    // / from characteristic UUIDs to the handler function for notfies
    internal val notifyHandlers = mutableMapOf<UUID, (BluetoothGattCharacteristic) -> Unit>()

    /** A BLE status code based error */
    class BLEStatusException(val status: Int, msg: String) : BLEException(msg)

    // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
    private val configurationDescriptorUUID = longBLEUUID("2902")

    /**
     * skanky hack to restart BLE if it says it is hosed
     * https://stackoverflow.com/questions/35103701/ble-android-onconnectionstatechange-not-being-called
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    internal fun restartBle() {
        analytics.track("ble_restart") // record # of times we needed to use this nasty hack
        Timber.w("Doing emergency BLE restart")
        context.bluetoothManager?.adapter?.let { adp ->
            if (adp.isEnabled) {
                adp.disable()
                // TODO: display some kind of UI about restarting BLE
                mainHandler.postDelayed(
                    {
                        if (!adp.isEnabled) {
                            adp.enable()
                        } else {
                            mainHandler.postDelayed(this::restartBle, 2500)
                        }
                    },
                    2500,
                )
            }
        }
    }

    companion object {
        // Our own custom BLE status codes
        internal const val STATUS_RELIABLE_WRITE_FAILED = 4403
    }

    // The original implementation had autoReconnect = true, but this was causing issues
    // with clients trying to manage their own state and reconnect logic.
    // Setting it to false means the client is responsible for initiating reconnects.
    internal var autoReconnect = false
        private set

    private val gattCallback = SafeBluetoothGattCallback(this)

    // / helper glue to make sync continuations and then wait for the result
    private fun <T> makeSync(wrappedFn: (SyncContinuation<T>) -> Unit): T {
        val cont = SyncContinuation<T>()
        wrappedFn(cont)
        return cont.await() // was timeoutMsec but we now do the timeout at the lower BLE level
    }

    // Is the gatt trying to repeatedly connect as needed?
    // private var autoConnect = false

    // / True if the current active connection is auto (possible for this to be false but autoConnect to be true
    // / if we are in the first non-automated lowLevel connect.
    internal var currentConnectIsAuto = false
        private set

    internal fun lowLevelConnect(autoNow: Boolean): BluetoothGatt? {
        currentConnectIsAuto = autoNow
        logAssert(gatt == null)

        // MinSdk is 26, so we always use TRANSPORT_LE
        val g = device.connectGatt(context, autoNow, gattCallback, BluetoothDevice.TRANSPORT_LE)

        gatt = g
        return g
    }

    // If autoConnect is false, it will try to connect now and will timeout and fail in 30 seconds.
    // If autoConnect is true, it will attempt to connect immediately and will also attempt to reconnect
    // automatically if the connection is lost.
    private fun queueConnect(autoConnect: Boolean = false, cont: Continuation<Unit>, timeout: Long = 0) {
        this.autoReconnect = autoConnect

        // assert(gatt == null) this now might be !null with our new reconnect support
        workQueue.queueWork("connect", cont, timeout) {
            // Note: To workaround https://issuetracker.google.com/issues/36995652
            // Always call BluetoothDevice#connectGatt() with autoConnect=false
            // (the race condition does not affect that case). If that connection times out
            // you will get a callback with status=133. Then call BluetoothGatt#connect()
            // to initiate a background connection.
            lowLevelConnect(false) != null
        }
    }

    /**
     * start a connection attempt.
     *
     * Note: if autoConnect is true, the callback you provide will be kept around _even after the connection is
     * complete. If we ever lose the connection, this class will immediately requque the attempt (after canceling any
     * outstanding queued operations).
     *
     * So you should expect your callback might be called multiple times, each time to reestablish a new connection.
     */
    fun asyncConnect(autoConnect: Boolean = false, cb: (Result<Unit>) -> Unit, lostConnectCb: () -> Unit) {
        // logAssert(workQueue.isEmpty())
        if (workQueue.currentWork != null) throw AssertionError("currentWork was not null: ${workQueue.currentWork}")

        lostConnectCallback = lostConnectCb
        connectionCallback = if (autoConnect) cb else null
        queueConnect(autoConnect, CallbackContinuation(cb))
    }

    // / Restart any previous connect attempts
    @Suppress("unused")
    private fun reconnect() {
        // closeGatt() // Get rid of any old gatt

        connectionCallback?.let { cb -> queueConnect(true, CallbackContinuation(cb)) }
    }

    internal fun lostConnection(reason: String) {
        workQueue.failAllWork(BLEException(reason))

        // Cancel any notifications - because when the device comes back it might have forgotten about us
        notifyHandlers.clear()

        lostConnectCallback?.invoke()
    }

    // / Drop our current connection and then requeue a connect as needed
    internal fun dropAndReconnect() {
        lostConnection("lost connection, reconnecting")

        // Queue a new connection attempt
        val cb = connectionCallback
        if (cb != null) {
            Timber.d("queuing a reconnection callback")
            assert(workQueue.currentWork == null)

            if (
                !currentConnectIsAuto
            ) { // we must have been running during that 1-time manual connect, switch to auto-mode from now on
                closeGatt() // Close the old non-auto connection
                lowLevelConnect(true)
            }

            // note - we don't need an init fn (because that would normally redo the connectGatt call - which we don't
            // need)
            workQueue.queueWork("reconnect", CallbackContinuation(cb), 0) { true }
        } else {
            Timber.d("No connectionCallback registered")
        }
    }

    fun connect(autoConnect: Boolean = false) = makeSync<Unit> { queueConnect(autoConnect, it) }

    private fun queueReadCharacteristic(
        c: BluetoothGattCharacteristic,
        cont: Continuation<BluetoothGattCharacteristic>,
        timeout: Long = 0,
    ) = workQueue.queueWork("readC ${c.uuid}", cont, timeout) { gatt!!.readCharacteristic(c) }

    fun asyncReadCharacteristic(c: BluetoothGattCharacteristic, cb: (Result<BluetoothGattCharacteristic>) -> Unit) =
        queueReadCharacteristic(c, CallbackContinuation(cb))

    private fun queueDiscoverServices(cont: Continuation<Unit>, timeout: Long = 0) {
        workQueue.queueWork("discover", cont, timeout) { gatt?.discoverServices() ?: false }
    }

    fun asyncDiscoverServices(cb: (Result<Unit>) -> Unit) {
        queueDiscoverServices(CallbackContinuation(cb))
    }

    /**
     * mtu operations seem to hang sometimes. To cope with this we have a 5 second timeout before throwing an exception
     * and cancelling the work
     */
    private fun queueRequestMtu(len: Int, cont: Continuation<Unit>) = workQueue.queueWork("reqMtu", cont, 10 * 1000) {
        workQueue.isSettingMtu = true
        gatt?.requestMtu(len) ?: false
    }

    fun asyncRequestMtu(len: Int, cb: (Result<Unit>) -> Unit) {
        queueRequestMtu(len, CallbackContinuation(cb))
    }

    @Volatile internal var currentReliableWrite: ByteArray? = null

    private fun queueWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        v: ByteArray,
        cont: Continuation<BluetoothGattCharacteristic>,
        timeout: Long = 0,
    ) = workQueue.queueWork("writeC ${c.uuid}", cont, timeout) {
        currentReliableWrite = null
        val g = gatt
        if (g != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use modern API for Android 13+
                g.writeCharacteristic(c, v, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                // Use deprecated API for older Android versions
                @Suppress("DEPRECATION")
                c.value = v
                @Suppress("DEPRECATION")
                g.writeCharacteristic(c)
            }
        } else {
            false
        }
    }

    fun asyncWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        v: ByteArray,
        cb: (Result<BluetoothGattCharacteristic>) -> Unit,
    ) = queueWriteCharacteristic(c, v, CallbackContinuation(cb))

    private fun queueWriteDescriptor(
        c: BluetoothGattDescriptor,
        value: ByteArray,
        cont: Continuation<BluetoothGattDescriptor>,
        timeout: Long = 0,
    ) = workQueue.queueWork("writeD", cont, timeout) {
        val g = gatt
        if (g != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use modern API for Android 13+
                g.writeDescriptor(c, value) == BluetoothStatusCodes.SUCCESS
            } else {
                // Use deprecated API for older Android versions
                @Suppress("DEPRECATION")
                c.value = value
                @Suppress("DEPRECATION")
                g.writeDescriptor(c)
            }
        } else {
            false
        }
    }

    fun asyncWriteDescriptor(
        c: BluetoothGattDescriptor,
        value: ByteArray,
        cb: (Result<BluetoothGattDescriptor>) -> Unit,
    ) = queueWriteDescriptor(c, value, CallbackContinuation(cb))

    // Added: Support reading remote RSSI
    private fun queueReadRemoteRssi(cont: Continuation<Int>, timeout: Long = 0) =
        workQueue.queueWork("readRSSI", cont, timeout) { gatt?.readRemoteRssi() ?: false }

    fun asyncReadRemoteRssi(cb: (Result<Int>) -> Unit) = queueReadRemoteRssi(CallbackContinuation(cb))

    /**
     * Some old androids have a bug where calling disconnect doesn't guarantee that the onConnectionStateChange callback
     * gets called but the only safe way to call gatt.close is from that callback. So we set a flag once we start
     * closing and then poll until we see the callback has set gatt to null (indicating the CALLBACK has close the
     * gatt). If the timeout expires we assume the bug has occurred, and we manually close the gatt here.
     *
     * per https://github.com/don/cordova-plugin-ble-central/issues/473#issuecomment-367687575
     */
    @Volatile internal var isClosing = false

    /** Close just the GATT device but keep our pending callbacks active */
    @Suppress("TooGenericExceptionCaught")
    fun closeGatt() {
        val g = gatt ?: return
        Timber.i("Closing our GATT connection")
        isClosing = true
        try {
            g.disconnect()
            g.close()
        } catch (e: Exception) {
            Timber.w(e, "Ignoring exception in close, probably bluetooth was just disabled")
        } finally {
            gatt = null
            isClosing = false
        }
    }

    /**
     * Close down any existing connection, any existing calls (including async connects will be cancelled and you'll
     * need to recall connect to use this againt
     */
    fun closeConnection() {
        // Set these to null _before_ calling gatt.disconnect(), because we don't want the old lostConnectCallback to
        // get called
        lostConnectCallback = null
        connectionCallback = null

        // Cancel any notifications - because when the device comes back it might have forgotten about us
        notifyHandlers.clear()

        closeGatt()

        workQueue.failAllWork(BLEConnectionClosing())
    }

    /** Close and destroy this SafeBluetooth instance. You'll need to make a new instance before using it again */
    override fun close() {
        closeConnection()
    }

    // / asyncronously turn notification on/off for a characteristic
    fun setNotify(c: BluetoothGattCharacteristic, enable: Boolean, onChanged: (BluetoothGattCharacteristic) -> Unit) {
        Timber.d("starting setNotify(${c.uuid}, $enable)")
        notifyHandlers[c.uuid] = onChanged
        // c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt!!.setCharacteristicNotification(c, enable)

        // per https://stackoverflow.com/questions/27068673/subscribe-to-a-ble-gatt-notification-android
        val descriptor: BluetoothGattDescriptor =
            c.getDescriptor(configurationDescriptorUUID)
                ?: throw BLEException(
                    "Notify descriptor not found for ${c.uuid}",
                ) // This can happen on buggy BLE implementations

        val descriptorValue =
            if (enable) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }

        asyncWriteDescriptor(descriptor, descriptorValue) { Timber.d("Notify enable=$enable completed") }
    }
}
