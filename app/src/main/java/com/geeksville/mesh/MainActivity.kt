package com.geeksville.mesh

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.text.method.LinkMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.geeksville.mesh.android.*
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.databinding.ActivityMainBinding
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.ChannelSet
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.repository.radio.BluetoothInterface
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.radio.SerialInterface
import com.geeksville.mesh.service.*
import com.geeksville.mesh.ui.*
import com.geeksville.mesh.util.Exceptions
import com.geeksville.mesh.util.exceptionReporter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.suddenh4x.ratingdialog.AppRating
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

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

@AndroidEntryPoint
class MainActivity : BaseActivity(), Logging {

    private lateinit var binding: ActivityMainBinding

    // Used to schedule a coroutine in the GUI thread
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private val scanModel: BTScanModel by viewModels()
    val model: UIViewModel by viewModels()

    @Inject
    internal lateinit var radioInterfaceService: RadioInterfaceService

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (!permissions.entries.all { it.value }) {
                errormsg("User denied permissions")
                showSnackbar(permissionMissing)
            }
            requestedEnable = false
            bluetoothViewModel.permissionsUpdated()
        }

    data class TabInfo(val text: String, val icon: Int, val content: Fragment)

    // private val tabIndexes = generateSequence(0) { it + 1 } FIXME, instead do withIndex or zip? to get the ids below, also stop duplicating strings
    private val tabInfos = arrayOf(
        TabInfo(
            "Messages",
            R.drawable.ic_twotone_message_24,
            ContactsFragment()
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

    /// Ask user to rate in play store
    private fun askToRate() {
        exceptionReporter { // we don't want to crash our app because of bugs in this optional feature
            AppRating.Builder(this)
                .setMinimumLaunchTimes(10) // default is 5, 3 means app is launched 3 or more times
                .setMinimumDays(10) // default is 5, 0 means install day, 10 means app is launched 10 or more days later than installation
                .setMinimumLaunchTimesToShowAgain(5) // default is 5, 1 means app is launched 1 or more times after neutral button clicked
                .setMinimumDaysToShowAgain(14) // default is 14, 1 means app is launched 1 or more days after neutral button clicked
                .showIfMeetsConditions()
        }
    }

    private val isInTestLab: Boolean by lazy {
        (application as GeeksvilleApplication).isInTestLab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val prefs = UIViewModel.getPreferences(this)
        if (!prefs.getBoolean("app_intro_completed", false)) {
            startActivity(Intent(this, AppIntroduction::class.java))
        }

        binding = ActivityMainBinding.inflate(layoutInflater)

        /// Set theme
        setUITheme(prefs)
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

        model.connectionState.observe(this) { connected ->
            updateConnectionStatusImage(connected)
        }

        // Handle any intent
        handleIntent(intent)

        if (isGooglePlayAvailable(this)) askToRate()
    }

    private fun initToolbar() {
        val toolbar = binding.toolbar as Toolbar
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

    private var requestedEnable = false
    private val bleRequestEnable = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestedEnable = false
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) {
            it.data?.data?.let { file_uri -> model.saveMessagesCSV(file_uri) }
        }
    }

    override fun onDestroy() {
        unregisterMeshReceiver()
        mainScope.cancel("Activity going away")
        super.onDestroy()
    }

    private var receiverRegistered = false

    private fun registerMeshReceiver() {
        unregisterMeshReceiver()
        val filter = IntentFilter()
        filter.addAction(MeshService.ACTION_MESH_CONNECTED)
        filter.addAction(MeshService.ACTION_NODE_CHANGE)
        registerReceiver(meshServiceReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterMeshReceiver() {
        if (receiverRegistered) {
            receiverRegistered = false
            unregisterReceiver(meshServiceReceiver)
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
    private fun onMeshConnectionChanged(newConnection: MeshService.ConnectionState) {
        val oldConnection = model.connectionState.value!!
        debug("connchange $oldConnection -> $newConnection")

        if (newConnection == MeshService.ConnectionState.CONNECTED) {
            model.meshService?.let { service ->

                model.setConnectionState(newConnection)

                debug("Getting latest DeviceConfig from service")
                try {
                    val info: MyNodeInfo? = service.myNodeInfo // this can be null
                    model.setMyNodeInfo(info)

                    if (info != null) {
                        val isOld = info.minAppVersion > BuildConfig.VERSION_CODE
                        if (isOld)
                            showAlert(R.string.app_too_old, R.string.must_update)
                        else {
                            // If we are already doing an update don't put up a dialog or try to get device info
                            val isUpdating = service.updateStatus >= 0
                            if (!isUpdating) {
                                val curVer = DeviceVersion(info.firmwareVersion ?: "0.0.0")

                                if (curVer < MeshService.minDeviceVersion)
                                    showAlert(R.string.firmware_too_old, R.string.firmware_old)
                                else {
                                    // If our app is too old/new, we probably don't understand the new DeviceConfig messages, so we don't read them until here

                                    // model.setLocalConfig(LocalOnlyProtos.LocalConfig.parseFrom(service.deviceConfig))
                                    // model.setChannels(ChannelSet(AppOnlyProtos.ChannelSet.parseFrom(service.channels)))

                                    model.updateNodesFromDevice()

                                    // we have a connection to our device now, do the channel change
                                    perhapsChangeChannel()
                                }
                            }
                        }
                    } else if (BluetoothInterface.invalidVersion) {
                        showAlert(R.string.firmware_too_old, R.string.firmware_old)
                    }
                } catch (ex: RemoteException) {
                    warn("Abandoning connect $ex, because we probably just lost device connection")
                    model.setConnectionState(oldConnection)
                }
                // if provideLocation enabled: Start providing location (from phone GPS) to mesh
                if (model.provideLocation.value == true)
                    service.startProvideLocation()
            }
        } else {
            // For other connection states, just slam them in
            model.setConnectionState(newConnection)
        }
    }

    private fun showSnackbar(msgId: Int) {
        try {
            Snackbar.make(binding.root, msgId, Snackbar.LENGTH_LONG).show()
        } catch (ex: IllegalStateException) {
            errormsg("Snackbar couldn't find view for msgId $msgId")
        }
    }

    private fun showSnackbar(msg: String) {
        try {
            Snackbar.make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                .apply { view.findViewById<TextView>(R.id.snackbar_text).isSingleLine = false }
                .setAction(R.string.okay) {
                    // dismiss
                }
                .show()
        } catch (ex: IllegalStateException) {
            errormsg("Snackbar couldn't find view for msgString $msg")
        }
    }

    private fun perhapsChangeChannel(url: Uri? = requestedChannelUrl) {
        // if the device is connected already, process it now
        if (url != null && model.isConnected()) {
            requestedChannelUrl = null
            try {
                val channels = ChannelSet(url)
                val primary = channels.primaryChannel
                if (primary == null)
                    showSnackbar(R.string.channel_invalid)
                else {

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
                                showSnackbar(R.string.cant_change_no_radio)
                            }
                        }
                        .show()
                }
            } catch (ex: Throwable) {
                errormsg("Channel url error: ${ex.message}")
                showSnackbar("${getString(R.string.channel_invalid)}: ${ex.message}")
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
                            val nodes = model.nodeDB.nodes.value!! + Pair(it, info)
                            model.nodeDB.setNodes(nodes)
                        }
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
        ServiceClient<IMeshService>({
            IMeshService.Stub.asInterface(it)
        }) {
        override fun onConnected(service: IMeshService) {

            /*
                Note: we must call this callback in a coroutine.  Because apparently there is only a single activity looper thread.  and if that onConnected override
                also tries to do a service operation we can deadlock.

                Old buggy stack trace:

                 at sun.misc.Unsafe.park (Unsafe.java)
                - waiting on an unknown object
                  at java.util.concurrent.locks.LockSupport.park (LockSupport.java:190)
                  at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await (AbstractQueuedSynchronizer.java:2067)
                  at com.geeksville.mesh.android.ServiceClient.waitConnect (ServiceClient.java:46)
                  at com.geeksville.mesh.android.ServiceClient.getService (ServiceClient.java:27)
                  at com.geeksville.mesh.service.MeshService$binder$1$setDeviceAddress$1.invoke (MeshService.java:1519)
                  at com.geeksville.mesh.service.MeshService$binder$1$setDeviceAddress$1.invoke (MeshService.java:1514)
                  at com.geeksville.mesh.util.ExceptionsKt.toRemoteExceptions (ExceptionsKt.java:56)
                  at com.geeksville.mesh.service.MeshService$binder$1.setDeviceAddress (MeshService.java:1516)
                  at com.geeksville.mesh.MainActivity$mesh$1$onConnected$1.invoke (MainActivity.java:743)
                  at com.geeksville.mesh.MainActivity$mesh$1$onConnected$1.invoke (MainActivity.java:734)
                  at com.geeksville.mesh.util.ExceptionsKt.exceptionReporter (ExceptionsKt.java:34)
                  at com.geeksville.mesh.MainActivity$mesh$1.onConnected (MainActivity.java:738)
                  at com.geeksville.mesh.MainActivity$mesh$1.onConnected (MainActivity.java:734)
                  at com.geeksville.mesh.android.ServiceClient$connection$1$onServiceConnected$1.invoke (ServiceClient.java:89)
                  at com.geeksville.mesh.android.ServiceClient$connection$1$onServiceConnected$1.invoke (ServiceClient.java:84)
                  at com.geeksville.mesh.util.ExceptionsKt.exceptionReporter (ExceptionsKt.java:34)
                  at com.geeksville.mesh.android.ServiceClient$connection$1.onServiceConnected (ServiceClient.java:85)
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

                    model.setMyNodeInfo(service.myNodeInfo) // Note: this could be NULL!

                    val connectionState =
                        MeshService.ConnectionState.valueOf(service.connectionState())

                    // if we are not connected, onMeshConnectionChange won't fetch nodes from the service
                    // in that case, we do it here - because the service certainly has a better idea of node db that we have
                    if (connectionState != MeshService.ConnectionState.CONNECTED)
                        model.updateNodesFromDevice()

                    // We won't receive a notify for the initial state of connection, so we force an update here
                    onMeshConnectionChanged(connectionState)
                } catch (ex: RemoteException) {
                    // If we get an exception while reading our service config, the device might have gone away, double check to see if we are really connected
                    errormsg("Device error during init ${ex.message}")
                    model.setConnectionState(MeshService.ConnectionState.valueOf(service.connectionState()))
                } finally {
                    connectionJob = null
                }

                debug("connected to mesh service, connectionState=${model.connectionState.value}")
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

        bluetoothViewModel.enabled.observe(this) { enabled ->
            if (!enabled && !requestedEnable && scanModel.selectedBluetooth) {
                requestedEnable = true
                if (hasBluetoothPermission()) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bleRequestEnable.launch(enableBtIntent)
                } else {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.required_permissions))
                        .setMessage(permissionMissing)
                        .setNeutralButton(R.string.cancel) { _, _ ->
                            warn("User bailed due to permissions")
                        }
                        .setPositiveButton(R.string.accept) { _, _ ->
                            info("requesting permissions")
                            requestPermissionsLauncher.launch(getBluetoothPermissions())                    }
                        .show()
                }
            }
        }

        // Call perhapsChangeChannel() whenever [changeChannelUrl] updates with a non-null value
        model.requestChannelUrl.observe(this) { url ->
            url?.let {
                requestedChannelUrl = url
                model.clearRequestChannelUrl()
                perhapsChangeChannel()
            }
        }

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
            // App is probably shutting down, ignore
            errormsg("Bind of MeshService failed")
        }

        val bonded = radioInterfaceService.getBondedDeviceAddress() != null
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

        updateConnectionStatusImage(model.connectionState.value!!)

        return true
    }

    val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
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
                    model.sendMessage(str)
                    handler.postDelayed({ postPing() }, 30000)
                }
                item.isChecked = !item.isChecked // toggle ping test
                if (item.isChecked)
                    postPing()
                else
                    handler.removeCallbacksAndMessages(null)
                return true
            }
            R.id.device_settings -> {
                supportFragmentManager.beginTransaction()
                    .add(R.id.mainActivityLayout, DeviceSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                return true
            }
            R.id.module_settings -> {
                supportFragmentManager.beginTransaction()
                    .add(R.id.mainActivityLayout, ModuleSettingsFragment())
                    .addToBackStack(null)
                    .commit()
                return true
            }
            R.id.save_messages_csv -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, "rangetest.csv")
                }
                createDocumentLauncher.launch(intent)
                return true
            }
            R.id.theme -> {
                chooseThemeDialog()
                return true
            }
            R.id.preferences_language -> {
                chooseLangDialog()
                return true
            }
            R.id.show_intro -> {
                startActivity(Intent(this, AppIntroduction::class.java))
                return true
            }
            R.id.preferences_quick_chat -> {
                val fragmentManager: FragmentManager = supportFragmentManager
                val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()
                val nameFragment = QuickChatSettingsFragment()
                fragmentTransaction.add(R.id.mainActivityLayout, nameFragment)
                fragmentTransaction.addToBackStack(null)
                fragmentTransaction.commit()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun getVersionInfo() {
        try {
            val packageInfo: PackageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            Toast.makeText(this, versionName, Toast.LENGTH_LONG).show()
        } catch (e: PackageManager.NameNotFoundException) {
            errormsg("Can not find the version: ${e.message}")
        }
    }

    /// Theme functions

    private fun chooseThemeDialog() {

        /// Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(this)
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

    private fun chooseLangDialog() {

        /// Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.preferences_language))

        val languageLabels by lazy { resources.getStringArray(R.array.language_entries) }
        val languageValues by lazy { resources.getStringArray(R.array.language_values) }

        /// Load preferences and its value
        val prefs = UIViewModel.getPreferences(this)
        val editor: SharedPreferences.Editor = prefs.edit()
        val lang = prefs.getString("lang", "zz")
        debug("Lang from prefs: $lang")

        builder.setSingleChoiceItems(
            languageLabels,
            languageValues.indexOf(lang)
        ) { dialog, which ->
            val selectedLang = languageValues[which]
            debug("Set lang pref to $selectedLang")
            editor.putString("lang", selectedLang)
            editor.apply()
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }
}
