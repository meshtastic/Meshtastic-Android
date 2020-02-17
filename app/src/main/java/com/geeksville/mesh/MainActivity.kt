package com.geeksville.mesh

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.ui.core.setContent
import com.geeksville.android.Logging
import com.geeksville.mesh.service.*
import com.geeksville.mesh.ui.MeshApp
import com.geeksville.mesh.ui.TextMessage
import com.geeksville.mesh.ui.UIState
import com.geeksville.mesh.ui.getInitials
import com.geeksville.util.exceptionReporter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import java.nio.charset.Charset
import java.util.*


/*
UI design

material setup instructions: https://material.io/develop/android/docs/getting-started/
dark theme (or use system eventually) https://material.io/develop/android/theming/dark/

NavDrawer is a standard draw which can be dragged in from the left or the menu icon inside the app
title.

Fragments:

SettingsFragment shows "Settings"
  username
  shortname
  bluetooth pairing list
  (eventually misc device settings that are not channel related)

Channel fragment "Channel"
  qr code, copy link button
  ch number
  misc other settings
  (eventually a way of choosing between past channels)

ChatFragment "Messages"
  a text box to enter new texts
  a scrolling list of rows.  each row is a text and a sender info layout

NodeListFragment "Users"
  a node info row for every node

ViewModels:

  BTScanModel starts/stops bt scan and provides list of devices (manages entire scan lifecycle)

  MeshModel contains: (manages entire service relationship)
  current received texts
  current radio macaddr
  current node infos (updated dynamically)

eventually use bottom navigation bar to switch between, Members, Chat, Channel, Settings. https://material.io/develop/android/components/bottom-navigation-view/
  use numbers of # chat messages and # of members in the badges.

(per this recommendation to not use top tabs: https://ux.stackexchange.com/questions/102439/android-ux-when-to-use-bottom-navigation-and-when-to-use-tabs )


eventually:
  make a custom theme: https://github.com/material-components/material-components-android/tree/master/material-theme-builder
*/

class MainActivity : AppCompatActivity(), Logging,
    ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        const val REQUEST_ENABLE_BT = 10
        const val DID_REQUEST_PERM = 11
        const val RC_SIGN_IN = 12 // google signin completed
    }


    private val utf8 = Charset.forName("UTF-8")

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun requestPermission() {
        debug("Checking permissions")

        val perms = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        if (Build.VERSION.SDK_INT >= 29) // only added later
            perms.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

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


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    private fun setOwner() {
        // Note: we are careful to not set a new unique ID
        val name = UIState.ownerName.value
        meshService!!.setOwner(null, name, getInitials(name))
    }

    private fun sendTestPackets() {
        exceptionReporter {
            val m = meshService!!

            // Do some test operations
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MeshApp()
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter != null) {
            bluetoothAdapter!!.takeIf { !it.isEnabled }?.apply {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            Toast.makeText(this, "Error - this app requires bluetooth", Toast.LENGTH_LONG)
                .show()
        }

        requestPermission()

        // Configure sign-in to request the user's ID, email address, and basic
// profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        // Configure sign-in to request the user's ID, email address, and basic
// profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso =
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()

        // Build a GoogleSignInClient with the options specified by gso.
        UIState.googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    override fun onDestroy() {
        unregisterMeshReceiver()
        super.onDestroy()
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode === RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account =
                completedTask.getResult(ApiException::class.java)
            // Signed in successfully, show authenticated UI.
            //updateUI(account)
        } catch (e: ApiException) { // The ApiException status code indicates the detailed failure reason.
// Please refer to the GoogleSignInStatusCodes class reference for more information.
            warn("signInResult:failed code=" + e.statusCode)
            //updateUI(null)
        }
    }

    private var receiverRegistered = false

    private fun registerMeshReceiver() {
        logAssert(!receiverRegistered)
        val filter = IntentFilter()
        filter.addAction(MeshService.ACTION_MESH_CONNECTED)
        filter.addAction(MeshService.ACTION_NODE_CHANGE)
        filter.addAction(MeshService.ACTION_RECEIVED_DATA)
        registerReceiver(meshServiceReceiver, filter)

    }

    private fun unregisterMeshReceiver() {
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(meshServiceReceiver)
        }
    }

    /// Read the config bytes from the radio so we can show them in our GUI, the radio's copy is ground truth
    private fun readRadioConfig() {
        val bytes = meshService!!.radioConfig

        val config = MeshProtos.RadioConfig.parseFrom(bytes)
        UIState.radioConfig.value = config

        debug("Read config from radio, channel URL ${UIState.channelUrl}")
    }

    /// Called when we gain/lose a connection to our mesh radio
    private fun onMeshConnectionChanged(connected: Boolean) {
        UIState.isConnected.value = connected
        debug("connchange ${UIState.isConnected.value}")
        if (connected) {
            // always get the current radio config when we connect
            readRadioConfig()

            // everytime the radio reconnects, we slam in our current owner data, the radio is smart enough to only broadcast if needed
            setOwner()
        }
    }

    private val meshServiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) = exceptionReporter {
            debug("Received from mesh service $intent")

            when (intent.action) {
                MeshService.ACTION_NODE_CHANGE -> {
                    val info: NodeInfo = intent.getParcelableExtra(EXTRA_NODEINFO)!!
                    debug("UI nodechange $info")

                    // We only care about nodes that have user info
                    info.user?.id?.let {
                        val newnodes = UIState.nodes.value.toMutableMap()
                        newnodes[it] = info
                        UIState.nodes.value = newnodes
                    }
                }

                MeshService.ACTION_RECEIVED_DATA -> {
                    debug("TODO rxdata")
                    val sender = intent.getStringExtra(EXTRA_SENDER)!!
                    val payload = intent.getByteArrayExtra(EXTRA_PAYLOAD)!!
                    val typ = intent.getIntExtra(EXTRA_TYP, -1)

                    when (typ) {
                        MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                            // FIXME - use the real time from the packet
                            val modded = UIState.messages.value.toMutableList()
                            modded.add(TextMessage(Date(), sender, payload.toString(utf8)))
                            UIState.messages.value = modded
                        }
                        else -> TODO()
                    }
                }
                MeshService.ACTION_MESH_CONNECTED -> {
                    val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
                    onMeshConnectionChanged(connected)
                }
                else -> TODO()
            }
        }
    }

    private var meshService: IMeshService? = null
    private var isBound = false

    private var serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) =
            exceptionReporter {
                val m = IMeshService.Stub.asInterface(service)
                meshService = m

                // We don't start listening for packets until after we are connected to the service
                registerMeshReceiver()

                // We won't receive a notify for the initial state of connection, so we force an update here
                onMeshConnectionChanged(m.isConnected)

                debug("connected to mesh service, isConnected=${UIState.isConnected.value}")

                // make some placeholder nodeinfos
                UIState.nodes.value =
                    m.nodes.toList().map {
                        it.user?.id!! to it
                    }.toMap()
            }

        override fun onServiceDisconnected(name: ComponentName) {
            warn("The mesh service has disconnected")
            unregisterMeshReceiver()
            meshService = null
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        logAssert(meshService == null)

        val intent = MeshService.startService(this)
        if (intent != null) {
            // ALSO bind so we can use the api
            logAssert(bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE))
            isBound = true;
        }
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
        unregisterMeshReceiver() // No point in receiving updates while the GUI is gone, we'll get them when the user launches the activity
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


