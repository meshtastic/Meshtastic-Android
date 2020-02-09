package com.geeksville.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Debug
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
import com.geeksville.util.exceptionReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.nio.charset.Charset
import java.util.*


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
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
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

    private fun sendTestPackets() {
        exceptionReporter {
            val m = meshService!!

            // Do some test operations
            m.setOwner("+16508675309", "Kevin Xter", "kx")
            val testPayload = "hello world".toByteArray()
            m.sendData(
                "+16508675310",
                testPayload,
                MeshProtos.Data.Type.SIGNAL_OPAQUE_VALUE
            )
            m.sendData(
                "+16508675310",
                testPayload,
                MeshProtos.Data.Type.CLEAR_TEXT_VALUE
            )
        }
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

                Button(text = "send packets",
                    onClick = { sendTestPackets() })
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // We default to off in the manifest, FIXME turn on only if user approves
        // leave off when running in the debugger
        if (false && !Debug.isDebuggerConnected())
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(true)

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

        val filter = IntentFilter()
        filter.addAction("")
        registerReceiver(meshServiceReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(meshServiceReceiver)
        super.onDestroy()
    }

    /// A map from nodeid to to nodeinfo
    private val nodes = mutableMapOf<String, NodeInfo>()

    data class TextMessage(val date: Date, val from: String, val text: String)

    private val messages = mutableListOf<TextMessage>()

    /// Are we connected to our radio device
    private var isConnected = false

    private val utf8 = Charset.forName("UTF-8")

    private val meshServiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            debug("Received from mesh service $intent")

            when (intent.action) {
                MeshService.ACTION_NODE_CHANGE -> {
                    warn("TODO nodechange")
                    val info: NodeInfo = intent.getParcelableExtra(EXTRA_NODEINFO)!!

                    // We only care about nodes that have user info
                    info.user?.id?.let {
                        nodes[it] = info
                    }
                }
                MeshService.ACTION_RECEIVED_DATA -> {
                    warn("TODO rxopaqe")
                    val sender = intent.getStringExtra(EXTRA_SENDER)!!
                    val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)!!
                    val typ = intent.getIntExtra(EXTRA_TYP, -1)!!

                    when (typ) {
                        MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                            // FIXME - use the real time from the packet
                            messages.add(TextMessage(Date(), sender, payload.toString(utf8)))
                        }
                        else -> TODO()
                    }
                }
                RadioInterfaceService.CONNECTCHANGED_ACTION -> {
                    isConnected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                    debug("connchange $isConnected")
                }
                else -> TODO()
            }
        }
    }

    private var meshService: IMeshService? = null
    private var isBound = false

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) = exceptionReporter {
            val m = IMeshService.Stub.asInterface(service)
            meshService = m

            // FIXME - do actions for when we connect to the service
            // FIXME - do actions for when we connect to the service
            debug("did connect")

            // FIXME: this still can't work this early because the send to +6508675310
            // requires a DB lookup which isn't yet populated (until the sim test packets
            // from the radio arrive)
            // sendTestPackets() // send some traffic ASAP

            // FIXME this doesn't work because the model has already been copied into compose land?
            // runOnUiThread { // FIXME - this can be removed?
            meshServiceState.connected = m.isConnected
            meshServiceState.onlineIds = m.online
            // }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            meshService = null
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        logAssert(meshService == null)

        // bind to our service using the same mechanism an external client would use (for testing coverage)
        // The following would work for us, but not external users
        //val intent = Intent(this, MeshService::class.java)
        //intent.action = IMeshService::class.java.name
        val intent = Intent()
        intent.setClassName("com.geeksville.mesh", "com.geeksville.mesh.MeshService")

        // Before binding we want to explicitly create - so the service stays alive forever (so it can keep
        // listening for the bluetooth packets arriving from the radio.  And when they arrive forward them
        // to Signal or whatever.

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        // ALSO bind so we can use the api
        logAssert(bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE))
        isBound = true;
    }

    private fun unbindMeshService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        // if we never connected, do nothing
        debug("Unbinding from mesh service!")
        if (isBound)
            unbindService(serviceConnection)
        meshService = null
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

