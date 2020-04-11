package com.geeksville.mesh

// import kotlinx.android.synthetic.main.tabs.*
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.geeksville.android.Logging
import com.geeksville.android.ServiceClient
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.TextMessage
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.*
import com.geeksville.mesh.ui.*
import com.geeksville.util.Exceptions
import com.geeksville.util.exceptionReporter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.Task
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.vorlonsoft.android.rate.AppRate
import com.vorlonsoft.android.rate.StoreType
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.charset.Charset

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

val utf8 = Charset.forName("UTF-8")


class MainActivity : AppCompatActivity(), Logging,
    ActivityCompat.OnRequestPermissionsResultCallback {

    companion object {
        const val REQUEST_ENABLE_BT = 10
        const val DID_REQUEST_PERM = 11
        const val RC_SIGN_IN = 12 // google signin completed
    }


    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val model: UIViewModel by viewModels()

    data class TabInfo(val text: String, val icon: Int, val content: Fragment)

    // private val tabIndexes = generateSequence(0) { it + 1 } FIXME, instead do withIndex or zip? to get the ids below, also stop duplicating strings
    private val tabInfos = arrayOf(
        TabInfo(
            "Messages",
            R.drawable.ic_twotone_message_24,
            MessagesFragment()
        ),
        TabInfo(
            "Users",
            R.drawable.ic_twotone_people_24,
            UsersFragment()
        ),
        TabInfo(
            "Map",
            R.drawable.ic_twotone_map_24,
            MapFragment()
        ),
        TabInfo(
            "Channel",
            R.drawable.ic_twotone_contactless_24,
            ChannelFragment()
        ),
        TabInfo(
            "Settings",
            R.drawable.ic_twotone_settings_applications_24,
            SettingsFragment()
        )
    )

    private
    val tabsAdapter = object : FragmentStateAdapter(this) {

        override fun getItemCount(): Int = tabInfos.size

        override fun createFragment(position: Int): Fragment {
            // Return a NEW fragment instance in createFragment(int)
            /*
            fragment.arguments = Bundle().apply {
                // Our object is just an integer :-P
                putInt(ARG_OBJECT, position + 1)
            } */
            return tabInfos[position].content
        }
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
            ActivityCompat.requestPermissions(
                this,
                missingPerms.toTypedArray(),
                DID_REQUEST_PERM
            )

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


    private fun sendTestPackets() {
        exceptionReporter {
            val m = model.meshService!!

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


    /// Ask user to rate in play store
    private fun askToRate() {
        AppRate.with(this)
            .setInstallDays(10.toByte()) // default is 10, 0 means install day, 10 means app is launched 10 or more days later than installation
            .setLaunchTimes(10.toByte()) // default is 10, 3 means app is launched 3 or more times
            .setRemindInterval(1.toByte()) // default is 1, 1 means app is launched 1 or more days after neutral button clicked
            .setRemindLaunchesNumber(1.toByte()) // default is 0, 1 means app is launched 1 or more times after neutral button clicked
            .monitor() // Monitors the app launch times

        // Only ask to rate if the user has a suitable store
        if (AppRate.with(this).storeType == StoreType.GOOGLEPLAY) { // Checks that current app store type from library options is StoreType.GOOGLEPLAY
            if (GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this) != ConnectionResult.SERVICE_MISSING
            ) { // Checks that Google Play is available
                AppRate.showRateDialogIfMeetsConditions(this) // Shows the Rate Dialog when conditions are met

                // Force the dialog - for testing
                // AppRate.with(this).showRateDialog(this)
            }
        } else {
            AppRate.showRateDialogIfMeetsConditions(this);     // Shows the Rate Dialog when conditions are met
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = UIViewModel.getPreferences(this)
        model.ownerName.value = prefs.getString("owner", "")!!

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter != null) {
            bluetoothAdapter!!.takeIf { !it.isEnabled }?.apply {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        } else {
            Toast.makeText(
                this,
                R.string.error_bluetooth,
                Toast.LENGTH_LONG
            )
                .show()
        }

        requestPermission()


        /*  not yet working
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

         */

        // Handle any intent
        handleIntent(intent)

        /* setContent {
            MeshApp()
        } */
        setContentView(R.layout.activity_main)

        pager.adapter = tabsAdapter
        pager.isUserInputEnabled =
            false // Gestures for screen switching doesn't work so good with the map view
        // pager.offscreenPageLimit = 0 // Don't keep any offscreen pages around, because we want to make sure our bluetooth scanning stops
        TabLayoutMediator(tab_layout, pager) { tab, position ->
            // tab.text = tabInfos[position].text // I think it looks better with icons only
            tab.icon = getDrawable(tabInfos[position].icon)
        }.attach()

        model.isConnected.observe(this, Observer { connected ->
            val image = when (connected) {
                MeshService.ConnectionState.CONNECTED -> R.drawable.cloud_on
                MeshService.ConnectionState.DEVICE_SLEEP -> R.drawable.ic_twotone_cloud_upload_24
                MeshService.ConnectionState.DISCONNECTED -> R.drawable.cloud_off
            }

            connectStatusImage.setImageDrawable(getDrawable(image))
        })

        askToRate()
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private var requestedChannelUrl: Uri? = null

    /// Handle any itents that were passed into us
    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        // Were we asked to open one our channel URLs?
        if (Intent.ACTION_VIEW == appLinkAction) {
            debug("Asked to open a channel URL - ask user if they want to switch to that channel.  If so send the config to the radio")
            requestedChannelUrl = appLinkData

            // if the device is connected already, process it now
            if (model.isConnected == MeshService.ConnectionState.CONNECTED)
                perhapsChangeChannel()

            // We now wait for the device to connect, once connected, we ask the user if they want to switch to the new channel
        }
    }

    override fun onDestroy() {
        unregisterMeshReceiver()
        super.onDestroy()
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> =
                GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }

    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        /*
        try {
            val account = completedTask.getResult(ApiException::class.java)
            // Signed in successfully, show authenticated UI.
            //updateUI(account)
        } catch (e: ApiException) { // The ApiException status code indicates the detailed failure reason.
// Please refer to the GoogleSignInStatusCodes class reference for more information.
            warn("signInResult:failed code=" + e.statusCode)
            //updateUI(null)
        } */
    }

    private
    var receiverRegistered = false

    private fun registerMeshReceiver() {
        unregisterMeshReceiver()
        val filter = IntentFilter()
        filter.addAction(MeshService.ACTION_MESH_CONNECTED)
        filter.addAction(MeshService.ACTION_NODE_CHANGE)
        filter.addAction(MeshService.ACTION_RECEIVED_DATA)
        registerReceiver(meshServiceReceiver, filter)
        receiverRegistered = true;
    }

    private fun unregisterMeshReceiver() {
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(meshServiceReceiver)
        }
    }


    /// Called when we gain/lose a connection to our mesh radio
    private fun onMeshConnectionChanged(connected: MeshService.ConnectionState) {
        model.isConnected.value = connected
        debug("connchange ${model.isConnected.value}")
        if (connected == MeshService.ConnectionState.CONNECTED) {

            // everytime the radio reconnects, we slam in our current owner data, the radio is smart enough to only broadcast if needed
            model.setOwner()

            model.meshService?.let { service ->
                debug("Getting latest radioconfig from service")
                model.radioConfig.value =
                    MeshProtos.RadioConfig.parseFrom(model.meshService!!.radioConfig)

                // Pull down our real node ID
                model.nodeDB.myId.value = service.myId

                // Update our nodeinfos based on data from the device
                val nodes = service.nodes.map {
                    it.user?.id!! to it
                }.toMap()

                model.nodeDB.nodes.value = nodes

                // we have a connection to our device now, do the channel change
                perhapsChangeChannel()
            }
        }
    }

    private fun perhapsChangeChannel() {
        // If the is opening a channel URL, handle it now
        requestedChannelUrl?.let { url ->
            val channel = Channel(url)
            requestedChannelUrl = null

            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.new_channel_rcvd)
                .setMessage(getString(R.string.do_you_want_switch).format(channel.name))
                .setNeutralButton(R.string.cancel) { _, _ ->
                    // Do nothing
                }
                .setPositiveButton(R.string.accept) { _, _ ->
                    debug("Setting channel from URL")
                    model.setChannel(channel.settings)
                }
                .show()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (ex: Throwable) {
            Exceptions.report(
                ex,
                "dispatchTouchEvent"
            ) // hide this Compose error from the user but report to the mothership
            false
        }
    }

    private
    val meshServiceReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) =
            exceptionReporter {
                debug("Received from mesh service $intent")

                when (intent.action) {
                    MeshService.ACTION_NODE_CHANGE -> {
                        val info: NodeInfo =
                            intent.getParcelableExtra(EXTRA_NODEINFO)!!
                        debug("UI nodechange $info")

                        // We only care about nodes that have user info
                        info.user?.id?.let {
                            val newnodes = model.nodeDB.nodes.value!! + Pair(it, info)
                            model.nodeDB.nodes.value = newnodes
                        }
                    }

                    MeshService.ACTION_RECEIVED_DATA -> {
                        debug("TODO rxdata")
                        val sender =
                            intent.getStringExtra(EXTRA_SENDER)!!
                        val payload =
                            intent.getByteArrayExtra(EXTRA_PAYLOAD)!!
                        val typ = intent.getIntExtra(EXTRA_TYP, -1)

                        when (typ) {
                            MeshProtos.Data.Type.CLEAR_TEXT_VALUE -> {
                                // FIXME - use the real time from the packet
                                // FIXME - don't just slam in a new list each time, it probably causes extra drawing.  Figure out how to be Compose smarter...
                                val msg = TextMessage(
                                    sender,
                                    payload.toString(utf8)
                                )

                                model.messagesState.addMessage(msg)
                            }
                            else -> TODO()
                        }
                    }
                    MeshService.ACTION_MESH_CONNECTED -> {
                        val connected =
                            MeshService.ConnectionState.valueOf(
                                intent.getStringExtra(
                                    EXTRA_CONNECTED
                                )!!
                            )
                        onMeshConnectionChanged(connected)
                    }
                    else -> TODO()
                }
            }
    }


    private
    val mesh = object :
        ServiceClient<com.geeksville.mesh.IMeshService>({
            com.geeksville.mesh.IMeshService.Stub.asInterface(it)
        }) {
        override fun onConnected(service: com.geeksville.mesh.IMeshService) = exceptionReporter {
            model.meshService = service

            // We don't start listening for packets until after we are connected to the service
            registerMeshReceiver()

            // We won't receive a notify for the initial state of connection, so we force an update here
            val connectionState =
                MeshService.ConnectionState.valueOf(service.connectionState())
            onMeshConnectionChanged(connectionState)

            debug("connected to mesh service, isConnected=${model.isConnected.value}")
        }

        override fun onDisconnected() {
            unregisterMeshReceiver()
            model.meshService = null
        }
    }

    fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        if (model.meshService != null)
            Exceptions.reportError("meshService was supposed to be null, ignoring (but reporting a bug)")

        MeshService.startService(this)?.let { intent ->
            // ALSO bind so we can use the api
            mesh.connect(this, intent, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindMeshService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        // if we never connected, do nothing
        debug("Unbinding from mesh service!")
        mesh.close()
        model.meshService = null
    }

    override fun onStop() {
        unregisterMeshReceiver() // No point in receiving updates while the GUI is gone, we'll get them when the user launches the activity
        unbindMeshService()

        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        bindMeshService()

        val bonded =
            RadioInterfaceService.getBondedDeviceAddress(this) != null
        if (!bonded)
            pager.currentItem = 5
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


