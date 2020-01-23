package com.geeksville.mesh

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.ParcelUuid
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.JobIntentService
import com.geeksville.android.Logging
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.zip.CRC32
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
class SyncBluetoothDevice(context: Context, device: BluetoothDevice) : Logging {

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            logAssert(pendingServiceDesc != null)
            if (status != 0)
                pendingServiceDesc!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingServiceDesc!!.resume(Unit)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            logAssert(pendingReadC != null)
            if (status != 0)
                pendingReadC!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingReadC!!.resume(characteristic)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            logAssert(pendingWriteC != null)
            if (status != 0)
                pendingWriteC!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingWriteC!!.resume(Unit)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            logAssert(pendingMtu != null)
            if (status != 0)
                pendingMtu!!.resumeWithException(IOException("Bluetooth status=$status"))
            else
                pendingMtu!!.resume(mtu)
        }
    }

    /// Users can access the GATT directly as needed
    val gatt = device.connectGatt(context, true, gattCallback)!!

    private var pendingServiceDesc: Continuation<Unit>? = null
    private var pendingMtu: Continuation<Int>? = null
    private var pendingWriteC: Continuation<Unit>? = null
    private var pendingReadC: Continuation<BluetoothGattCharacteristic>? = null

    suspend fun discoverServices(c: BluetoothGattCharacteristic) =
        suspendCoroutine<Unit> { cont ->
            pendingServiceDesc = cont
            logAssert(gatt.discoverServices())
        }

    /// Returns the actual MTU size used
    suspend fun requestMtu(len: Int) = suspendCoroutine<Int> { cont ->
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

/**
 * typical flow
 *
 * startScan
 * startUpdate
 * sendNextBlock
 * finishUpdate
 *
 * stopScan
 *
 * FIXME - if we don't find a device stop our scan
 * FIXME - broadcast when we found devices, made progress sending blocks or when the update is complete
 * FIXME - make the user decide to start an update on a particular device
 */
class SoftwareUpdateService : JobIntentService(), Logging {

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter!!
    }


    fun startUpdate() {
        info("starting update")
        firmwareStream = assets.open("firmware.bin")
        firmwareCrc.reset()
        firmwareNumSent = 0
        firmwareSize = firmwareStream.available()

        // we begin by setting our MTU size as high as it can go
        logAssert(updateGatt.requestMtu(512))
    }

    // Send the next block of our file to the device
    fun sendNextBlock() {

        if (firmwareNumSent < firmwareSize) {
            info("sending block ${firmwareNumSent * 100 / firmwareSize}%")
            var blockSize = 512 - 3 // Max size MTU excluding framing

            if (blockSize > firmwareStream.available())
                blockSize = firmwareStream.available()
            val buffer = ByteArray(blockSize)

            // slightly expensive to keep reallocing this buffer, but whatever
            logAssert(firmwareStream.read(buffer) == blockSize)
            firmwareCrc.update(buffer)

            // updateGatt.beginReliableWrite()
            dataDesc.value = buffer
            logAssert(updateGatt.writeCharacteristic(dataDesc))
            firmwareNumSent += blockSize
        } else {
            // We have finished sending all our blocks, so post the CRC so our state machine can advance
            val c = firmwareCrc.value
            info("Sent all blocks, crc is $c")
            logAssert(crc32Desc.setValue(c.toInt(), BluetoothGattCharacteristic.FORMAT_UINT32, 0))
            logAssert(updateGatt.writeCharacteristic(crc32Desc))
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        debug("Connect to $device")

        lateinit var bluetoothGatt: BluetoothGatt // late init so we can declare our callback and use this there

        //var connectionState = STATE_DISCONNECTED

        // Various callback methods defined by the BLE API.
        val gattCallback = object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                info("new bluetooth connection state $newState")
                //val intentAction: String
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        //intentAction = ACTION_GATT_CONNECTED
                        //connectionState = STATE_CONNECTED
                        // broadcastUpdate(intentAction)
                        logAssert(bluetoothGatt.discoverServices())
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        //intentAction = ACTION_GATT_DISCONNECTED
                        //connectionState = STATE_DISCONNECTED
                        // broadcastUpdate(intentAction)
                    }
                }
            }

            // New services discovered
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                info("onServicesDiscovered")
                logAssert(status == BluetoothGatt.GATT_SUCCESS)

                // broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                val service = gatt.services.find { it.uuid == SW_UPDATE_UUID }
                logAssert(service != null)

                // FIXME instead of slamming in the target device here, instead make it a param for startUpdate
                updateService = service!!
                totalSizeDesc = service.getCharacteristic(SW_UPDATE_TOTALSIZE_CHARACTER)
                dataDesc = service.getCharacteristic(SW_UPDATE_DATA_CHARACTER)
                crc32Desc = service.getCharacteristic(SW_UPDATE_CRC32_CHARACTER)
                updateResultDesc = service.getCharacteristic(SW_UPDATE_RESULT_CHARACTER)

                // FIXME instead of keeping the connection open, make start update just reconnect (needed once user can choose devices)
                updateGatt = bluetoothGatt
                enqueueWork(this@SoftwareUpdateService, startUpdateIntent)
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                debug("onMtuChanged $mtu")
                logAssert(status == BluetoothGatt.GATT_SUCCESS)

                // Start the update by writing the # of bytes in the image
                logAssert(
                    totalSizeDesc.setValue(
                        firmwareSize,
                        BluetoothGattCharacteristic.FORMAT_UINT32,
                        0
                    )
                )
                logAssert(updateGatt.writeCharacteristic(totalSizeDesc))
            }

            // Result of a characteristic read operation
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                debug("onCharacteristicRead $characteristic")
                logAssert(status == BluetoothGatt.GATT_SUCCESS)

                if (characteristic == totalSizeDesc) {
                    // Our read of this has completed, either fail or continue updating
                    val readvalue =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                    logAssert(readvalue != 0) // FIXME - handle this case
                    enqueueWork(this@SoftwareUpdateService, sendNextBlockIntent)
                } else if (characteristic == updateResultDesc) {
                    // we just read the update result if !0 we have an error
                    val readvalue =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    logAssert(readvalue == 0) // FIXME - handle this case
                } else {
                    warn("Unexpected read: $characteristic")
                }

                // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                debug("onCharacteristicWrite $characteristic")
                logAssert(status == BluetoothGatt.GATT_SUCCESS)

                if (characteristic == totalSizeDesc) {
                    // Our write completed, queue up a readback
                    logAssert(updateGatt.readCharacteristic(totalSizeDesc))
                } else if (characteristic == dataDesc) {
                    enqueueWork(this@SoftwareUpdateService, sendNextBlockIntent)
                } else if (characteristic == crc32Desc) {
                    // Now that we wrote the CRC, we should read the result code
                    logAssert(updateGatt.readCharacteristic(updateResultDesc))
                } else {
                    warn("Unexpected write: $characteristic")
                }
            }
        }
        bluetoothGatt =
            device.connectGatt(this@SoftwareUpdateService.applicationContext, true, gattCallback)!!
        toast("Connected to $device")

        // too early to do this here
        // logAssert(bluetoothGatt.discoverServices())
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            throw NotImplementedError()
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            throw NotImplementedError()
        }

        // For each device that appears in our scan, ask for its GATT, when the gatt arrives,
        // check if it is an eligable device and store it in our list of candidates
        // if that device later disconnects remove it as a candidate
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            info("onScanResult")

            // We don't need any more results now
            bluetoothAdapter.bluetoothLeScanner.stopScan(this)

            connectToDevice(result.device)
        }
    }

    // Until my race condition with scanning is fixed
    fun connectToTestDevice() {
        connectToDevice(bluetoothAdapter.getRemoteDevice("B4:E6:2D:EA:32:B7"))
    }

    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                /* handler.postDelayed({
                    mScanning = false
                    bluetoothAdapter.stopLeScan(leScanCallback)
                }, SCAN_PERIOD)
                mScanning = true */

                val scanner = bluetoothAdapter.bluetoothLeScanner

                // filter and only accept devices that have a sw update service
                val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SW_UPDATE_UUID)).build()

                /* ScanSettings.CALLBACK_TYPE_FIRST_MATCH seems to trigger a bug returning an error of
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES (error #5)
                 */
                val settings =
                    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).
                        // setMatchMode(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT).
                        // setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH).
                        build()
                scanner.startScan(listOf(filter), settings, scanCallback)
            }
            else -> {
                // mScanning = false
                // bluetoothAdapter.stopLeScan(leScanCallback)
            }
        }
    }

    override fun onHandleWork(intent: Intent) { // We have received work to do.  The system or framework is already
// holding a wake lock for us at this point, so we can just go.
        debug("Executing work: $intent")
        when (intent.action) {
            scanDevicesIntent.action -> connectToTestDevice() // FIXME scanLeDevice(true)
            startUpdateIntent.action -> startUpdate()
            sendNextBlockIntent.action -> sendNextBlock()
            else -> logAssert(false)
        }

        debug(
            "Completed service @ " + SystemClock.elapsedRealtime()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // toast("All work complete")
    }

    val mHandler = Handler()
    // Helper for showing tests
    fun toast(text: CharSequence?) {
        mHandler.post {
            Toast.makeText(this@SoftwareUpdateService, text, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        /**
         * Unique job ID for this service.  Must be the same for all work.
         */
        const val JOB_ID = 1000

        val scanDevicesIntent = Intent("com.geeksville.com.geeeksville.mesh.SCAN_DEVICES")
        val startUpdateIntent = Intent("com.geeksville.com.geeeksville.mesh.START_UPDATE")
        private val sendNextBlockIntent =
            Intent("com.geeksville.com.geeeksville.mesh.SEND_NEXT_BLOCK")

        private const val SCAN_PERIOD: Long = 10000

        //const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        //const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"

        private val TAG =
            MainActivity::class.java.simpleName // FIXME - use my logging class instead

        private val SW_UPDATE_UUID = UUID.fromString("cb0b9a0b-a84c-4c0d-bdbb-442e3144ee30")
        private val SW_UPDATE_TOTALSIZE_CHARACTER =
            UUID.fromString("e74dd9c0-a301-4a6f-95a1-f0e1dbea8e1e") // write|read          total image size, 32 bit, write this first, then read read back to see if it was acceptable (0 mean not accepted)
        private val SW_UPDATE_DATA_CHARACTER =
            UUID.fromString("e272ebac-d463-4b98-bc84-5cc1a39ee517") //  write               data, variable sized, recommended 512 bytes, write one for each block of file
        private val SW_UPDATE_CRC32_CHARACTER =
            UUID.fromString("4826129c-c22a-43a3-b066-ce8f0d5bacc6") //  write               crc32, write last - writing this will complete the OTA operation, now you can read result
        private val SW_UPDATE_RESULT_CHARACTER =
            UUID.fromString("5e134862-7411-4424-ac4a-210937432c77") // read|notify         result code, readable but will notify when the OTA operation completes

        // FIXME - this is state that really more properly goes with the serice instance, but
        // it can go away if our work queue gets empty.  So we keep it here instead. Not sure
        // if there is a better approach?
        lateinit var updateGatt: BluetoothGatt // the gatt api used to talk to our device
        lateinit var updateService: BluetoothGattService // The service we are currently talking to to do the update
        lateinit var totalSizeDesc: BluetoothGattCharacteristic
        lateinit var dataDesc: BluetoothGattCharacteristic
        lateinit var crc32Desc: BluetoothGattCharacteristic
        lateinit var updateResultDesc: BluetoothGattCharacteristic
        lateinit var firmwareStream: InputStream
        val firmwareCrc = CRC32()
        var firmwareNumSent = 0
        var firmwareSize = 0

        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(
                context,
                SoftwareUpdateService::class.java, JOB_ID, work
            )
        }
    }
}