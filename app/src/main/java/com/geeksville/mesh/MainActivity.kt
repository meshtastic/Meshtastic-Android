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
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.android.getBluetoothPermissions
import com.geeksville.mesh.android.getNotificationPermissions
import com.geeksville.mesh.android.hasBluetoothPermission
import com.geeksville.mesh.android.hasNotificationPermission
import com.geeksville.mesh.android.permissionMissing
import com.geeksville.mesh.android.shouldShowRequestPermissionRationale
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.DEEP_LINK_BASE_URI
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.service.startService
import com.geeksville.mesh.ui.MainMenuAction
import com.geeksville.mesh.ui.MainScreen
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.theme.MODE_DYNAMIC
import com.geeksville.mesh.util.Exceptions
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.getPackageInfoCompat
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
            // First run: show AppIntroduction
            if (!prefs.getBoolean("app_intro_completed", false)) {
                startActivity(Intent(this, AppIntroduction::class.java))
            }
            // Ask user to rate in play store
            (application as GeeksvilleApplication).askToRate(this)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val theme by model.theme.collectAsState()
            val dynamic = theme == MODE_DYNAMIC
            val dark = when (theme) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
                else -> isSystemInDarkTheme()
            }

            AppTheme(
                dynamicColor = dynamic,
                darkTheme = dark,
            ) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect {
                        AppCompatDelegate.setDefaultNightMode(theme)
                    }
                }
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.background)
                        .systemBarsPadding()
                ) {
                    MainScreen(
                        viewModel = model,
                        onAction = ::onMainMenuAction
                    )
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
                appLinkData?.let {
                    debug("App link data: $it")
                    if (it.path?.startsWith("/e/") == true ||
                        it.path?.startsWith("/E/") == true
                    ) {
                        debug("App link data is a channel set")
                        model.requestChannelUrl(it)
                    } else {
                        debug("App link data is not a channel set")
                    }
                }
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
        val deepLink = "$DEEP_LINK_BASE_URI/share?message=$message"
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
        val deepLink = "$DEEP_LINK_BASE_URI/settings"
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

    // Called when we gain/lose a connection to our mesh radio
    private fun onMeshConnectionChanged(newConnection: MeshService.ConnectionState) {
        if (newConnection == MeshService.ConnectionState.CONNECTED) {
            serviceRepository.meshService?.let { service ->
                try {
                    val info: MyNodeInfo? = service.myNodeInfo // this can be null

                    if (info != null) {
                        val isOld = info.minAppVersion > BuildConfig.VERSION_CODE
                        if (isOld) {
                            model.showAlert(
                                getString(R.string.app_too_old),
                                getString(R.string.must_update),
                                dismissable = false,
                            )
                        } else {
                            // If we are already doing an update don't put up a dialog or try to get device info
                            val isUpdating = service.updateStatus >= 0
                            if (!isUpdating) {
                                val curVer = DeviceVersion(info.firmwareVersion ?: "0.0.0")
                                if (curVer < MeshService.minDeviceVersion) {
                                    val title = getString(R.string.firmware_too_old)
                                    val message = getString(R.string.firmware_old)
                                    model.showAlert(title, message, dismissable = false)
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
                } else {
                    service.stopProvideLocation()
                }
            }
            checkNotificationPermissions()
        }
    }

    private fun checkNotificationPermissions() {
        if (!hasNotificationPermission()) {
            val notificationPermissions = getNotificationPermissions()
            if (shouldShowRequestPermissionRationale(notificationPermissions)) {
                val title = getString(R.string.notification_required)
                val message = getString(R.string.why_notification_required)
                model.showAlert(
                    title = title,
                    message = message,
                    onConfirm = {
                        notificationPermissionsLauncher.launch(notificationPermissions)
                    },
                )
            } else {
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
                model.showAlert(
                    title = getString(R.string.alerts_dnd_request_title),
                    html = getString(R.string.alerts_dnd_request_text),
                    onConfirm = {
                        showAlertAppNotificationSettings()
                    },
                    dismissable = true
                ).also {
                    prefs.edit { putBoolean("dnd_rationale_shown", true) }
                }
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
                    val title = getString(R.string.required_permissions)
                    val message = permissionMissing
                    model.showAlert(
                        title = title,
                        message = message,
                        onConfirm = {
                            bluetoothPermissionsLauncher.launch(bluetoothPermissions)
                        },
                    )
                }
            }
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
        val styles = mapOf(
            getString(R.string.dynamic) to MODE_DYNAMIC,
            getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
            getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
            getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        )

        // Load preferences and its value
        val prefs = UIViewModel.getPreferences(this)
        val theme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        debug("Theme from prefs: $theme")
        // map theme keys to function to set theme
        model.showAlert(
            title = getString(R.string.choose_theme),
            message = "",
            choices = styles.mapValues { (_, value) ->
                {
                    model.setTheme(value)
                }
            },
        )
    }

    private fun chooseLangDialog() {
        val languageTags = LanguageUtils.getLanguageTags(this)
        // Load preferences and its value
        val lang = LanguageUtils.getLocale()
        debug("Lang from prefs: $lang")
        // map lang keys to function to set locale
        val langMap = languageTags.mapValues { (_, value) ->
            {
                LanguageUtils.setLocale(value)
            }
        }

        model.showAlert(
            title = getString(R.string.preferences_language),
            message = "",
            choices = langMap,
        )
    }
}
