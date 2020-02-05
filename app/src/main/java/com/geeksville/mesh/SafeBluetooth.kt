package com.geeksville.mesh

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.geeksville.android.Logging
import com.geeksville.concurrent.CallbackContinuation
import com.geeksville.concurrent.Continuation
import com.geeksville.concurrent.SyncContinuation
import com.geeksville.util.exceptionReporter
import java.io.IOException


/**
 * Uses coroutines to safely access a bluetooth GATT device with a synchronous API
 *
 * The BTLE API on android is dumb.  You can only have one outstanding operation in flight to
 * the device.  If you try to do something when something is pending, the operation just returns
 * false.  You are expected to chain your operations from the results callbacks.
 *
 * This class fixes the API by using coroutines to let you safely do a series of BTLE operations.
 */
class SafeBluetooth(private val context: Context, private val device: BluetoothDevice) :
    Logging {

    /// Timeout before we declare a bluetooth operation failed
    var timeoutMsec = 5 * 1000L

    /// Users can access the GATT directly as needed
    var gatt: BluetoothGatt? = null

    var state = BluetoothProfile.STATE_DISCONNECTED
    private var currentWork: BluetoothContinuation? = null
    private val workQueue = mutableListOf<BluetoothContinuation>()

    // Called for reconnection attemps
    private var connectionCallback: ((Result<Unit>) -> Unit)? = null
    private var lostConnectCallback: (() -> Unit)? = null

    /// When we see the BT stack getting disabled/renabled we handle that as a connect/disconnect event
    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val newstate = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                when (newstate) {
                    // Simulate a disconnection if the user disables bluetooth entirely
                    BluetoothAdapter.STATE_OFF -> {
                        if (state == BluetoothProfile.STATE_CONNECTED)
                            gattCallback.onConnectionStateChange(
                                gatt!!,
                                0,
                                BluetoothProfile.STATE_DISCONNECTED
                            )
                        else
                            debug("We were not connected, so ignoring bluetooth shutdown")
                    }
                    BluetoothAdapter.STATE_ON -> {
                        warn("FIXME - requeue a connect anytime bluetooth is reenabled?")
                    }
                }
            }
        }
    }

    init {
        context.registerReceiver(
            btStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    /**
     * a schedulable bit of bluetooth work, includes both the closure to call to start the operation
     * and the completion (either async or sync) to call when it completes
     */
    private class BluetoothContinuation(
        val tag: String,
        val completion: com.geeksville.concurrent.Continuation<*>,
        val startWorkFn: () -> Boolean
    ) : Logging {

        /// Start running a queued bit of work, return true for success or false for fatal bluetooth error
        fun startWork(): Boolean {
            debug("Starting work: $tag")
            return startWorkFn()
        }
    }


    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            g: BluetoothGatt,
            status: Int,
            newState: Int
        ) = exceptionReporter {
            info("new bluetooth connection state $newState, status $status")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    state =
                        newState // we only care about connected/disconnected - not the transitional states
                    //logAssert(workQueue.isNotEmpty())
                    //val work = workQueue.removeAt(0)
                    completeWork(status, Unit)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    // cancel any queued ops if we were already connected
                    val oldstate = state
                    state = newState
                    if (oldstate == BluetoothProfile.STATE_CONNECTED) {
                        info("Lost connection - aborting current work")


                        /*
                        Supposedly this reconnect attempt happens automatically
                        "If the connection was established through an auto connect, Android will
                        automatically try to reconnect to the remote device when it gets disconnected
                        until you manually call disconnect() or close(). Once a connection established
                        through direct connect disconnects, no attempt is made to reconnect to the remote device."
                        https://stackoverflow.com/questions/37965337/what-exactly-does-androids-bluetooth-autoconnect-parameter-do?rq=1

                        closeConnection()
                        */
                        failAllWork(IOException("Lost connection"))

                        debug("calling lostConnect handler")
                        lostConnectCallback?.invoke()

                        // Queue a new connection attempt
                        val cb = connectionCallback
                        if (cb != null) {
                            debug("queuing a reconnection callback")
                            assert(currentWork == null)

                            // note - we don't need an init fn (because that would normally redo the connectGatt call - which we don't need
                            queueWork("reconnect", CallbackContinuation(cb)) { -> true }
                        } else {
                            debug("No connectionCallback registered")
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            completeWork(status, Unit)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            completeWork(status, characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            completeWork(status, characteristic)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            completeWork(status, mtu)
        }
    }


    /// If we have work we can do, start doing it.
    private fun startNewWork() {
        logAssert(currentWork == null)

        if (workQueue.isNotEmpty()) {
            val newWork = workQueue.removeAt(0)
            currentWork = newWork
            logAssert(newWork.startWork())
        }
    }

    private fun <T> queueWork(tag: String, cont: Continuation<T>, initFn: () -> Boolean) {
        val btCont = BluetoothContinuation(tag, cont, initFn)

        synchronized(workQueue) {
            debug("Enqueuing work: ${btCont.tag}")
            workQueue.add(btCont)

            // if we don't have any outstanding operations, run first item in queue
            if (currentWork == null)
                startNewWork()
        }
    }

    /**
     * Called from our big GATT callback, completes the current job and then schedules a new one
     */
    private fun <T : Any> completeWork(status: Int, res: T) {

        // startup next job in queue before calling the completion handler
        val work =
            synchronized(workQueue) {
                val w = currentWork!! // will throw if null, which is helpful
                currentWork = null // We are now no longer working on anything

                startNewWork()
                w
            }

        debug("work ${work.tag} is completed, resuming status=$status, res=$res")
        if (status != 0)
            work.completion.resumeWithException(IOException("Bluetooth status=$status"))
        else
            work.completion.resume(Result.success(res) as Result<Nothing>)
    }

    /**
     * Something went wrong, abort all queued
     */
    private fun failAllWork(ex: Exception) {
        synchronized(workQueue) {
            workQueue.forEach {
                it.completion.resumeWithException(ex)
            }
            workQueue.clear()
            currentWork = null
        }
    }

    /// helper glue to make sync continuations and then wait for the result
    private fun <T> makeSync(wrappedFn: (SyncContinuation<T>) -> Unit): T {
        val cont = SyncContinuation<T>()
        wrappedFn(cont)
        return cont.await(timeoutMsec)
    }

    // FIXME, pass in true for autoconnect - so we will autoconnect whenever the radio
    // comes in range (even if we made this connect call long ago when we got powered on)
    // see https://stackoverflow.com/questions/40156699/which-correct-flag-of-autoconnect-in-connectgatt-of-ble for
    // more info.
    // Otherwise if you pass in false, it will try to connect now and will timeout and fail in 30 seconds.
    private fun queueConnect(autoConnect: Boolean = false, cont: Continuation<Unit>) {
        assert(gatt == null);
        queueWork("connect", cont) {
            val g = device.connectGatt(context, autoConnect, gattCallback)
            if (g != null)
                gatt = g
            g != null
        }
    }


    /**
     * start a connection attempt.
     *
     * Note: if autoConnect is true, the callback you provide will be kept around _even after the connection is complete.
     * If we ever lose the connection, this class will immediately requque the attempt (after canceling
     * any outstanding queued operations).
     *
     * So you should expect your callback might be called multiple times, each time to reestablish a new connection.
     */
    fun asyncConnect(
        autoConnect: Boolean = false,
        cb: (Result<Unit>) -> Unit,
        lostConnectCb: () -> Unit
    ) {
        logAssert(workQueue.isEmpty() && currentWork == null) // I don't think anything should be able to sneak in front
        lostConnectCallback = lostConnectCb
        connectionCallback = if (autoConnect)
            cb
        else
            null
        queueConnect(autoConnect, CallbackContinuation(cb))
    }

    fun connect(autoConnect: Boolean = false) = makeSync<Unit> { queueConnect(autoConnect, it) }

    private fun queueReadCharacteristic(
        c: BluetoothGattCharacteristic,
        cont: Continuation<BluetoothGattCharacteristic>
    ) = queueWork("readc", cont) { gatt!!.readCharacteristic(c) }

    fun asyncReadCharacteristic(
        c: BluetoothGattCharacteristic,
        cb: (Result<BluetoothGattCharacteristic>) -> Unit
    ) = queueReadCharacteristic(c, CallbackContinuation(cb))

    fun readCharacteristic(c: BluetoothGattCharacteristic): BluetoothGattCharacteristic =
        makeSync { queueReadCharacteristic(c, it) }

    private fun queueDiscoverServices(cont: Continuation<Unit>) {
        queueWork("discover", cont) {
            gatt!!.discoverServices()
        }
    }

    fun asyncDiscoverServices(cb: (Result<Unit>) -> Unit) {
        logAssert(workQueue.isEmpty() && currentWork == null) // I don't think anything should be able to sneak in front
        queueDiscoverServices(CallbackContinuation(cb))
    }

    fun discoverServices() = makeSync<Unit> { queueDiscoverServices(it) }

    private fun queueRequestMtu(
        len: Int,
        cont: Continuation<Int>
    ) = queueWork("reqMtu", cont) { gatt!!.requestMtu(len) }

    fun asyncRequestMtu(
        len: Int,
        cb: (Result<Int>) -> Unit
    ) {
        logAssert(workQueue.isEmpty() && currentWork == null) // I don't think anything should be able to sneak in front
        queueRequestMtu(len, CallbackContinuation(cb))
    }

    fun requestMtu(len: Int): Int =
        makeSync { queueRequestMtu(len, it) }

    private fun queueWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        cont: Continuation<BluetoothGattCharacteristic>
    ) = queueWork("writec", cont) { gatt!!.writeCharacteristic(c) }

    fun asyncWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        cb: (Result<BluetoothGattCharacteristic>) -> Unit
    ) = queueWriteCharacteristic(c, CallbackContinuation(cb))

    fun writeCharacteristic(c: BluetoothGattCharacteristic): BluetoothGattCharacteristic =
        makeSync { queueWriteCharacteristic(c, it) }

    private fun closeConnection() {
        failAllWork(IOException("Connection closing"))

        if (gatt != null) {
            info("Closing our GATT connection")
            gatt!!.disconnect()
            gatt!!.close()
            gatt = null
        }
    }

    fun disconnect() {
        closeConnection()

        context.unregisterReceiver(btStateReceiver)
    }
}

