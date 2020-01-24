package com.geeksville.mesh

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.ParcelUuid
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.JobIntentService
import com.geeksville.android.Logging
import java.util.*
import java.util.zip.CRC32

/**
 * typical flow
 *
 * startScan
 * startUpdate
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

    lateinit var device: BluetoothDevice

    fun startUpdate() {
        info("starting update")

        val sync = SyncBluetoothDevice(this@SoftwareUpdateService, device)

        val firmwareStream = assets.open("firmware.bin")
        val firmwareCrc = CRC32()
        var firmwareNumSent = 0
        val firmwareSize = firmwareStream.available()

        sync.connect()
        sync.discoverServices() // Get our services

        val service = sync.gatt.services.find { it.uuid == SW_UPDATE_UUID }!!

        val totalSizeDesc = service.getCharacteristic(SW_UPDATE_TOTALSIZE_CHARACTER)
        val dataDesc = service.getCharacteristic(SW_UPDATE_DATA_CHARACTER)
        val crc32Desc = service.getCharacteristic(SW_UPDATE_CRC32_CHARACTER)
        val updateResultDesc = service.getCharacteristic(SW_UPDATE_RESULT_CHARACTER)

        // we begin by setting our MTU size as high as it can go
        sync.requestMtu(512)

        // Start the update by writing the # of bytes in the image
        logAssert(
            totalSizeDesc.setValue(
                firmwareSize,
                BluetoothGattCharacteristic.FORMAT_UINT32,
                0
            )
        )
        sync.writeCharacteristic(totalSizeDesc)

// Our write completed, queue up a readback
        val totalSizeReadback = sync.readCharacteristic(totalSizeDesc)
            .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
        if (totalSizeReadback == 0) // FIXME - handle this case
            throw Exception("Device rejected file size")

        // Send all the blocks
        while (firmwareNumSent < firmwareSize) {
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
            sync.writeCharacteristic(dataDesc)
            firmwareNumSent += blockSize
        }

        // We have finished sending all our blocks, so post the CRC so our state machine can advance
        val c = firmwareCrc.value
        info("Sent all blocks, crc is $c")
        logAssert(crc32Desc.setValue(c.toInt(), BluetoothGattCharacteristic.FORMAT_UINT32, 0))
        sync.writeCharacteristic(crc32Desc)

        // we just read the update result if !0 we have an error
        val updateResult =
            sync.readCharacteristic(updateResultDesc)
                .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
        if (updateResult != 0) // FIXME - handle this case
            throw Exception("Device update failed, reason=$updateResult")

        // FIXME perhaps ask device to reboot
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

            device = result.device
        }
    }

    // Until my race condition with scanning is fixed
    fun connectToTestDevice() {
        device = bluetoothAdapter.getRemoteDevice("B4:E6:2D:EA:32:B7")
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
            scanDevicesIntent.action -> scanLeDevice(true)
            startUpdateIntent.action -> {
                connectToTestDevice() // FIXME, pass in as an intent arg instead
                startUpdate()
            }
            else -> logAssert(false)
        }

        debug(
            "Completed service @ " + SystemClock.elapsedRealtime()
        )
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

        private const val SCAN_PERIOD: Long = 10000

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