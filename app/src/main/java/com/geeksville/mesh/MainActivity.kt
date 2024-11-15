/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
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
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.asLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.android.getNotificationPermissions
import com.geeksville.mesh.android.hasBluetoothPermission
import com.geeksville.mesh.android.hasNotificationPermission
import com.geeksville.mesh.android.permissionMissing
import com.geeksville.mesh.android.rationaleDialog
import com.geeksville.mesh.android.shouldShowRequestPermissionRationale
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.databinding.ActivityMainBinding
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.IntentMessage
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.MeshServiceNotifications
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.service.startService
import com.geeksville.mesh.ui.ChannelFragment
import com.geeksville.mesh.ui.ContactsFragment
import com.geeksville.mesh.ui.DebugFragment
import com.geeksville.mesh.ui.QuickChatSettingsFragment
import com.geeksville.mesh.ui.SettingsFragment
import com.geeksville.mesh.ui.UsersFragment
import com.geeksville.mesh.ui.components.ScannedQrCodeDialog
import com.geeksville.mesh.ui.map.MapFragment
import com.geeksville.mesh.ui.navigateToMessages
import com.geeksville.mesh.ui.navigateToNavGraph
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.Exceptions
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.getPackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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
                showSnackbar(getString(R.string.notification_denied), Snackbar.LENGTH_SHORT)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val prefs = UIViewModel.getPreferences(this)
            // First run: migrate in-app language prefs to appcompat
            val lang = prefs.getString("lang", LanguageUtils.SYSTEM_DEFAULT)
            if (lang != LanguageUtils.SYSTEM_MANAGED) LanguageUtils.migrateLanguagePrefs(prefs)
            info("in-app language is ${LanguageUtils.getLocale()}")
            // Set theme
            AppCompatDelegate.setDefaultNightMode(
                prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            )
            // First run: show AppIntroduction
            if (!prefs.getBoolean("app_intro_completed", false)) {
                startActivity(Intent(this, AppIntroduction::class.java))
            }
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

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val mainTab = tab?.position ?: 0
                model.setCurrentTab(mainTab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.composeView.setContent {
            val connState by model.connectionState.collectAsStateWithLifecycle()
            val channels by model.channels.collectAsStateWithLifecycle()
            val requestChannelSet by model.requestChannelSet.collectAsStateWithLifecycle()

            AppTheme {
                if (connState.isConnected()) {
                    if (requestChannelSet != null) {
                        ScannedQrCodeDialog(
                            channels = channels,
                            incoming = requestChannelSet!!,
                            onDismiss = model::clearRequestChannelUrl,
                            onConfirm = model::setChannels,
                        )
                    }
                }
            }
        }

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

    // Handle any intents that were passed into us
    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        when (appLinkAction) {
            Intent.ACTION_VIEW -> {
                debug("Asked to open a channel URL - ask user if they want to switch to that channel.  If so send the config to the radio")
                appLinkData?.let(model::requestChannelUrl)

                // We now wait for the device to connect, once connected, we ask the user if they want to switch to the new channel
            }

            MeshServiceNotifications.OPEN_MESSAGE_ACTION -> {
                val contactKey =
                    intent.getStringExtra(MeshServiceNotifications.OPEN_MESSAGE_EXTRA_CONTACT_KEY)
                showMessages(contactKey)
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                showSettingsPage()
            }

            Intent.ACTION_MAIN -> {
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    val json = Json
                    try {
                        val intentMessage: IntentMessage = json.decodeFromString(text)
                        if (intentMessage.autoSend) {
                            model.sendMessage(intentMessage.message, intentMessage.contactKey)
                            showMessages(intentMessage.contactKey, intentMessage.contactName)
                        } else {
                            showMessagesPreInit(
                                intentMessage.contactKey,
                                intentMessage.contactName,
                                intentMessage.message
                            )
                        }
                    } catch (e: SerializationException) {
                        debug("Failed to decode JSON: ${e.message}; falling back to default message")
                        shareMessages(text)
                    }
                }
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
        mainScope.cancel("Activity going away")
        super.onDestroy()
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

    // Called when we gain/lose a connection to our mesh radio
    private fun onMeshConnectionChanged(newConnection: MeshService.ConnectionState) {
        if (newConnection == MeshService.ConnectionState.CONNECTED) {
            serviceRepository.meshService?.let { service ->
                try {
                    val info: MyNodeInfo? = service.myNodeInfo // this can be null

                    if (info != null) {
                        val isOld = info.minAppVersion > BuildConfig.VERSION_CODE
                        if (isOld) {
                            showAlert(R.string.app_too_old, R.string.must_update)
                        } else {
                            // If we are already doing an update don't put up a dialog or try to get device info
                            val isUpdating = service.updateStatus >= 0
                            if (!isUpdating) {
                                val curVer = DeviceVersion(info.firmwareVersion ?: "0.0.0")

                                if (curVer < MeshService.minDeviceVersion) {
                                    showAlert(R.string.firmware_too_old, R.string.firmware_old)
                                }
                            }
                        }
                    }
                } catch (ex: RemoteException) {
                    warn("Abandoning connect $ex, because we probably just lost device connection")
                }
                // if provideLocation enabled: Start providing location (from phone GPS) to mesh
                if (model.provideLocation.value == true) {
                    service.startProvideLocation()
                }
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
        }
    }

    private fun showSnackbar(msgId: Int) {
        try {
            Snackbar.make(binding.root, msgId, Snackbar.LENGTH_LONG).show()
        } catch (ex: IllegalStateException) {
            errormsg("Snackbar couldn't find view for msgId $msgId")
        }
    }

    private fun showSnackbar(msg: String, duration: Int = Snackbar.LENGTH_INDEFINITE) {
        try {
            Snackbar.make(binding.root, msg, duration)
                .apply { view.findViewById<TextView>(R.id.snackbar_text).isSingleLine = false }
                .setAction(R.string.okay) {
                    // dismiss
                }
                .show()
        } catch (ex: IllegalStateException) {
            errormsg("Snackbar couldn't find view for msgString $msg")
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

    private var connectionJob: Job? = null

    private val mesh = object :
        ServiceClient<IMeshService>({
            IMeshService.Stub.asInterface(it)
        }) {
        override fun onConnected(service: IMeshService) {
            connectionJob = mainScope.handledLaunch {
                serviceRepository.setMeshService(service)

                try {
                    val connectionState =
                        MeshService.ConnectionState.valueOf(service.connectionState())

                    // We won't receive a notify for the initial state of connection, so we force an update here
                    onMeshConnectionChanged(connectionState)
                } catch (ex: RemoteException) {
                    errormsg("Device error during init ${ex.message}")
                } finally {
                    connectionJob = null
                }

                debug("connected to mesh service, connectionState=${model.connectionState.value}")
            }
        }

        override fun onDisconnected() {
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
        unbindMeshService()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()

        model.connectionState.asLiveData().observe(this) { state ->
            onMeshConnectionChanged(state)
            updateConnectionStatusImage(state)
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

        // Call showSnackbar() whenever [snackbarText] updates with a non-null value
        model.snackbarText.observe(this) { text ->
            if (text is Int) showSnackbar(text)
            if (text is String) showSnackbar(text)
            if (text != null) model.clearSnackbarText()
        }

        model.currentTab.observe(this) {
            binding.tabLayout.getTabAt(it)?.select()
        }

        model.tracerouteResponse.observe(this) { response ->
            MaterialAlertDialogBuilder(this)
                .setCancelable(false)
                .setTitle(R.string.traceroute)
                .setMessage(response ?: return@observe)
                .setPositiveButton(R.string.okay) { _, _ -> }
                .show()

            model.clearTracerouteResponse()
        }

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
            // App is probably shutting down, ignore
            errormsg("Bind of MeshService failed")
        }

        val bonded = model.bondedAddress != null
        if (!bonded) showSettingsPage()
    }

    private fun showSettingsPage() {
        binding.pager.currentItem = 5
    }

    private fun showMessages(contactKey: String?) {
        model.setCurrentTab(0)
        if (contactKey != null) {
            supportFragmentManager.navigateToMessages(contactKey)
        }
    }

    private fun showMessagesPreInit(contactKey: String?, contactName: String?, message: String?) {
        model.setCurrentTab(0)
        if (contactKey != null && contactName != null && message != null) {
            supportFragmentManager.navigateToPreInitMessages(contactKey, contactName, message)
        }
    }

    private fun shareMessages(message: String?) {
        model.setCurrentTab(0)
        if (message != null) {
            supportFragmentManager.navigateToShareMessage(message)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        model.actionBarMenu = menu

        updateConnectionStatusImage(model.connectionState.value)

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
                if (item.isChecked) {
                    postPing()
                } else {
                    handler.removeCallbacksAndMessages(null)
                }
                return true
            }

            R.id.radio_config -> {
                supportFragmentManager.navigateToNavGraph()
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

    // Theme functions

    private fun chooseThemeDialog() {

        // Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.choose_theme))

        val styles = mapOf(
            getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
            getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
            getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        // Load preferences and its value
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
        // Prepare dialog and its items
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(getString(R.string.preferences_language))

        val languageTags = LanguageUtils.getLanguageTags(this)

        // Load preferences and its value
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
