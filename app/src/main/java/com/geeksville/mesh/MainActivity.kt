/*
 * Copyright (c) 2025 Meshtastic LLC
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

import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.setPadding
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.android.dpToPx
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.android.getNotificationPermissions
import com.geeksville.mesh.android.hasBluetoothPermission
import com.geeksville.mesh.android.hasNotificationPermission
import com.geeksville.mesh.android.permissionMissing
import com.geeksville.mesh.android.rationaleDialog
import com.geeksville.mesh.android.shouldShowRequestPermissionRationale
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.Route
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.service.startService
import com.geeksville.mesh.ui.MainMenuAction
import com.geeksville.mesh.ui.MainScreen
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.Exceptions
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.getPackageInfoCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), Logging {

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
                model.showSnackbar(permissionMissing)
            }
            requestedEnable = false
            bluetoothViewModel.permissionsUpdated()
        }

    private val notificationPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.entries.all { it.value }) {
                info("Notification permissions granted")
                checkAlertDnD()
            } else {
                warn("Notification permissions denied")
                model.showSnackbar(getString(R.string.notification_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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

        setContent {
            Box(Modifier.safeDrawingPadding()) {
                AppTheme {
                    MainScreen(viewModel = model, onAction = ::onMainMenuAction)
                }
            }
        }

        // Handle any intent
        handleIntent(intent)
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

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                debug("USB device attached")
                showSettingsPage()
            }

            Intent.ACTION_MAIN -> {
            }

            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (text != null) {
                    createShareIntent(text).send()
                }
            }

            else -> {
                warn("Unexpected action $appLinkAction")
            }
        }
    }

    private fun createShareIntent(message: String): PendingIntent {
        val deepLink = "${Route.URI}/share?message=$message"
        val startActivityIntent = Intent(
            Intent.ACTION_VIEW, deepLink.toUri(),
            this, MainActivity::class.java
        )

        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(startActivityIntent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
        }
        return resultPendingIntent!!
    }

    private fun createSettingsIntent(): PendingIntent {
        val deepLink = "${Route.URI}/settings"
        val startActivityIntent = Intent(
            Intent.ACTION_VIEW, deepLink.toUri(),
            this, MainActivity::class.java
        )

        val resultPendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(startActivityIntent)
            getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
        }
        return resultPendingIntent!!
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
        if (it.resultCode == RESULT_OK) {
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

        debug("showAlert: $titleText")
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
            checkNotificationPermissions()
        }
    }

    private fun checkNotificationPermissions() {
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

    @Suppress("MagicNumber")
    private fun checkAlertDnD() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ) {
            val prefs = UIViewModel.getPreferences(this)
            val rationaleShown = prefs.getBoolean("dnd_rationale_shown", false)
            if (!rationaleShown && hasNotificationPermission()) {
                fun showAlertAppNotificationSettings() {
                    val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    intent.putExtra(Settings.EXTRA_CHANNEL_ID, "my_alerts")
                    startActivity(intent)
                }
                val message = Html.fromHtml(
                    getString(R.string.alerts_dnd_request_text),
                    Html.FROM_HTML_MODE_COMPACT
                )
                val messageTextView = TextView(this).also {
                    it.text = message
                    it.movementMethod = LinkMovementMethod.getInstance()
                    it.setPadding(dpToPx(16f))
                }
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.alerts_dnd_request_title)
                    .setView(messageTextView)
                    .setNeutralButton(R.string.cancel) { dialog, _ ->
                        prefs.edit { putBoolean("dnd_rationale_shown", true) }
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.channel_settings) { dialog, _ ->
                        showAlertAppNotificationSettings()
                        prefs.edit { putBoolean("dnd_rationale_shown", true) }
                        dialog.dismiss()
                    }
                    .setCancelable(false).show()
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

    private var connectionJob: Job? = null

    private val mesh = object : ServiceClient<IMeshService>(IMeshService.Stub::asInterface) {
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
            BIND_AUTO_CREATE + BIND_ABOVE_CLIENT
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
            errormsg("Bind of MeshService failed${ex.message}")
        }
    }

    private fun showSettingsPage() {
        createSettingsIntent().send()
    }

    private fun onMainMenuAction(action: MainMenuAction) {
        when (action) {
            MainMenuAction.ABOUT -> {
                getVersionInfo()
            }
            MainMenuAction.EXPORT_MESSAGES -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/csv"
                    putExtra(Intent.EXTRA_TITLE, "rangetest.csv")
                }
                createDocumentLauncher.launch(intent)
            }
            MainMenuAction.THEME -> {
                chooseThemeDialog()
            }
            MainMenuAction.LANGUAGE -> {
                chooseLangDialog()
            }
            MainMenuAction.SHOW_INTRO -> {
                startActivity(Intent(this, AppIntroduction::class.java))
            }
            else -> {}
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
