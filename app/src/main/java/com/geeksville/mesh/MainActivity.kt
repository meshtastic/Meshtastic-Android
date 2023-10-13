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
import androidx.appcompat.app.AppCompatActivity
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
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.primaryChannel
import com.geeksville.mesh.model.toChannelSet
import com.geeksville.mesh.repository.radio.BluetoothInterface
import com.geeksville.mesh.repository.radio.SerialInterface
import com.geeksville.mesh.service.*
import com.geeksville.mesh.ui.*
import com.geeksville.mesh.ui.map.MapFragment
import com.geeksville.mesh.util.Exceptions
import com.geeksville.mesh.util.getParcelableExtraCompat
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.exceptionReporter
import com.geeksville.mesh.util.getPackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
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
class MainActivity : AppCompatActivity(), Logging {

    private lateinit var binding: ActivityMainBinding

    // Used to schedule a coroutine in the GUI thread
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private val model: UIViewModel by viewModels()

    @Inject
    internal lateinit var serviceRepository: ServiceRepository

    private val bluetoothPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.entries.all { it.value }) {
                info("Bluetooth permissions granted")
            } else {
                warn("Bluetooth permissions denied")
                showSnackbar(permissionMissing)
            }
            requestedEnable = false
            bluetoothViewModel.permissionsUpdated()
        }

    private val notificationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.entries.all { it.value }) {
                info("Notification permissions granted")
            } else {
                warn("Notification permissions denied")
                showSnackbar(getString(R.string.notification_denied))
            }
        }

    data class TabInfo(val text: String, val icon: Int, val content: Fragment)

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

    private val tabsAdapter = object : FragmentStateAdapter(supportFragmentManager, lifecycle) {

        override fun getItemCount(): Int = tabInfos.size
        override fun createFragment(position: Int): Fragment = tabInfos[position].content
    }

    private val isInTestLab: Boolean by lazy {
        (application as GeeksvilleApplication).isInTestLab
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val prefs = UIViewModel.getPreferences(this)
            // First run: show AppIntroduction
            if (!prefs.getBoolean("app_intro_completed", false)) {
                startActivity(Intent(this, AppIntroduction::class.java))
            }
            // First run: migrate in-app language prefs to appcompat
            val lang = prefs.getString("lang", LanguageUtils.SYSTEM_DEFAULT)
            if (lang != LanguageUtils.SYSTEM_MANAGED) LanguageUtils.migrateLanguagePrefs(prefs)
            info("in-app language is ${LanguageUtils.getLocale()}")
            // Set theme
            AppCompatDelegate.setDefaultNightMode(
                prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            )
            // Ask user to rate in play store
            (application as GeeksvilleApplication).askToRate(this)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
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

        // Handle any intent
        handleIntent(intent)
    }

    private fun initToolbar() {
        val toolbar = binding.toolbar as Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }

    private fun updateConnectionStatusImage(connected: MeshService.ConnectionState) {
        if (model.actionBarMenu == null) return

        val (image, tooltip) = when (connected) {
            MeshService.ConnectionState.CONNECTED -> R.drawable.cloud_on to R.string.connected
            MeshService.ConnectionState.DEVICE_SLEEP -> R.drawable.ic_twotone_cloud_upload_24 to R.string.device_sleeping
            MeshService.ConnectionState.DISCONNECTED -> R.drawable.cloud_off to R.string.disconnected
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
                val device: UsbDevice? = intent.getParcelableExtraCompat(UsbManager.EXTRA_DEVICE)
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
            .setCancelable(false)
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
            serviceRepository.meshService?.let { service ->

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

            if (!hasNotificationPermission()) {
                val notificationPermissions = getNotificationPermissions()
                rationaleDialog(
                    shouldShowRequestPermissionRationale(notificationPermissions),
                    R.string.notification_required,
                    getString(R.string.why_notification_required),
                ) {
                    notificationPermissionsLauncher.launch(notificationPermissions)
                }
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
                val channels = url.toChannelSet()
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
            connectionJob = mainScope.handledLaunch {
                serviceRepository.setMeshService(service)

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
            serviceRepository.setMeshService(null)
        }
    }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        // we bind using the well known name, to make sure 3rd party apps could also
        if (serviceRepository.meshService != null) {
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
        serviceRepository.setMeshService(null)
    }

    override fun onStop() {
        unregisterMeshReceiver() // No point in receiving updates while the GUI is gone, we'll get them when the user launches the activity
        unbindMeshService()

        model.connectionState.removeObservers(this)
        bluetoothViewModel.enabled.removeObservers(this)
        model.requestChannelUrl.removeObservers(this)
        model.snackbarText.removeObservers(this)

        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        model.connectionState.observe(this) { connected ->
            updateConnectionStatusImage(connected)
        }

        bluetoothViewModel.enabled.observe(this) { enabled ->
            if (!enabled && !requestedEnable && model.selectedBluetooth) {
                requestedEnable = true
                if (hasBluetoothPermission()) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    bleRequestEnable.launch(enableBtIntent)
                } else {
                    val bluetoothPermissions = getBluetoothPermissions()
                    rationaleDialog(shouldShowRequestPermissionRationale(bluetoothPermissions)) {
                        bluetoothPermissionsLauncher.launch(bluetoothPermissions)
                    }
                }
            }
        }

        // Call perhapsChangeChannel() whenever [requestChannelUrl] updates with a non-null value
        model.requestChannelUrl.observe(this) { url ->
            url?.let {
                requestedChannelUrl = url
                model.clearRequestChannelUrl()
                perhapsChangeChannel()
            }
        }

        // Call showSnackbar() whenever [snackbarText] updates with a non-null value
        model.snackbarText.observe(this) { text ->
            if (text is Int) showSnackbar(text)
            if (text is String) showSnackbar(text)
            if (text != null) model.clearSnackbarText()
        }

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
            // App is probably shutting down, ignore
            errormsg("Bind of MeshService failed")
        }

        val bonded = model.bondedAddress != null
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

    private val handler: Handler by lazy {
        Handler(Looper.getMainLooper())
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.stress_test).isVisible =
            BuildConfig.DEBUG // only show stress test for debug builds (for now)
        menu.findItem(R.id.radio_config).isEnabled = !model.isManaged
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
            R.id.radio_config -> {
                model.setDestNode(null)
                supportFragmentManager.beginTransaction()
                    .add(R.id.mainActivityLayout, DeviceSettingsFragment())
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
            val packageInfo: PackageInfo = packageManager.getPackageInfoCompat(packageName, 0)
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
        builder.setTitle(getString(R.string.choose_theme))

        val styles = mapOf(
            getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
            getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
            getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        /// Load preferences and its value
        val prefs = UIViewModel.getPreferences(this)
        val theme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        debug("Theme from prefs: $theme")

        builder.setSingleChoiceItems(
            styles.keys.toTypedArray(),
            styles.values.indexOf(theme)
        ) { dialog, position ->
            val selectedTheme = styles.values.elementAt(position)
            debug("Set theme pref to $selectedTheme")
            prefs.edit().putInt("theme", selectedTheme).apply()
            AppCompatDelegate.setDefaultNightMode(selectedTheme)
            dialog.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
    }

    private fun chooseLangDialog() {
        /// Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.preferences_language))

        val languageTags = LanguageUtils.getLanguageTags(this)

        /// Load preferences and its value
        val lang = LanguageUtils.getLocale()
        debug("Lang from prefs: $lang")

        builder.setSingleChoiceItems(
            languageTags.keys.toTypedArray(),
            languageTags.values.indexOf(lang)
        ) { dialog, position ->
            val selectedLang = languageTags.values.elementAt(position)
            debug("Set lang pref to $selectedLang")
            LanguageUtils.setLocale(selectedLang)
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
    }
}
