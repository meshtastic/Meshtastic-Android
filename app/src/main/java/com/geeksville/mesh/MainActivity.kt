package com.geeksville.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.Composable
import androidx.compose.Model
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.core.setContent
import androidx.ui.layout.Column
import androidx.ui.layout.Spacing
import androidx.ui.material.Button
import androidx.ui.material.MaterialTheme
import androidx.ui.tooling.preview.Preview
import com.geeksville.android.Logging


class MainActivity : AppCompatActivity(), Logging {

    companion object {
        const val REQUEST_ENABLE_BT = 10
        const val DID_REQUEST_PERM = 11
    }

    @Model
    class MeshServiceState(
        var connected: Boolean = false,
        var onlineIds: Array<String> = arrayOf()
    )

    val meshServiceState = MeshServiceState()

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    fun requestPermission() {
        debug("Checking permissions")

        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WAKE_LOCK
        )

        val missingPerms = perms.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isNotEmpty()) {
            missingPerms.forEach {
                // Permission is not granted
                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, it)) {
                    // FIXME
                    // Show an explanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.
                }
            }

            // Ask for all the missing perms
            ActivityCompat.requestPermissions(this, missingPerms.toTypedArray(), DID_REQUEST_PERM)

            // DID_REQUEST_PERM is an
            // app-defined int constant. The callback method gets the
            // result of the request.
        } else {
            // Permission has already been granted
        }
    }

    @Preview
    @Composable
    fun previewView() {
        composeView(meshServiceState)
    }

    @Composable
    fun composeView(meshServiceState: MeshServiceState) {
        MaterialTheme {
            Column(modifier = Spacing(8.dp)) {
                Text(text = "Meshtastic", modifier = Spacing(8.dp))

                Text("Radio connected: ${meshServiceState.connected}")
                meshServiceState.onlineIds.forEach {
                    Text("User: $it")
                }

                Button(text = "Start scan",
                    onClick = {
                        if (bluetoothAdapter != null) {
                            // Note: We don't want this service to die just because our activity goes away (because it is doing a software update)
                            // So we use the application context instead of the activity
                            SoftwareUpdateService.enqueueWork(
                                applicationContext,
                                SoftwareUpdateService.startUpdateIntent
                            )
                        }
                    })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            composeView(meshServiceState)
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter != null) {
            bluetoothAdapter!!.takeIf { !it.isEnabled }?.apply {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            Toast.makeText(this, "Error - this app requires bluetooth", Toast.LENGTH_LONG).show()
        }

        requestPermission()
    }

    var meshService: IMeshService? = null
    var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            meshService = IMeshService.Stub.asInterface(service)

            // FIXME this doesn't work because the model has already been copied into compose land?
            runOnUiThread {
                // FIXME - this can be removed?
                meshServiceState.connected = meshService!!.isConnected
                meshServiceState.onlineIds = meshService!!.online
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            meshService = null
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        logAssert(meshService == null)
        // FIXME - finding by string does work
        val intent = Intent(this, MeshService::class.java)
        intent.action = IMeshService::class.java.name

        // This is the remote version that does not work! FIXME
        //val intent = Intent(IMeshService::class.java.name)
        //intent.setPackage("com.geeksville.mesh");
        isBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        logAssert(isBound)
    }

    private fun unbindMeshService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        // if we never connected, do nothing
        if (isBound) {
            debug("Unbinding from mesh service!")
            unbindService(serviceConnection)
            meshService = null
        }
    }

    override fun onPause() {
        unbindMeshService()

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        bindMeshService()
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

