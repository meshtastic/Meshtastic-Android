package com.geeksville.mesh.service

import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.Looper
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.concurrent.CallbackContinuation
import com.geeksville.concurrent.Continuation
import com.geeksville.concurrent.SyncContinuation
import com.geeksville.mesh.android.bluetoothManager
import com.geeksville.util.exceptionReporter
import kotlinx.coroutines.*
import java.io.Closeable
import java.util.*


/// Return a standard BLE 128 bit UUID from the short 16 bit versions
fun longBLEUUID(hexFour: String): UUID = UUID.fromString("0000$hexFour-0000-1000-8000-00805f9b34fb")


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
    Logging, Closeable {

    /// Timeout before we declare a bluetooth operation failed (used for synchronous API operations only)
    var timeoutMsec = 20 * 1000L

    /// Users can access the GATT directly as needed
    @Volatile
    var gatt: BluetoothGatt? = null

    @Volatile
    var state = BluetoothProfile.STATE_DISCONNECTED

    @Volatile
    private var currentWork: BluetoothContinuation? = null
    private val workQueue = mutableListOf<BluetoothContinuation>()

    // Called for reconnection attemps
    @Volatile
    private var connectionCallback: ((Result<Unit>) -> Unit)? = null

    @Volatile
    private var lostConnectCallback: (() -> Unit)? = null

    /// from characteristic UUIDs to the handler function for notfies
    private val notifyHandlers = mutableMapOf<UUID, (BluetoothGattCharacteristic) -> Unit>()

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    /**
     * A BLE status code based error
     */
    class BLEStatusException(val status: Int, msg: String) : BLEException(msg)

    // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
    private val configurationDescriptorUUID =
        longBLEUUID("2902")

    /**
     * a schedulable bit of bluetooth work, includes both the closure to call to start the operation
     * and the completion (either async or sync) to call when it completes
     */
    private class BluetoothContinuation(
        val tag: String,
        val completion: com.geeksville.concurrent.Continuation<*>,
        val timeoutMillis: Long = 0, // If we want to timeout this operation at a certain time, use a non zero value
        private val startWorkFn: () -> Boolean
    ) : Logging {

        /// Start running a queued bit of work, return true for success or false for fatal bluetooth error
        fun startWork(): Boolean {
            debug("Starting work: $tag")
            return startWorkFn()
        }

        override fun toString(): String {
            return "Work:$tag"
        }

        /// Connection work items are treated specially
        fun isConnect() = tag == "connect" || tag == "reconnect"
    }

    /**
     * skanky hack to restart BLE if it says it is hosed
     * https://stackoverflow.com/questions/35103701/ble-android-onconnectionstatechange-not-being-called
     */
    private val mHandler: Handler = Handler(Looper.getMainLooper())

    fun restartBle() {
        GeeksvilleApplication.analytics.track("ble_restart") // record # of times we needed to use this nasty hack
        errormsg("Doing emergency BLE restart")
        context.bluetoothManager?.adapter?.let { adp ->
            if (adp.isEnabled) {
                adp.disable()
                // TODO: display some kind of UI about restarting BLE
                mHandler.postDelayed(object : Runnable {
                    override fun run() {
                        if (!adp.isEnabled) {
                            adp.enable()
                        } else {
                            mHandler.postDelayed(this, 2500)
                        }
                    }
                }, 2500)
            }
        }
    }

    // Our own custom BLE status codes
    private val STATUS_RELIABLE_WRITE_FAILED = 4403
    private val STATUS_TIMEOUT = 4404
    private val STATUS_NOSTART = 4405
    private val STATUS_SIMFAILURE = 4406

    /**
     * Should we automatically try to reconnect when we lose our connection?
     *
     * Originally this was true, but over time (now that clients are smarter and need to build
     * up more state) I see this was a mistake.  Now if the connection drops we just call
     * the lostConnection callback and the client of this API is responsible for reconnecting.
     * This also prevents nasty races when sometimes both the upperlayer and this layer decide to reconnect
     * simultaneously.
     */
    private val autoReconnect = false

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

                    // If autoconnect is on and this connect attempt failed, hopefully some future attempt will succeed
                    if (status != BluetoothGatt.GATT_SUCCESS && autoConnect) {
                        errormsg("Connect attempt failed $status, not calling connect completion handler...")
                    } else
                        completeWork(status, Unit)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (gatt == null) {
                        errormsg("No gatt: ignoring connection state $newState, status $status")
                    } else if (isClosing) {
                        info("Got disconnect because we are shutting down, closing gatt")
                        gatt = null
                        g.close() // Finish closing our gatt here
                    } else {
                        // cancel any queued ops if we were already connected
                        val oldstate = state
                        state = newState
                        if (oldstate == BluetoothProfile.STATE_CONNECTED) {
                            info("Lost connection - aborting current work: $currentWork")

                            // If we get a disconnect, just try again otherwise fail all current operations
                            // Note: if no work is pending (likely) we also just totally teardown and restart the connection, because we won't be
                            // throwing a lost connection exception to any worker.
                            if (autoReconnect && (currentWork == null || currentWork?.isConnect() == true))
                                dropAndReconnect()
                            else
                                lostConnection("lost connection")
                        } else if (status == 133) {
                            // We were not previously connected and we just failed with our non-auto connection attempt.  Therefore we now need
                            // to do an autoconnection attempt.  When that attempt succeeds/fails the normal callbacks will be called

                            // Note: To workaround https://issuetracker.google.com/issues/36995652
                            // Always call BluetoothDevice#connectGatt() with autoConnect=false
                            // (the race condition does not affect that case). If that connection times out
                            // you will get a callback with status=133. Then call BluetoothGatt#connect()
                            // to initiate a background connection.
                            if (autoConnect) {
                                warn("Failed on non-auto connect, falling back to auto connect attempt")
                                closeGatt() // Close the old non-auto connection
                                lowLevelConnect(true)
                            }
                        }

                        if (status == 257) { // mystery error code when phone is hung
                            //throw Exception("Mystery bluetooth failure - debug me")
                            restartBle()
                        }
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // For testing lie and claim failure
            completeWork(status, Unit)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            completeWork(status, characteristic)
        }

        override fun onReliableWriteCompleted(gatt: BluetoothGatt, status: Int) {
            completeWork(status, Unit)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val reliable = currentReliableWrite
            if (reliable != null)
                if (!characteristic.value.contentEquals(reliable)) {
                    errormsg("A reliable write failed!")
                    gatt.abortReliableWrite()
                    completeWork(
                        STATUS_RELIABLE_WRITE_FAILED,
                        characteristic
                    ) // skanky code to indicate failure
                } else {
                    logAssert(gatt.executeReliableWrite())
                    // After this execute reliable completes - we can continue with normal operations (see onReliableWriteCompleted)
                }
            else // Just a standard write - do the normal flow
                completeWork(status, characteristic)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            // Alas, passing back an Int mtu isn't working and since I don't really care what MTU
            // the device was willing to let us have I'm just punting and returning Unit
            if (isSettingMtu)
                completeWork(status, Unit)
            else
                errormsg("Ignoring bogus onMtuChanged")
        }

        /**
         * Callback triggered as a result of a remote characteristic notification.
         *
         * @param gatt GATT client the characteristic is associated with
         * @param characteristic Characteristic that has been updated as a result of a remote
         * notification event.
         */
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val handler = notifyHandlers.get(characteristic.uuid)
            if (handler == null)
                warn("Received notification from $characteristic, but no handler registered")
            else {
                exceptionReporter {
                    handler(characteristic)
                }
            }
        }

        /**
         * Callback indicating the result of a descriptor write operation.
         *
         * @param gatt GATT client invoked [BluetoothGatt.writeDescriptor]
         * @param descriptor Descriptor that was writte to the associated remote device.
         * @param status The result of the write operation [BluetoothGatt.GATT_SUCCESS] if the
         * operation succeeds.
         */
        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            completeWork(status, descriptor)
        }

        /**
         * Callback reporting the result of a descriptor read operation.
         *
         * @param gatt GATT client invoked [BluetoothGatt.readDescriptor]
         * @param descriptor Descriptor that was read from the associated remote device.
         * @param status [BluetoothGatt.GATT_SUCCESS] if the read operation was completed
         * successfully
         */
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            completeWork(status, descriptor)
        }
    }

    // To test loss of BLE faults we can randomly fail a certain % of all work items.  We
    // skip this for "connect" items because the handling for connection failure is special
    var simFailures = false
    var failPercent =
        10 // 15% failure is unusably high because of constant reconnects, 7% somewhat usable, 10% pretty bad
    private val failRandom = Random()

    private var activeTimeout: Job? = null

    /// If we have work we can do, start doing it.
    private fun startNewWork() {
        logAssert(currentWork == null)

        if (workQueue.isNotEmpty()) {
            val newWork = workQueue.removeAt(0)
            currentWork = newWork

            if (newWork.timeoutMillis != 0L) {

                activeTimeout = serviceScope.launch {
                    // debug("Starting failsafe timer ${newWork.timeoutMillis}")
                    delay(newWork.timeoutMillis)
                    errormsg("Failsafe BLE timer expired!")
                    completeWork(
                        STATUS_TIMEOUT,
                        Unit
                    ) // Throw an exception in that work
                }
            }

            isSettingMtu =
                false // Most work is not doing MTU stuff, the work that is will re set this flag

            val failThis =
                simFailures && !newWork.isConnect() && failRandom.nextInt(100) < failPercent

            if (failThis) {
                errormsg("Simulating random work failure!")
                completeWork(STATUS_SIMFAILURE, Unit)
            } else {
                val started = newWork.startWork()
                if (!started) {
                    errormsg("Failed to start work, returned error status")
                    completeWork(
                        STATUS_NOSTART,
                        Unit
                    ) // abandon the current attempt and try for another
                }
            }
        }
    }

    private fun <T> queueWork(
        tag: String,
        cont: Continuation<T>,
        timeout: Long,
        initFn: () -> Boolean
    ) {
        val btCont =
            BluetoothContinuation(
                tag,
                cont,
                timeout,
                initFn
            )

        synchronized(workQueue) {
            debug("Enqueuing work: ${btCont.tag}")
            workQueue.add(btCont)

            // if we don't have any outstanding operations, run first item in queue
            if (currentWork == null)
                startNewWork()
        }
    }

    /**
     * Stop any current work
     */
    private fun stopCurrentWork() {
        activeTimeout?.let {
            it.cancel()
            activeTimeout = null
        }
        currentWork = null
    }

    /**
     * Called from our big GATT callback, completes the current job and then schedules a new one
     */
    private fun <T : Any> completeWork(status: Int, res: T) {
        exceptionReporter {
            // We might unexpectedly fail inside here, but we don't want to pass that exception back up to the bluetooth GATT layer

            // startup next job in queue before calling the completion handler
            val work =
                synchronized(workQueue) {
                    val w = currentWork

                    if (w != null) {
                        stopCurrentWork() // We are now no longer working on anything

                        startNewWork()
                    }
                    w
                }

            if (work == null)
                warn("wor completed, but we already killed it via failsafetimer? status=$status, res=$res")
            else {
                // debug("work ${work.tag} is completed, resuming status=$status, res=$res")
                if (status != 0)
                    work.completion.resumeWithException(
                        BLEStatusException(
                            status,
                            "Bluetooth status=$status while doing ${work.tag}"
                        )
                    )
                else
                    work.completion.resume(Result.success(res) as Result<Nothing>)
            }
        }
    }

    /**
     * Something went wrong, abort all queued
     */
    private fun failAllWork(ex: Exception) {
        synchronized(workQueue) {
            warn("Failing ${workQueue.size} works, because ${ex.message}")
            workQueue.forEach {
                try {
                    it.completion.resumeWithException(ex)
                } catch (ex: Exception) {
                    errormsg(
                        "Mystery exception, why were we informed about our own exceptions?",
                        ex
                    )
                }
            }
            workQueue.clear()
            stopCurrentWork()
        }
    }

    /// helper glue to make sync continuations and then wait for the result
    private fun <T> makeSync(wrappedFn: (SyncContinuation<T>) -> Unit): T {
        val cont = SyncContinuation<T>()
        wrappedFn(cont)
        return cont.await() // was timeoutMsec but we now do the timeout at the lower BLE level
    }

    // Is the gatt trying to repeatedly connect as needed?
    private var autoConnect = false

    /// True if the current active connection is auto (possible for this to be false but autoConnect to be true
    /// if we are in the first non-automated lowLevel connect.
    private var currentConnectIsAuto = false

    private fun lowLevelConnect(autoNow: Boolean): BluetoothGatt? {
        currentConnectIsAuto = autoNow
        logAssert(gatt == null)

        val g = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(
                context,
                autoNow,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } else {
            device.connectGatt(
                context,
                autoNow,
                gattCallback
            )
        }

        gatt = g
        return g
    }

    // FIXME, pass in true for autoconnect - so we will autoconnect whenever the radio
    // comes in range (even if we made this connect call long ago when we got powered on)
    // see https://stackoverflow.com/questions/40156699/which-correct-flag-of-autoconnect-in-connectgatt-of-ble for
    // more info.
    // Otherwise if you pass in false, it will try to connect now and will timeout and fail in 30 seconds.
    private fun queueConnect(
        autoConnect: Boolean = false,
        cont: Continuation<Unit>,
        timeout: Long = 0
    ) {
        this.autoConnect = autoConnect

        // assert(gatt == null) this now might be !null with our new reconnect support
        queueWork("connect", cont, timeout) {

            // Note: To workaround https://issuetracker.google.com/issues/36995652
            // Always call BluetoothDevice#connectGatt() with autoConnect=false
            // (the race condition does not affect that case). If that connection times out
            // you will get a callback with status=133. Then call BluetoothGatt#connect()
            // to initiate a background connection.
            val g = lowLevelConnect(false)
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
        logAssert(workQueue.isEmpty())
        if (currentWork != null)
            throw AssertionError("currentWork was not null: $currentWork")

        lostConnectCallback = lostConnectCb
        connectionCallback = if (autoConnect)
            cb
        else
            null
        queueConnect(autoConnect, CallbackContinuation(cb))
    }

    /// Restart any previous connect attempts
    private fun reconnect() {
        // closeGatt() // Get rid of any old gatt

        connectionCallback?.let { cb ->
            queueConnect(true, CallbackContinuation(cb))
        }
    }

    private fun lostConnection(reason: String) {
        /*
        Supposedly this reconnect attempt happens automatically
        "If the connection was established through an auto connect, Android will
        automatically try to reconnect to the remote device when it gets disconnected
        until you manually call disconnect() or close(). Once a connection established
        through direct connect disconnects, no attempt is made to reconnect to the remote device."
        https://stackoverflow.com/questions/37965337/what-exactly-does-androids-bluetooth-autoconnect-parameter-do?rq=1

        closeConnection()
        */
        failAllWork(BLEException(reason))

        // Cancel any notifications - because when the device comes back it might have forgotten about us
        notifyHandlers.clear()

        lostConnectCallback?.let {
            debug("calling lostConnect handler")
            it.invoke()
        }
    }

    /// Drop our current connection and then requeue a connect as needed
    private fun dropAndReconnect() {
        lostConnection("lost connection, reconnecting")

        // Queue a new connection attempt
        val cb = connectionCallback
        if (cb != null) {
            debug("queuing a reconnection callback")
            assert(currentWork == null)

            if (!currentConnectIsAuto) { // we must have been running during that 1-time manual connect, switch to auto-mode from now on
                closeGatt() // Close the old non-auto connection
                lowLevelConnect(true)
            }

            // note - we don't need an init fn (because that would normally redo the connectGatt call - which we don't need)
            queueWork("reconnect", CallbackContinuation(cb), 0) { -> true }
        } else {
            debug("No connectionCallback registered")
        }
    }

    fun connect(autoConnect: Boolean = false) =
        makeSync<Unit> { queueConnect(autoConnect, it) }

    private fun queueReadCharacteristic(
        c: BluetoothGattCharacteristic,
        cont: Continuation<BluetoothGattCharacteristic>, timeout: Long = 0
    ) = queueWork("readC ${c.uuid}", cont, timeout) { gatt!!.readCharacteristic(c) }

    fun asyncReadCharacteristic(
        c: BluetoothGattCharacteristic,
        cb: (Result<BluetoothGattCharacteristic>) -> Unit
    ) = queueReadCharacteristic(c, CallbackContinuation(cb))

    fun readCharacteristic(
        c: BluetoothGattCharacteristic,
        timeout: Long = timeoutMsec
    ): BluetoothGattCharacteristic =
        makeSync { queueReadCharacteristic(c, it, timeout) }

    private fun queueDiscoverServices(cont: Continuation<Unit>, timeout: Long = 0) {
        queueWork("discover", cont, timeout) {
            gatt?.discoverServices()
                ?: false // throw BLEException("GATT is null") - if we return false here it is probably because the device is being torn down
        }
    }

    fun asyncDiscoverServices(cb: (Result<Unit>) -> Unit) {
        queueDiscoverServices(CallbackContinuation(cb))
    }

    fun discoverServices() = makeSync<Unit> { queueDiscoverServices(it) }

    /**
     * On some phones we receive bogus mtu gatt callbacks, we need to ignore them if we weren't setting the mtu
     */
    private var isSettingMtu = false

    /**
     * mtu operations seem to hang sometimes.  To cope with this we have a 5 second timeout before throwing an exception and cancelling the work
     */
    private fun queueRequestMtu(
        len: Int,
        cont: Continuation<Unit>
    ) = queueWork("reqMtu", cont, 10 * 1000) {
        isSettingMtu = true
        gatt?.requestMtu(len) ?: false
    }

    fun asyncRequestMtu(
        len: Int,
        cb: (Result<Unit>) -> Unit
    ) {
        queueRequestMtu(len, CallbackContinuation(cb))
    }

    fun requestMtu(len: Int): Unit = makeSync { queueRequestMtu(len, it) }

    private var currentReliableWrite: ByteArray? = null

    private fun queueWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        v: ByteArray,
        cont: Continuation<BluetoothGattCharacteristic>, timeout: Long = 0
    ) = queueWork("writeC ${c.uuid}", cont, timeout) {
        currentReliableWrite = null
        c.value = v
        gatt?.writeCharacteristic(c) ?: false
    }

    fun asyncWriteCharacteristic(
        c: BluetoothGattCharacteristic,
        v: ByteArray,
        cb: (Result<BluetoothGattCharacteristic>) -> Unit
    ) = queueWriteCharacteristic(c, v, CallbackContinuation(cb))

    fun writeCharacteristic(
        c: BluetoothGattCharacteristic,
        v: ByteArray,
        timeout: Long = timeoutMsec
    ): BluetoothGattCharacteristic =
        makeSync { queueWriteCharacteristic(c, v, it, timeout) }

    /** Like write, but we use the extra reliable flow documented here:
     * https://stackoverflow.com/questions/24485536/what-is-reliable-write-in-ble
     */
    private fun queueWriteReliable(
        c: BluetoothGattCharacteristic,
        cont: Continuation<Unit>, timeout: Long = 0
    ) = queueWork("rwriteC ${c.uuid}", cont, timeout) {
        logAssert(gatt!!.beginReliableWrite())
        currentReliableWrite = c.value.clone()
        gatt?.writeCharacteristic(c) ?: false
    }

    fun asyncWriteReliable(
        c: BluetoothGattCharacteristic,
        cb: (Result<Unit>) -> Unit
    ) = queueWriteReliable(c, CallbackContinuation(cb))

    fun writeReliable(c: BluetoothGattCharacteristic): Unit =
        makeSync { queueWriteReliable(c, it) }

    private fun queueWriteDescriptor(
        c: BluetoothGattDescriptor,
        cont: Continuation<BluetoothGattDescriptor>, timeout: Long = 0
    ) = queueWork("writeD", cont, timeout) { gatt?.writeDescriptor(c) ?: false }

    fun asyncWriteDescriptor(
        c: BluetoothGattDescriptor,
        cb: (Result<BluetoothGattDescriptor>) -> Unit
    ) = queueWriteDescriptor(c, CallbackContinuation(cb))

    /**
     * Some old androids have a bug where calling disconnect doesn't guarantee that the onConnectionStateChange callback gets called
     * but the only safe way to call gatt.close is from that callback.  So we set a flag once we start closing and then poll
     * until we see the callback has set gatt to null (indicating the CALLBACK has close the gatt).  If the timeout expires we assume the bug
     * has occurred, and we manually close the gatt here.
     *
     * Log of typical failure
     * 06-29 08:47:15.035 29788-30155/com.geeksville.mesh D/BluetoothGatt: cancelOpen() - device: 24:62:AB:F8:40:9A
    06-29 08:47:15.036 29788-30155/com.geeksville.mesh D/BluetoothGatt: close()
    06-29 08:47:15.037 29788-30155/com.geeksville.mesh D/BluetoothGatt: unregisterApp() - mClientIf=5
    06-29 08:47:15.037 29788-29813/com.geeksville.mesh D/BluetoothGatt: onClientConnectionState() - status=0 clientIf=5 device=24:62:AB:F8:40:9A
    06-29 08:47:15.037 29788-29813/com.geeksville.mesh W/BluetoothGatt: Unhandled exception in callback
    java.lang.NullPointerException: Attempt to invoke virtual method 'void android.bluetooth.BluetoothGattCallback.onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)' on a null object reference
    at android.bluetooth.BluetoothGatt$1.onClientConnectionState(BluetoothGatt.java:182)
    at android.bluetooth.IBluetoothGattCallback$Stub.onTransact(IBluetoothGattCallback.java:70)
    at android.os.Binder.execTransact(Binder.java:446)
     *
     * per https://github.com/don/cordova-plugin-ble-central/issues/473#issuecomment-367687575
     */
    @Volatile
    private var isClosing = false

    /** Close just the GATT device but keep our pending callbacks active */
    fun closeGatt() {

        gatt?.let { g ->
            info("Closing our GATT connection")
            isClosing = true
            try {
                g.disconnect()

                // Wait for our callback to run and handle hte disconnect
                var msecsLeft = 1000
                while (gatt != null && msecsLeft >= 0) {
                    Thread.sleep(100)
                    msecsLeft -= 100
                }

                gatt?.let { g2 ->
                    warn("Android onConnectionStateChange did not run, manually closing")
                    gatt =
                        null // clear gat before calling close, bcause close might throw dead object exception
                    g2.close()
                }
            } catch (ex: NullPointerException) {
                // Attempt to invoke virtual method 'com.android.bluetooth.gatt.AdvertiseClient com.android.bluetooth.gatt.AdvertiseManager.getAdvertiseClient(int)' on a null object reference
                //com.geeksville.mesh.service.SafeBluetooth.closeGatt
                warn("Ignoring NPE in close - probably buggy Samsung BLE")
            } catch (ex: DeadObjectException) {
                warn("Ignoring dead object exception, probably bluetooth was just disabled")
            } finally {
                isClosing = false
            }
        }
    }

    /**
     * Close down any existing connection, any existing calls (including async connects will be
     * cancelled and you'll need to recall connect to use this againt
     */
    fun closeConnection() {
        // Set these to null _before_ calling gatt.disconnect(), because we don't want the old lostConnectCallback to get called
        lostConnectCallback = null
        connectionCallback = null

        // Cancel any notifications - because when the device comes back it might have forgotten about us
        notifyHandlers.clear()

        closeGatt()

        failAllWork(BLEConnectionClosing())
    }

    /**
     * Close and destroy this SafeBluetooth instance.  You'll need to make a new instance before using it again
     */
    override fun close() {
        closeConnection()

        // context.unregisterReceiver(btStateReceiver)
    }


    /// asyncronously turn notification on/off for a characteristic
    fun setNotify(
        c: BluetoothGattCharacteristic,
        enable: Boolean,
        onChanged: (BluetoothGattCharacteristic) -> Unit
    ) {
        debug("starting setNotify(${c.uuid}, $enable)")
        notifyHandlers[c.uuid] = onChanged
        // c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt!!.setCharacteristicNotification(c, enable)

        // per https://stackoverflow.com/questions/27068673/subscribe-to-a-ble-gatt-notification-android
        val descriptor: BluetoothGattDescriptor = c.getDescriptor(configurationDescriptorUUID)
            ?: throw BLEException("Notify descriptor not found for ${c.uuid}") // This can happen on buggy BLE implementations
        descriptor.value =
            if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        asyncWriteDescriptor(descriptor) {
            debug("Notify enable=$enable completed")
        }
    }
}