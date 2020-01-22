package com.geeksville.meshutil

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.ParcelUuid
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.JobIntentService
import java.io.InputStream
import java.util.*


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
class SoftwareUpdateService : JobIntentService() {

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter!!
    }

    lateinit var updateGatt: BluetoothGatt // the gatt api used to talk to our device
    lateinit var updateService: BluetoothGattService // The service we are currently talking to to do the update
    lateinit var totalSizeDesc: BluetoothGattCharacteristic
    lateinit var dataDesc: BluetoothGattCharacteristic
    lateinit var firmwareStream: InputStream

    fun startUpdate() {
            totalSizeDesc = updateService.getCharacteristic(SW_UPDATE_TOTALSIZE_CHARACTER)!!

            firmwareStream = assets.open("firmware.bin")

            // Start the update by writing the # of bytes in the image
            val numBytes = firmwareStream.available()
            assert(totalSizeDesc.setValue(numBytes, BluetoothGattCharacteristic.FORMAT_UINT32, 0))
            assert(updateGatt.writeCharacteristic(totalSizeDesc))
            assert(updateGatt.readCharacteristic(totalSizeDesc))
    }

    // Send the next block of our file to the device
    fun sendNextBlock() {
        if(firmwareStream.available() > 0) {
            var blockSize = 512

            if (blockSize > firmwareStream.available())
                blockSize = firmwareStream.available()
            val buffer = ByteArray(blockSize)

            // slightly expensive to keep reallocing this buffer, but whatever
            assert(firmwareStream.read(buffer) == blockSize)

            dataDesc = updateService.getCharacteristic(SW_UPDATE_DATA_CHARACTER)!!
            // updateGatt.beginReliableWrite()
            dataDesc.value = buffer
            assert(updateGatt.writeCharacteristic(dataDesc))
        }
        else {
            assert(false) // fixme
        }
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

            // We don't need any more results now
            bluetoothAdapter.bluetoothLeScanner.stopScan(this)

            lateinit var bluetoothGatt: BluetoothGatt // late init so we can declare our callback and use this there

            //var connectionState = STATE_DISCONNECTED

            // Various callback methods defined by the BLE API.
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    //val intentAction: String
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            //intentAction = ACTION_GATT_CONNECTED
                            //connectionState = STATE_CONNECTED
                            // broadcastUpdate(intentAction)
                            assert(bluetoothGatt.discoverServices())
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
                    assert(status == BluetoothGatt.GATT_SUCCESS)

                    // broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                    val service = gatt.services.find { it.uuid == SW_UPDATE_UUID }
                    if (service != null) {
                        // FIXME instead of slamming in the target device here, instead make it a param for startUpdate
                        updateService = service
                        // FIXME instead of keeping the connection open, make start update just reconnect (needed once user can choose devices)
                        updateGatt = bluetoothGatt
                        enqueueWork(this@SoftwareUpdateService, startUpdateIntent)
                    }
                }

                // Result of a characteristic read operation
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    assert(status == BluetoothGatt.GATT_SUCCESS)

                    if (characteristic == totalSizeDesc) {
                        // Our read of this has completed, either fail or continue updating
                        val readvalue =
                            characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                        assert(readvalue != 0) // FIXME - handle this case
                        enqueueWork(this@SoftwareUpdateService, sendNextBlockIntent)
                    }

                    // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    assert(status == BluetoothGatt.GATT_SUCCESS)

                    if (characteristic == dataDesc) {
                        enqueueWork(this@SoftwareUpdateService, sendNextBlockIntent)
                    }
                }
            }
            bluetoothGatt = result.device.connectGatt(this@SoftwareUpdateService, false, gattCallback)!!
            toast("FISH " + bluetoothGatt)
            assert(bluetoothGatt.discoverServices())
        }
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
                val settings = ScanSettings.Builder().
                    setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).
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
        Log.i("SimpleJobIntentService", "Executing work: $intent")
        var label = intent.getStringExtra("label")
        if (label == null) {
            label = intent.toString()
        }
        toast("Executing: $label")

        when(intent.action) {
            scanDevicesIntent.action -> scanLeDevice(true)
            startUpdateIntent.action -> startUpdate()
            sendNextBlockIntent.action -> sendNextBlock()
            else -> assert(false)
        }

        Log.i(
            "SimpleJobIntentService",
            "Completed service @ " + SystemClock.elapsedRealtime()
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        toast("All work complete")
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

        val scanDevicesIntent = Intent("com.geeksville.meshutil.SCAN_DEVICES")
        val startUpdateIntent = Intent("com.geeksville.meshutil.START_UPDATE")
        private val sendNextBlockIntent = Intent("com.geeksville.meshutil.SEND_NEXT_BLOCK")
        private val finishUpdateIntent = Intent("com.geeksville.meshutil.FINISH_UPDATE")

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