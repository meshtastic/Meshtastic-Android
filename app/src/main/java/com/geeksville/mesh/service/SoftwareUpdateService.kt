package com.geeksville.mesh.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.geeksville.android.Logging
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.util.exceptionReporter
import java.util.*
import java.util.zip.CRC32

/**
 * Move this somewhere as a generic network byte order function
 */
fun toNetworkByteArray(value: Int, formatType: Int): ByteArray {

    val len: Int = 4 // getTypeLen(formatType)
    val mValue = ByteArray(len)

    when (formatType) {
        /* BluetoothGattCharacteristic.FORMAT_SINT8 -> {
            value = intToSignedBits(value, 8)
            mValue.get(offset) = (value and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_UINT8 -> mValue.get(offset) =
            (value and 0xFF).toByte()
        BluetoothGattCharacteristic.FORMAT_SINT16 -> {
            value = intToSignedBits(value, 16)
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset) = (value shr 8 and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_UINT16 -> {
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset) = (value shr 8 and 0xFF).toByte()
        }
        BluetoothGattCharacteristic.FORMAT_SINT32 -> {
            value = intToSignedBits(value, 32)
            mValue.get(offset++) = (value and 0xFF).toByte()
            mValue.get(offset++) = (value shr 8 and 0xFF).toByte()
            mValue.get(offset++) = (value shr 16 and 0xFF).toByte()
            mValue.get(offset) = (value shr 24 and 0xFF).toByte()
        } */
        BluetoothGattCharacteristic.FORMAT_UINT32 -> {
            mValue[0] = (value and 0xFF).toByte()
            mValue[1] = (value shr 8 and 0xFF).toByte()
            mValue[2] = (value shr 16 and 0xFF).toByte()
            mValue[3] = (value shr 24 and 0xFF).toByte()
        }
        else -> TODO()
    }
    return mValue
}

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


    private fun startUpdate(macaddr: String) {
        info("starting update to $macaddr")

        val device = bluetoothAdapter.getRemoteDevice(macaddr)

        val sync =
            SafeBluetooth(
                this@SoftwareUpdateService,
                device
            )

        sync.connect()
        sync.use { _ ->
            // we begin by setting our MTU size as high as it can go
            sync.requestMtu(512)

            sync.discoverServices() // Get our services

            val updateFilename = getUpdateFilename(this, sync)
            if (updateFilename != null) {
                doUpdate(this, sync, updateFilename)
            } else
                warn("Device is already up-to-date no update needed.")
        }
    }


    override fun onHandleWork(intent: Intent) {
        // We have received work to do.  The system or framework is already
// holding a wake lock for us at this point, so we can just go.

        // Report failures but do not crash the app
        exceptionReporter {
            debug("Executing work: $intent")
            when (intent.action) {
                ACTION_START_UPDATE -> {
                    val addr = intent.getStringExtra(EXTRA_MACADDR)
                        ?: throw Exception("EXTRA_MACADDR not specified")
                    startUpdate(addr) // FIXME, pass in as an intent arg instead
                }
                else -> TODO("Unhandled case")
            }
        }
    }

    companion object : Logging {
        /**
         * Unique job ID for this service.  Must be the same for all work.
         */
        private const val JOB_ID = 1000

        fun startUpdateIntent(macAddress: String): Intent {
            val i = Intent(ACTION_START_UPDATE)
            i.putExtra(EXTRA_MACADDR, macAddress)

            return i
        }

        const val ACTION_START_UPDATE = "$prefix.START_UPDATE"

        const val EXTRA_MACADDR = "macaddr"

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

        private val SW_VERSION_CHARACTER = longBLEUUID("2a28")
        private val MANUFACTURE_CHARACTER = longBLEUUID("2a29")
        private val HW_VERSION_CHARACTER = longBLEUUID("2a27")

        /**
         * % progress through the update
         */
        var progress = 0

        /**
         * Convenience method for enqueuing work in to this service.
         */
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(
                context,
                SoftwareUpdateService::class.java,
                JOB_ID, work
            )
        }

        /**
         * Convert a version string of the form 1.23.57 to a comparable integer of
         * the form 12357.
         *
         * Or throw an exception if the string can not be parsed
         */
        fun verStringToInt(s: String): Int {
            // Allow 1 to two digits per match
            val match =
                Regex("(\\d{1,2}).(\\d{1,2}).(\\d{1,2})").find(s)
                    ?: throw Exception("Can't parse version $s")
            val (major, minor, build) = match.destructured
            return major.toInt() * 1000 + minor.toInt() * 100 + build.toInt()
        }

        /** Return true if we thing the firmwarte shoulde be updated
         *
         * @param swVer the version of the software running on the target
         */
        fun shouldUpdate(
            context: Context,
            swVer: String
        ): Boolean = try {
            val curVer = verStringToInt(context.getString(R.string.cur_firmware_version))
            val minVer =
                verStringToInt("0.7.8") // The oldest device version with a working software update service

            // If the user is running a development build we never do an automatic update
            val deviceVersion =
                verStringToInt(if (swVer.isEmpty() || swVer == "unset") "99.99.99" else swVer)

            (curVer > deviceVersion) && (deviceVersion >= minVer)
        } catch (ex: Exception) {
            errormsg("Error finding swupdate info", ex)
            false // If we fail parsing our update info
        }

        /** Return the filename this device needs to use as an update (or null if no update needed)
         */
        fun getUpdateFilename(
            context: Context,
            mfg: String
        ): String? {
            val curver = context.getString(R.string.cur_firmware_version)

            val base = "firmware-$mfg-$curver.bin"

            // Check to see if the file exists (some builds might not include update files for size reasons)
            val firmwareFiles = context.assets.list("firmware") ?: arrayOf()
            return if (firmwareFiles.contains(base))
                "firmware/$base"
            else
                null
        }

        /** Return the filename this device needs to use as an update (or null if no update needed)
         * No longer used, because we get update info inband from our radio API
         */
        fun getUpdateFilename(context: Context, sync: SafeBluetooth): String? {
            val service = sync.gatt!!.services.find { it.uuid == SW_UPDATE_UUID }!!

            //val hwVerDesc = service.getCharacteristic(HW_VERSION_CHARACTER)
            val mfgDesc = service.getCharacteristic(MANUFACTURE_CHARACTER)
            //val swVerDesc = service.getCharacteristic(SW_VERSION_CHARACTER)

            // looks like HELTEC
            val mfg = sync.readCharacteristic(mfgDesc).getStringValue(0)

            return getUpdateFilename(context, mfg)
        }

        /**
         * A public function so that if you have your own SafeBluetooth connection already open
         * you can use it for the software update.
         */
        fun doUpdate(context: Context, sync: SafeBluetooth, assetName: String) {
            try {
                val g = sync.gatt!!
                val service = g.services.find { it.uuid == SW_UPDATE_UUID }
                    ?: throw BLEException("Couldn't find update service")

                info("Starting firmware update for $assetName")

                progress = 0
                val totalSizeDesc = service.getCharacteristic(SW_UPDATE_TOTALSIZE_CHARACTER)
                val dataDesc = service.getCharacteristic(SW_UPDATE_DATA_CHARACTER)
                val crc32Desc = service.getCharacteristic(SW_UPDATE_CRC32_CHARACTER)
                val updateResultDesc = service.getCharacteristic(SW_UPDATE_RESULT_CHARACTER)

                context.assets.open(assetName).use { firmwareStream ->
                    val firmwareCrc = CRC32()
                    var firmwareNumSent = 0
                    val firmwareSize = firmwareStream.available()

                    // Start the update by writing the # of bytes in the image
                    sync.writeCharacteristic(
                        totalSizeDesc,
                        toNetworkByteArray(firmwareSize, BluetoothGattCharacteristic.FORMAT_UINT32)
                    )

                    // Our write completed, queue up a readback
                    val totalSizeReadback = sync.readCharacteristic(totalSizeDesc)
                        .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                    if (totalSizeReadback == 0) // FIXME - handle this case
                        throw Exception("Device rejected file size")

                    // Send all the blocks
                    while (firmwareNumSent < firmwareSize) {
                        progress = firmwareNumSent * 100 / firmwareSize
                        debug("sending block ${progress}%")
                        var blockSize = 512 - 3 // Max size MTU excluding framing

                        if (blockSize > firmwareStream.available())
                            blockSize = firmwareStream.available()
                        val buffer = ByteArray(blockSize)

                        // slightly expensive to keep reallocing this buffer, but whatever
                        logAssert(firmwareStream.read(buffer) == blockSize)
                        firmwareCrc.update(buffer)

                        sync.writeCharacteristic(dataDesc, buffer)
                        firmwareNumSent += blockSize
                    }

                    try {
                        // We have finished sending all our blocks, so post the CRC so our state machine can advance
                        val c = firmwareCrc.value
                        info("Sent all blocks, crc is $c")
                        sync.writeCharacteristic(
                            crc32Desc,
                            toNetworkByteArray(c.toInt(), BluetoothGattCharacteristic.FORMAT_UINT32)
                        )

                        // we just read the update result if !0 we have an error
                        val updateResult =
                            sync.readCharacteristic(updateResultDesc)
                                .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                        if (updateResult != 0) {
                            progress = -2
                            throw Exception("Device update failed, reason=$updateResult")
                        }

                        // Device will now reboot
                    } catch (ex: BLEException) {
                        // We might get SyncContinuation timeout on the final write, assume the device simply rebooted to run the new load and we missed it
                        errormsg("Assuming successful update", ex)
                    }

                    progress = -1 // success
                }
            } catch (ex: BLEException) {
                progress = -3
                throw ex // Unexpected BLE exception
            }
        }
    }
}
