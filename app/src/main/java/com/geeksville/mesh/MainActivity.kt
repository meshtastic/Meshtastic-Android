package com.geeksville.mesh

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.RemoteException
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.geeksville.android.BindFailedException
import com.geeksville.android.GeeksvilleApplication
import com.geeksville.android.Logging
import com.geeksville.android.ServiceClient
import com.geeksville.concurrent.handledLaunch
import com.geeksville.mesh.android.getLocationPermissions
import com.geeksville.mesh.android.getBackgroundPermissions
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.android.getMissingPermissions
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.ActivityMainBinding
import com.geeksville.mesh.model.ChannelSet
import com.geeksville.mesh.model.DeviceVersion
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
import com.google.protobuf.InvalidProtocolBufferException
import com.vorlonsoft.android.rate.AppRate
import com.vorlonsoft.android.rate.StoreType
import kotlinx.coroutines.*
import java.io.FileOutputStream
import java.lang.Runnable
import java.nio.charset.Charset
import java.text.DateFormat
import java.util.*
import kotlin.math.roundToInt


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
        const val RC_SELECT_DEVICE =
            13 // seems to be hardwired in CompanionDeviceManager to add 65536
        const val CREATE_CSV_FILE = 14
    }

    private lateinit var binding: ActivityMainBinding

    // Used to schedule a coroutine in the GUI thread
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    val model: UIViewModel by viewModels()

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

    private val tabsAdapter = object : FragmentStateAdapter(this) {

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


    private val btStateReceiver = BluetoothStateReceiver { _ ->
        updateBluetoothEnabled()
    }

    /**
     * Don't tell our app we have bluetooth until we have bluetooth _and_ location access
     */
    private fun updateBluetoothEnabled() {
        var enabled = false // assume failure

        val requiredPerms = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (getMissingPermissions(requiredPerms).isEmpty()) {
            /// ask the adapter if we have access
            bluetoothAdapter?.apply {
                enabled = isEnabled
            }
        } else
            errormsg("Still missing needed bluetooth permissions")

        debug("Detected our bluetooth access=$enabled")
        model.bluetoothEnabled.value = enabled
    }

    /** Get the minimum permissions our app needs to run correctly
     */
    private fun getMinimumPermissions(): List<String> {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WAKE_LOCK

            // We only need this for logging to capture files for the simulator - turn off for most users
            // Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // Some old phones complain about requesting perms they don't understand
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND)
            perms.add(Manifest.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND)
        }

        return getMissingPermissions(perms)
    }

    /** Ask the user to grant camera permission */
    fun requestCameraPermission() = requestPermission(getCameraPermissions(), false)

    /** Ask the user to grant foreground location permission */
    fun requestLocationPermission() = requestPermission(getLocationPermissions(), false)

    /** Ask the user to grant background location permission */
    fun requestBackgroundPermission() = requestPermission(getBackgroundPermissions(), false)

    /**
     * @return a localized string warning user about missing permissions.  Or null if everything is find
     */
    fun getMissingMessage(): String? {
        val renamedPermissions = mapOf(
            // Older versions of android don't know about these permissions - ignore failure to grant
            Manifest.permission.ACCESS_COARSE_LOCATION to null,
            Manifest.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND to null,
            Manifest.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND to null,
            Manifest.permission.ACCESS_FINE_LOCATION to getString(R.string.location)
        )

        val deniedPermissions = getMinimumPermissions().mapNotNull {
            if (renamedPermissions.containsKey(it))
                renamedPermissions[it]
            else // No localization found - just show the nasty android string
                it
        }

        return if (deniedPermissions.isEmpty())
            null
        else {
            val asEnglish = deniedPermissions.joinToString(" & ")

            getString(R.string.permission_missing).format(asEnglish)
        }
    }

    /** Possibly prompt user to grant permissions
     * @param shouldShowDialog usually true, but in cases where we've already shown a dialog elsewhere we skip it.
     *
     * @return true if we already have the needed permissions
     */
    private fun requestPermission(
        missingPerms: List<String> = getMinimumPermissions(),
        shouldShowDialog: Boolean = true
    ): Boolean =
        if (missingPerms.isNotEmpty()) {
            val shouldShow = missingPerms.filter {
                ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }

            fun doRequest() {
                info("requesting permissions")
                // Ask for all the missing perms
                ActivityCompat.requestPermissions(
                    this,
                    missingPerms.toTypedArray(),
                    DID_REQUEST_PERM
                )
            }

            if (shouldShow.isNotEmpty() && shouldShowDialog) {
                // DID_REQUEST_PERM is an
                // app-defined int constant. The callback method gets the
                // result of the request.
                warn("Permissions $shouldShow missing, we should show dialog")

                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.required_permissions))
                    .setMessage(getMissingMessage())
                    .setNeutralButton(R.string.cancel) { _, _ ->
                        warn("User bailed due to permissions")
                    }
                    .setPositiveButton(R.string.accept) { _, _ ->
                        doRequest()
                    }
                    .show()
            } else {
                info("Permissions $missingPerms missing, no need to show dialog, just asking OS")
                doRequest()
            }

            false
        } else {
            // Permission has already been granted
            debug("We have our required permissions")
            true
        }

    /**
     * Remind user he's disabled permissions we need
     *
     * @return true if we did warn
     */
    @SuppressLint("InlinedApi") // This function is careful to work with old APIs correctly
    fun warnMissingPermissions(): Boolean {
        val message = getMissingMessage()

        return if (message != null) {
            errormsg("Denied permissions: $message")
            showToast(message)
            true
        } else
            false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            DID_REQUEST_PERM -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ) {
                    // Permission is granted. Continue the action or workflow
                    // in your app.

                    // yay!
                } else {
                    // Explain to the user that the feature is unavailable because
                    // the features requires a permission that the user has denied.
                    // At the same time, respect the user's decision. Don't link to
                    // system settings in an effort to convince the user to change
                    // their decision.
                    warnMissingPermissions()
                }
            }
            else -> {
                // ignore other requests
            }
        }

        updateBluetoothEnabled()
    }


    private fun sendTestPackets() {
        exceptionReporter {
            val m = model.meshService!!

            // Do some test operations
            val testPayload = "hello world".toByteArray()
            m.send(
                DataPacket(
                    "+16508675310",
                    testPayload,
                    Portnums.PortNum.PRIVATE_APP_VALUE
                )
            )
            m.send(
                DataPacket(
                    "+16508675310",
                    testPayload,
                    Portnums.PortNum.TEXT_MESSAGE_APP_VALUE
                )
            )
        }
    }


    /// Ask user to rate in play store
    private fun askToRate() {
        exceptionReporter { // Got one IllegalArgumentException from inside this lib, but we don't want to crash our app because of bugs in this optional feature

            val hasGooglePlay = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this) != ConnectionResult.SERVICE_MISSING

            val rater = AppRate.with(this)
                .setInstallDays(10.toByte()) // default is 10, 0 means install day, 10 means app is launched 10 or more days later than installation
                .setLaunchTimes(10.toByte()) // default is 10, 3 means app is launched 3 or more times
                .setRemindInterval(1.toByte()) // default is 1, 1 means app is launched 1 or more days after neutral button clicked
                .setRemindLaunchesNumber(1.toByte()) // default is 0, 1 means app is launched 1 or more times after neutral button clicked
                .setStoreType(if (hasGooglePlay) StoreType.GOOGLEPLAY else StoreType.AMAZON)

            rater.monitor() // Monitors the app launch times

            // Only ask to rate if the user has a suitable store
            AppRate.showRateDialogIfMeetsConditions(this)     // Shows the Rate Dialog when conditions are met
        }
    }

    private val isInTestLab: Boolean by lazy {
        (application as GeeksvilleApplication).isInTestLab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        val prefs = UIViewModel.getPreferences(this)
        model.ownerName.value = prefs.getString("owner", "")!!

        /// Set theme
        setUITheme(prefs)

        /// Set initial bluetooth state
        updateBluetoothEnabled()

        /// We now want to be informed of bluetooth state
        registerReceiver(btStateReceiver, btStateReceiver.intentFilter)

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

        /* setContent {
            MeshApp()
        } */
        setContentView(binding.root)

        initToolbar()

        binding.pager.adapter = tabsAdapter
        binding.pager.isUserInputEnabled =
            false // Gestures for screen switching doesn't work so good with the map view
        // pager.offscreenPageLimit = 0 // Don't keep any offscreen pages around, because we want to make sure our bluetooth scanning stops
        TabLayoutMediator(binding.tabLayout, binding.pager, false, false) { tab, position ->
            // tab.text = tabInfos[position].text // I think it looks better with icons only
            tab.icon = ContextCompat.getDrawable(this, tabInfos[position].icon)
        }.attach()

        model.isConnected.observe(this, Observer { connected ->
            updateConnectionStatusImage(connected)
        })

        // Handle any intent
        handleIntent(intent)

        askToRate()

        // if (!isInTestLab) - very important - even in test lab we must request permissions because we need location perms for some of our tests to pass
        requestPermission()
    }


    private fun initToolbar() {
        val toolbar =
            findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun updateConnectionStatusImage(connected: MeshService.ConnectionState) {

        if (model.actionBarMenu == null)
            return

        val (image, tooltip) = when (connected) {
            MeshService.ConnectionState.CONNECTED -> Pair(R.drawable.cloud_on, R.string.connected)
            MeshService.ConnectionState.DEVICE_SLEEP -> Pair(
                R.drawable.ic_twotone_cloud_upload_24,
                R.string.device_sleeping
            )
            MeshService.ConnectionState.DISCONNECTED -> Pair(
                R.drawable.cloud_off,
                R.string.disconnected
            )
        }

        val item = model.actionBarMenu?.findItem(R.id.connectStatusImage)
        if (item != null) {
            item.setIcon(image)
            item.setTitle(tooltip)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private var requestedChannelUrl: Uri? = null

    /** We keep the usb device here, so later we can give it to our service */
    private var usbDevice: UsbDevice? = null

    /// Handle any itents that were passed into us
    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        when (appLinkAction) {
            Intent.ACTION_VIEW -> {
                debug("Asked to open a channel URL - ask user if they want to switch to that channel.  If so send the config to the radio")
                requestedChannelUrl = appLinkData

                // if the device is connected already, process it now
                if (model.isConnected.value == MeshService.ConnectionState.CONNECTED)
                    perhapsChangeChannel()

                // We now wait for the device to connect, once connected, we ask the user if they want to switch to the new channel
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                if (device != null) {
                    debug("Handle USB device attached! $device")
                    usbDevice = device
                }
            }

            Intent.ACTION_MAIN -> {
            }

            else -> {
                warn("Unexpected action $appLinkAction")
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(btStateReceiver)
        unregisterMeshReceiver()
        mainScope.cancel("Activity going away")
        super.onDestroy()
    }

    /**
     * Dispatch incoming result to the correct fragment.
     */
    @SuppressLint("InlinedApi")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        when (requestCode) {
            RC_SIGN_IN -> {
                // The Task returned from this call is always completed, no need to attach
                // a listener.
                val task: Task<GoogleSignInAccount> =
                    GoogleSignIn.getSignedInAccountFromIntent(data)
                handleSignInResult(task)
            }
            (65536 + RC_SELECT_DEVICE) -> when (resultCode) {
                Activity.RESULT_OK -> {
                    // User has chosen to pair with the Bluetooth device.
                    val device: BluetoothDevice =
                        data?.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)!!
                    debug("Received BLE pairing ${device.address}")
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        device.createBond()
                        // FIXME - wait for bond to complete
                    }

                    // ... Continue interacting with the paired device.
                    model.meshService?.let { service ->
                        MeshService.changeDeviceAddress(this@MainActivity, service, device.address)
                    }
                }

                else ->
                    warn("BLE device select intent failed")
            }
            CREATE_CSV_FILE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.let { file_uri ->
                        // model.allPackets is a result of a query, so we need to use observer for
                        // the query to materialize
                        model.allPackets.observe(this, { packets ->
                            if (packets != null) {
                                // no need for observer once got non-null list
                                model.allPackets.removeObservers(this)
                                // execute on the default thread pool to not block the main thread
                                CoroutineScope(Dispatchers.Default + Job()).handledLaunch {
                                    saveMessagesCSV(file_uri, packets)
                                }
                            }
                        })
                    }
                }
            }
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

    private var receiverRegistered = false

    private fun registerMeshReceiver() {
        unregisterMeshReceiver()
        val filter = IntentFilter()
        filter.addAction(MeshService.ACTION_MESH_CONNECTED)
        filter.addAction(MeshService.ACTION_NODE_CHANGE)
        filter.addAction(MeshService.actionReceived(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE))
        filter.addAction((MeshService.ACTION_MESSAGE_STATUS))
        registerReceiver(meshServiceReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterMeshReceiver() {
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(meshServiceReceiver)
        }
    }

    /// Pull our latest node db from the device
    private fun updateNodesFromDevice() {
        model.meshService?.let { service ->
            // Update our nodeinfos based on data from the device
            val nodes = service.nodes.map {
                it.user?.id!! to it
            }.toMap()

            model.nodeDB.nodes.value = nodes

            try {
                // Pull down our real node ID - This must be done AFTER reading the nodedb because we need the DB to find our nodeinof object
                model.nodeDB.myId.value = service.myId
                val ourNodeInfo = model.nodeDB.ourNodeInfo
                model.ownerName.value = ourNodeInfo?.user?.longName
            } catch (ex: Exception) {
                warn("Ignoring failure to get myId, service is probably just uninited... ${ex.message}")
            }
        }
    }

    /** Show an alert that may contain HTML */
    private fun showAlert(titleText: Int, messageText: Int) {
        // make links clickable per https://stackoverflow.com/a/62642807
        // val messageStr = getText(messageText)

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(titleText)
            .setMessage(messageText)
            .setPositiveButton(R.string.okay) { _, _ ->
                info("User acknowledged")
            }

        val dialog = builder.show()

        // Make the textview clickable. Must be called after show()
        val view = (dialog.findViewById(android.R.id.message) as TextView?)!!
        // Linkify.addLinks(view, Linkify.ALL) // not needed with this method
        view.movementMethod = LinkMovementMethod.getInstance()

        showSettingsPage() // Default to the settings page in this case
    }

    /// Called when we gain/lose a connection to our mesh radio
    private fun onMeshConnectionChanged(connected: MeshService.ConnectionState) {
        debug("connchange ${model.isConnected.value} -> $connected")

        if (connected == MeshService.ConnectionState.CONNECTED) {
            model.meshService?.let { service ->

                val oldConnection = model.isConnected.value
                model.isConnected.value = connected

                debug("Getting latest radioconfig from service")
                try {
                    val info: MyNodeInfo? = service.myNodeInfo // this can be null
                    model.myNodeInfo.value = info

                    if (info != null) {
                        val isOld = info.minAppVersion > BuildConfig.VERSION_CODE
                        if (isOld)
                            showAlert(R.string.app_too_old, R.string.must_update)
                        else {
                            // If we are already doing an update don't put up a dialog or try to get device info
                            val isUpdating = service.updateStatus >= 0
                            if (!isUpdating) {
                                val curVer = DeviceVersion(info.firmwareVersion ?: "0.0.0")

                                if (curVer < MeshService.minFirmwareVersion)
                                    showAlert(R.string.firmware_too_old, R.string.firmware_old)
                                else {
                                    // If our app is too old/new, we probably don't understand the new radioconfig messages, so we don't read them until here

                                    model.radioConfig.value =
                                        RadioConfigProtos.RadioConfig.parseFrom(service.radioConfig)

                                    model.channels.value =
                                        ChannelSet(AppOnlyProtos.ChannelSet.parseFrom(service.channels))

                                    updateNodesFromDevice()

                                    // we have a connection to our device now, do the channel change
                                    perhapsChangeChannel()
                                }
                            }
                        }
                    }
                } catch (ex: RemoteException) {
                    warn("Abandoning connect $ex, because we probably just lost device connection")
                    model.isConnected.value = oldConnection
                }
                // if provideLocation enabled: Start providing location (from phone GPS) to mesh
                if (model.provideLocation.value == true && (oldConnection != connected))
                    service.setupProvideLocation()
            }
        } else {
            // For other connection states, just slam them in
            model.isConnected.value = connected
        }
    }

    private fun showToast(msgId: Int) {
        Toast.makeText(
            this,
            msgId,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun perhapsChangeChannel() {
        // If the is opening a channel URL, handle it now
        requestedChannelUrl?.let { url ->
            try {
                val channels = ChannelSet(url)
                val primary = channels.primaryChannel
                if (primary == null)
                    showToast(R.string.channel_invalid)
                else {
                    requestedChannelUrl = null

                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.new_channel_rcvd)
                        .setMessage(getString(R.string.do_you_want_switch).format(primary.name))
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            // Do nothing
                        }
                        .setPositiveButton(R.string.accept) { _, _ ->
                            debug("Setting channel from URL")
                            try {
                                model.setChannels(channels)
                            } catch (ex: RemoteException) {
                                errormsg("Couldn't change channel ${ex.message}")
                                showToast(R.string.cant_change_no_radio)
                            }
                        }
                        .show()
                }
            } catch (ex: InvalidProtocolBufferException) {
                showToast(R.string.channel_invalid)
            }
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

    private val meshServiceReceiver = object : BroadcastReceiver() {

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

                    MeshService.actionReceived(Portnums.PortNum.TEXT_MESSAGE_APP_VALUE) -> {
                        debug("received new message from service")
                        val payload =
                            intent.getParcelableExtra<DataPacket>(EXTRA_PAYLOAD)!!

                        model.messagesState.addMessage(payload)
                    }

                    MeshService.ACTION_MESSAGE_STATUS -> {
                        debug("received message status from service")
                        val id = intent.getIntExtra(EXTRA_PACKET_ID, 0)
                        val status = intent.getParcelableExtra<MessageStatus>(EXTRA_STATUS)!!

                        model.messagesState.updateStatus(id, status)
                    }

                    MeshService.ACTION_MESH_CONNECTED -> {
                        val extra = intent.getStringExtra(EXTRA_CONNECTED)
                        if (extra != null) {
                            onMeshConnectionChanged(MeshService.ConnectionState.valueOf(extra))
                        }
                    }
                    else -> TODO()
                }
            }
    }

    private var connectionJob: Job? = null

    private val mesh = object :
        ServiceClient<com.geeksville.mesh.IMeshService>({
            com.geeksville.mesh.IMeshService.Stub.asInterface(it)
        }) {
        override fun onConnected(service: com.geeksville.mesh.IMeshService) {

            /*
                Note: we must call this callback in a coroutine.  Because apparently there is only a single activity looper thread.  and if that onConnected override
                also tries to do a service operation we can deadlock.

                Old buggy stack trace:

                 at sun.misc.Unsafe.park (Unsafe.java)
                - waiting on an unknown object
                  at java.util.concurrent.locks.LockSupport.park (LockSupport.java:190)
                  at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await (AbstractQueuedSynchronizer.java:2067)
                  at com.geeksville.android.ServiceClient.waitConnect (ServiceClient.java:46)
                  at com.geeksville.android.ServiceClient.getService (ServiceClient.java:27)
                  at com.geeksville.mesh.service.MeshService$binder$1$setDeviceAddress$1.invoke (MeshService.java:1519)
                  at com.geeksville.mesh.service.MeshService$binder$1$setDeviceAddress$1.invoke (MeshService.java:1514)
                  at com.geeksville.util.ExceptionsKt.toRemoteExceptions (ExceptionsKt.java:56)
                  at com.geeksville.mesh.service.MeshService$binder$1.setDeviceAddress (MeshService.java:1516)
                  at com.geeksville.mesh.MainActivity$mesh$1$onConnected$1.invoke (MainActivity.java:743)
                  at com.geeksville.mesh.MainActivity$mesh$1$onConnected$1.invoke (MainActivity.java:734)
                  at com.geeksville.util.ExceptionsKt.exceptionReporter (ExceptionsKt.java:34)
                  at com.geeksville.mesh.MainActivity$mesh$1.onConnected (MainActivity.java:738)
                  at com.geeksville.mesh.MainActivity$mesh$1.onConnected (MainActivity.java:734)
                  at com.geeksville.android.ServiceClient$connection$1$onServiceConnected$1.invoke (ServiceClient.java:89)
                  at com.geeksville.android.ServiceClient$connection$1$onServiceConnected$1.invoke (ServiceClient.java:84)
                  at com.geeksville.util.ExceptionsKt.exceptionReporter (ExceptionsKt.java:34)
                  at com.geeksville.android.ServiceClient$connection$1.onServiceConnected (ServiceClient.java:85)
                  at android.app.LoadedApk$ServiceDispatcher.doConnected (LoadedApk.java:2067)
                  at android.app.LoadedApk$ServiceDispatcher$RunConnection.run (LoadedApk.java:2099)
                  at android.os.Handler.handleCallback (Handler.java:883)
                  at android.os.Handler.dispatchMessage (Handler.java:100)
                  at android.os.Looper.loop (Looper.java:237)
                  at android.app.ActivityThread.main (ActivityThread.java:8016)
                  at java.lang.reflect.Method.invoke (Method.java)
                  at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run (RuntimeInit.java:493)
                  at com.android.internal.os.ZygoteInit.main (ZygoteInit.java:1076)
                 */
            connectionJob = mainScope.handledLaunch {
                model.meshService = service

                try {
                    usbDevice?.let { usb ->
                        debug("Switching to USB radio ${usb.deviceName}")
                        service.setDeviceAddress(SerialInterface.toInterfaceName(usb.deviceName))
                        usbDevice =
                            null // Only switch once - thereafter it should be stored in settings
                    }

                    // We don't start listening for packets until after we are connected to the service
                    registerMeshReceiver()

                    // Init our messages table with the service's record of past text messages (ignore all other message types)
                    val allMsgs = service.oldMessages
                    val msgs =
                        allMsgs.filter { p -> p.dataType == Portnums.PortNum.TEXT_MESSAGE_APP_VALUE }

                    model.myNodeInfo.value = service.myNodeInfo // Note: this could be NULL!
                    debug("Service provided ${msgs.size} messages and myNodeNum ${model.myNodeInfo.value?.myNodeNum}")

                    model.messagesState.setMessages(msgs)
                    val connectionState =
                        MeshService.ConnectionState.valueOf(service.connectionState())

                    // if we are not connected, onMeshConnectionChange won't fetch nodes from the service
                    // in that case, we do it here - because the service certainly has a better idea of node db that we have
                    if (connectionState != MeshService.ConnectionState.CONNECTED)
                        updateNodesFromDevice()

                    // We won't receive a notify for the initial state of connection, so we force an update here
                    onMeshConnectionChanged(connectionState)
                } catch (ex: RemoteException) {
                    // If we get an exception while reading our service config, the device might have gone away, double check to see if we are really connected
                    errormsg("Device error during init ${ex.message}")
                    model.isConnected.value =
                        MeshService.ConnectionState.valueOf(service.connectionState())
                } finally {
                    connectionJob = null
                }

                debug("connected to mesh service, isConnected=${model.isConnected.value}")
            }
        }

        override fun onDisconnected() {
            unregisterMeshReceiver()
            model.meshService = null
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        if (model.meshService != null) {
            /* This problem can occur if we unbind, but there is already an onConnected job waiting to run.  That job runs and then makes meshService != null again
            I think I've fixed this by cancelling connectionJob.  We'll see!
             */
            Exceptions.reportError("meshService was supposed to be null, ignoring (but reporting a bug)")
        }

        try {
            MeshService.startService(this) // Start the service so it stays running even after we unbind
        } catch (ex: Exception) {
            // Old samsung phones have a race condition andthis might rarely fail.  Which is probably find because the bind will be sufficient most of the time
            errormsg("Failed to start service from activity - but ignoring because bind will work ${ex.message}")
        }

        // ALSO bind so we can use the api
        mesh.connect(
            this,
            MeshService.createIntent(),
            Context.BIND_AUTO_CREATE + Context.BIND_ABOVE_CLIENT
        )
    }

    private fun unbindMeshService() {
        // If we have received the service, and hence registered with
        // it, then now is the time to unregister.
        // if we never connected, do nothing
        debug("Unbinding from mesh service!")
        connectionJob?.let { job ->
            connectionJob = null
            warn("We had a pending onConnection job, so we are cancelling it")
            job.cancel("unbinding")
        }
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

        // Ask to start bluetooth if no USB devices are visible
        val hasUSB = SerialInterface.findDrivers(this).isNotEmpty()
        if (!isInTestLab && !hasUSB) {
            bluetoothAdapter?.let {
                if (!it.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                }
            }
        }

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
            // App is probably shutting down, ignore
            errormsg("Bind of MeshService failed")
        }

        val bonded = RadioInterfaceService.getBondedDeviceAddress(this) != null
        if (!bonded && usbDevice == null) // we will handle USB later
            showSettingsPage()
    }

    private fun showSettingsPage() {
        binding.pager.currentItem = 5
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        model.actionBarMenu = menu

        updateConnectionStatusImage(model.isConnected.value!!)

        return true
    }

    val handler: Handler by lazy {
        Handler(mainLooper)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.stress_test).isVisible =
            BuildConfig.DEBUG // only show stress test for debug builds (for now)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.about -> {
                getVersionInfo()
                return true
            }
            R.id.connectStatusImage -> {
                Toast.makeText(applicationContext, item.title, Toast.LENGTH_SHORT).show()
                return true
            }
            R.id.debug -> {
                val fragmentManager: FragmentManager = supportFragmentManager
                val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
                val nameFragment = DebugFragment()
                fragmentTransaction.add(R.id.mainActivityLayout, nameFragment)
                fragmentTransaction.addToBackStack(null)
                fragmentTransaction.commit()
                return true
            }
            R.id.stress_test -> {
                fun postPing() {
                    // Send ping message and arrange delayed recursion.
                    debug("Sending ping")
                    val str = "Ping " + DateFormat.getTimeInstance(DateFormat.MEDIUM)
                        .format(Date(System.currentTimeMillis()))
                    model.messagesState.sendMessage(str)
                    handler.postDelayed(
                        Runnable {
                            postPing()
                        },
                        30000
                    )
                }
                item.isChecked = !item.isChecked // toggle ping test
                if (item.isChecked)
                    postPing()
                else
                    handler.removeCallbacksAndMessages(null)
                return true
            }
            R.id.advanced_settings -> {
                val fragmentManager: FragmentManager = supportFragmentManager
                val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
                val nameFragment = AdvancedSettingsFragment()
                fragmentTransaction.add(R.id.mainActivityLayout, nameFragment)
                fragmentTransaction.addToBackStack(null)
                fragmentTransaction.commit()
                return true
            }
            R.id.save_messages_csv -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, "messages.csv")
                }
                startActivityForResult(intent, CREATE_CSV_FILE)
                return true
            }
            R.id.theme -> {
                chooseThemeDialog()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getVersionInfo() {
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            showToast(versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            errormsg("Can not find the version: ${e.message}")
        }
    }

    private fun saveMessagesCSV(file_uri: Uri, packets: List<Packet>) {
        // Extract distances to this device from position messages and put (node,SNR,distance) in
        // the file_uri
        val myNodeNum = model.myNodeInfo.value?.myNodeNum ?: return

        applicationContext.contentResolver.openFileDescriptor(file_uri, "w")?.use {
            FileOutputStream(it.fileDescriptor).use { fs ->
                // Write header
                fs.write(("from,rssi,snr,time,dist\n").toByteArray())
                // Packets are ordered by time, we keep most recent position of
                // our device in my_position.
                var my_position: MeshProtos.Position? = null
                packets.forEach {
                    it.proto?.let { packet_proto ->
                        it.position?.let { position ->
                            if (packet_proto.from == myNodeNum) {
                                my_position = position
                            } else if (my_position != null) {
                                val dist = positionToMeter(my_position!!, position).roundToInt()
                                fs.write(
                                    "%x,%d,%f,%d,%d\n".format(
                                        packet_proto.from, packet_proto.rxRssi,
                                        packet_proto.rxSnr, packet_proto.rxTime, dist
                                    ).toByteArray()
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    /// Theme functions

    private fun chooseThemeDialog() {

        /// Prepare dialog and its items
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.choose_theme_title))

        val styles = arrayOf(
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
            getString(R.string.theme_system)
        )

        /// Load preferences and its value
        val prefs = UIViewModel.getPreferences(this)
        val editor: SharedPreferences.Editor = prefs.edit()
        val checkedItem = prefs.getInt("theme", 2)

        builder.setSingleChoiceItems(styles, checkedItem) { dialog, which ->

            when (which) {
                0 -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    editor.putInt("theme", 0)
                    editor.apply()

                    delegate.applyDayNight()
                    dialog.dismiss()
                }
                1 -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    editor.putInt("theme", 1)
                    editor.apply()

                    delegate.applyDayNight()
                    dialog.dismiss()
                }
                2 -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                    editor.putInt("theme", 2)
                    editor.apply()

                    delegate.applyDayNight()
                    dialog.dismiss()
                }

            }
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun setUITheme(prefs: SharedPreferences) {
        /// Read theme settings from preferences and set it
        /// If nothing is found set FOLLOW SYSTEM option

        when (prefs.getInt("theme", 2)) {
            0 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                delegate.applyDayNight()
            }
            1 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                delegate.applyDayNight()
            }
            2 -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                delegate.applyDayNight()
            }
        }
    }

}
