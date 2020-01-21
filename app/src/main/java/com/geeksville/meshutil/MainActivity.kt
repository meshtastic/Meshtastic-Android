package com.geeksville.meshutil

import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_ENABLE_BT = 10

        private const val SCAN_PERIOD: Long = 10000

        const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        private val TAG = MainActivity::class.java.simpleName // FIXME - use my logging class instead

        private val SW_UPDATE_UUID = UUID.fromString("cb0b9a0b-a84c-4c0d-bdbb-442e3144ee30")

        private val SW_UPDATE_TOTALSIZE_CHARACTER = UUID.fromString("e74dd9c0-a301-4a6f-95a1-f0e1dbea8e1e") // write|read          total image size, 32 bit, write this first, then read read back to see if it was acceptable (0 mean not accepted)
        private val SW_UPDATE_DATA_CHARACTER = UUID.fromString("e272ebac-d463-4b98-bc84-5cc1a39ee517") //  write               data, variable sized, recommended 512 bytes, write one for each block of file
        private val SW_UPDATE_CRC32_CHARACTER = UUID.fromString("4826129c-c22a-43a3-b066-ce8f0d5bacc6") //  write               crc32, write last - writing this will complete the OTA operation, now you can read result
        private val SW_UPDATE_RESULT_CHARACTER = UUID.fromString("5e134862-7411-4424-ac4a-210937432c77") // read|notify         result code, readable but will notify when the OTA operation completes
    }

    private val BluetoothAdapter.isDisabled: Boolean
        get() = !isEnabled

    private val bluetoothAdapter: BluetoothAdapter by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter!!
    }

    private var mScanning: Boolean = false
    private val handler = Handler()

    private val leScanCallback = BluetoothAdapter.LeScanCallback { device, _, _ ->
        runOnUiThread {
            /*
            leDeviceListAdapter.addDevice(device)
            leDeviceListAdapter.notifyDataSetChanged()
             */

            lateinit var bluetoothGatt: BluetoothGatt

            //var connectionState = STATE_DISCONNECTED

            lateinit var totalSizeDesc: BluetoothGattCharacteristic

            // Send the next block of our file to the device
            fun sendNextBlock() {

            }

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
                            Log.i(TAG, "Connected to GATT server.")
                            Log.i(TAG, "Attempting to start service discovery: " +
                                    bluetoothGatt.discoverServices())
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            //intentAction = ACTION_GATT_DISCONNECTED
                            //connectionState = STATE_DISCONNECTED
                            Log.i(TAG, "Disconnected from GATT server.")
                            // broadcastUpdate(intentAction)
                        }
                    }
                }

                // New services discovered
                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    when (status) {
                        BluetoothGatt.GATT_SUCCESS -> {
                            // broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)

                            val updateService = gatt.services.find { it.uuid == SW_UPDATE_UUID }
                            if(updateService != null) {

                                // Start the update by writing the # of bytes in the image
                                val numBytes = 45
                                totalSizeDesc = updateService.getCharacteristic(SW_UPDATE_TOTALSIZE_CHARACTER)!!
                                assert(totalSizeDesc.setValue(numBytes, BluetoothGattCharacteristic.FORMAT_UINT32, 0))
                                assert(bluetoothGatt.writeCharacteristic(totalSizeDesc))
                                assert(bluetoothGatt.readCharacteristic(totalSizeDesc))
                            }
                        }
                        else -> Log.w(TAG, "onServicesDiscovered received: $status")
                    }
                }

                // Result of a characteristic read operation
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    assert(status == BluetoothGatt.GATT_SUCCESS)

                    if(characteristic == totalSizeDesc) {
                        // Our read of this has completed, either fail or continue updating
                        val readvalue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                        assert(readvalue != 0) // FIXME - handle this case
                        sendNextBlock() // FIXME, call this in a job queue of the service
                    }

                    // broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
                }
            }
            bluetoothGatt = device.connectGatt(this, false, gattCallback)!!
        }
    }

    private fun scanLeDevice(enable: Boolean) {
        when (enable) {
            true -> {
                // Stops scanning after a pre-defined scan period.
                handler.postDelayed({
                    mScanning = false
                    bluetoothAdapter.stopLeScan(leScanCallback)
                }, SCAN_PERIOD)
                mScanning = true
                bluetoothAdapter.startLeScan(leScanCallback)
            }
            else -> {
                mScanning = false
                bluetoothAdapter.stopLeScan(leScanCallback)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { view ->
            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show()
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        bluetoothAdapter.takeIf { it.isDisabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }
}

