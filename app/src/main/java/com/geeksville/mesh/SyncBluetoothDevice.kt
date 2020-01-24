package com.geeksville.mesh

import android.bluetooth.*
import android.content.Context
import com.geeksville.android.Logging
import java.io.IOException
import com.geeksville.concurrent.SyncContinuation
import com.geeksville.concurrent.suspend


/**
 * Uses coroutines to safely access a bluetooth GATT device with a synchronous API
 *
 * The BTLE API on android is dumb.  You can only have one outstanding operation in flight to
 * the device.  If you try to do something when something is pending, the operation just returns
 * false.  You are expected to chain your operations from the results callbacks.
 *
 * This class fixes the API by using coroutines to let you safely do a series of BTLE operations.
 */
class SyncBluetoothDevice(private val context: Context, private val device: BluetoothDevice) :
    Logging {

    private var pendingServiceDesc: SyncContinuation<Unit>? = null
    private var pendingMtu: SyncContinuation<Int>? = null
    private var pendingWriteC: SyncContinuation<Unit>? = null
    private var pendingReadC: SyncContinuation<BluetoothGattCharacteristic>? = null
    private var pendingConnect: SyncContinuation<Unit>? = null

    var state = BluetoothProfile.STATE_DISCONNECTED

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            info("new bluetooth connection state $newState")
            state = newState
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (pendingConnect != null) { // If someone was waiting to connect unblock them
                        pendingConnect!!.resume(Unit)
                        pendingConnect = null
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // cancel any ops

                    val pendings = listOf(pendingMtu, pendingServiceDesc, pendingWriteC, pendingReadC, pendingConnect)
                    pendings.filterNotNull().forEach {
                        it.resumeWithException(IOException("Lost connection"))
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != 0)
                pendingServiceDesc!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingServiceDesc!!.resume(Unit)
            pendingServiceDesc = null
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != 0)
                pendingReadC!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingReadC!!.resume(characteristic)
            pendingReadC = null
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != 0)
                pendingWriteC!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingWriteC!!.resume(Unit)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status != 0)
                pendingMtu!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingMtu!!.resume(mtu)
            pendingMtu = null
        }
    }

    /// Users can access the GATT directly as needed
    lateinit var gatt: BluetoothGatt

    fun connect() =
        suspend<Unit> { cont ->
            pendingConnect = cont
            gatt = device.connectGatt(context, false, gattCallback)!!
        }

    fun discoverServices() =
        suspend<Unit> { cont ->
            pendingServiceDesc = cont
            logAssert(gatt.discoverServices())
        }

    /// Returns the actual MTU size used
    fun requestMtu(len: Int) = suspend<Int> { cont ->
        pendingMtu = cont
        logAssert(gatt.requestMtu(len))
    }

    fun writeCharacteristic(c: BluetoothGattCharacteristic) =
        suspend<Unit> { cont ->
            pendingWriteC = cont
            logAssert(gatt.writeCharacteristic(c))
        }

    fun readCharacteristic(c: BluetoothGattCharacteristic) =
        suspend<BluetoothGattCharacteristic> { cont ->
            pendingReadC = cont
            logAssert(gatt.readCharacteristic(c))
        }

    fun disconnect() {
        gatt.disconnect()
    }
}
