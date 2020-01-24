package com.geeksville.mesh

import android.bluetooth.*
import android.content.Context
import com.geeksville.android.Logging
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


/**
 * Uses coroutines to safely access a bluetooth GATT device with a synchronous API
 *
 * The BTLE API on android is dumb.  You can only have one outstanding operation in flight to
 * the device.  If you try to do something when something is pending, the operation just returns
 * false.  You are expected to chain your operations from the results callbacks.
 *
 * This class fixes the API by using coroutines to let you safely do a series of BTLE operations.
 */
class SyncBluetoothDevice(private val context: Context, private val device: BluetoothDevice) : Logging {

    private var pendingServiceDesc: Continuation<Unit>? = null
    private var pendingMtu: Continuation<kotlin.Int>? = null
    private var pendingWriteC: Continuation<Unit>? = null
    private var pendingReadC: Continuation<BluetoothGattCharacteristic>? = null
    private var pendingConnect: Continuation<Unit>? = null

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            info("new bluetooth connection state $newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if(pendingConnect != null) { // If someone was waiting to connect unblock them
                        pendingConnect!!.resume(Unit)
                        pendingConnect = null
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    TODO("handle loss of connection")
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

    suspend fun connect() =
        suspendCoroutine<Unit> { cont ->
            pendingConnect = cont
            gatt = device.connectGatt(context, false, gattCallback)!!
        }

    suspend fun discoverServices() =
        suspendCoroutine<Unit> { cont ->
            pendingServiceDesc = cont
            logAssert(gatt.discoverServices())
        }

    /// Returns the actual MTU size used
    suspend fun requestMtu(len: Int): kotlin.Int = suspendCoroutine { cont ->
        pendingMtu = cont
        logAssert(gatt.requestMtu(len))
    }

    suspend fun writeCharacteristic(c: BluetoothGattCharacteristic) =
        suspendCoroutine<Unit> { cont ->
            pendingWriteC = cont
            logAssert(gatt.writeCharacteristic(c))
        }

    suspend fun readCharacteristic(c: BluetoothGattCharacteristic) =
        suspendCoroutine<BluetoothGattCharacteristic> { cont ->
            pendingReadC = cont
            logAssert(gatt.readCharacteristic(c))
        }

    fun disconnect() {
        gatt.disconnect()
    }
}
