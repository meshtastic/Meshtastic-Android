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
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.android.BindFailedException
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.ServiceClient
import com.geeksville.mesh.concurrent.handledLaunch
import com.geeksville.mesh.model.BluetoothViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.navigation.DEEP_LINK_BASE_URI
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.ServiceRepository
import com.geeksville.mesh.service.startService
import com.geeksville.mesh.ui.MainMenuAction
import com.geeksville.mesh.ui.MainScreen
import com.geeksville.mesh.ui.common.theme.AppTheme
import com.geeksville.mesh.ui.common.theme.MODE_DYNAMIC
import com.geeksville.mesh.ui.intro.AppIntroductionScreen
import com.geeksville.mesh.ui.sharing.toSharedContact
import com.geeksville.mesh.util.LanguageUtils
import com.geeksville.mesh.util.getPackageInfoCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity(),
    Logging {
    private val bluetoothViewModel: BluetoothViewModel by viewModels()
    private val model: UIViewModel by viewModels()

    @Inject internal lateinit var serviceRepository: ServiceRepository

    private var showAppIntro by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = UIViewModel.getPreferences(this)
        if (savedInstanceState == null) {
            val lang = prefs.getString("lang", LanguageUtils.SYSTEM_DEFAULT)
            if (lang != LanguageUtils.SYSTEM_MANAGED) LanguageUtils.migrateLanguagePrefs(prefs)
            info("in-app language is ${LanguageUtils.getLocale()}")

            if (!prefs.getBoolean("app_intro_completed", false)) {
                showAppIntro = true
            } else {
                (application as GeeksvilleApplication).askToRate(this)
            }
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val theme by model.theme.collectAsState()
            val dynamic = theme == MODE_DYNAMIC
            val dark =
                when (theme) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
                    else -> isSystemInDarkTheme()
                }

            AppTheme(dynamicColor = dynamic, darkTheme = dark) {
                val view = LocalView.current
                if (!view.isInEditMode) {
                    SideEffect { AppCompatDelegate.setDefaultNightMode(theme) }
                }

                if (showAppIntro) {
                    AppIntroductionScreen(
                        onDone = {
                            prefs.edit { putBoolean("app_intro_completed", true) }
                            showAppIntro = false
                            (application as GeeksvilleApplication).askToRate(this@MainActivity)
                        },
                    )
                } else {
                    MainScreen(
                        uIViewModel = model,
                        bluetoothViewModel = bluetoothViewModel,
                        onAction = ::onMainMenuAction,
                    )
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        when (appLinkAction) {
            Intent.ACTION_VIEW -> {
                appLinkData?.let {
                    debug("App link data: $it")
                    if (it.path?.startsWith("/e/") == true || it.path?.startsWith("/E/") == true) {
                        debug("App link data is a channel set")
                        model.requestChannelUrl(it)
                    } else if (it.path?.startsWith("/v/") == true || it.path?.startsWith("/V/") == true) {
                        val sharedContact = it.toSharedContact()
                        debug("App link data is a shared contact: ${sharedContact.user.longName}")
                        model.setSharedContactRequested(sharedContact)
                    } else {
                        debug("App link data is not a channel set")
                    }
                }
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                debug("USB device attached")
                showSettingsPage()
            }

            Intent.ACTION_MAIN -> {}

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
        val startActivityIntent =
            Intent(Intent.ACTION_VIEW, deepLink.toUri(), this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val resultPendingIntent: PendingIntent? =
            TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(startActivityIntent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
            }
        return resultPendingIntent!!
    }

    private fun createSettingsIntent(): PendingIntent {
        val deepLink = "$DEEP_LINK_BASE_URI/connections"
        val startActivityIntent =
            Intent(Intent.ACTION_VIEW, deepLink.toUri(), this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        val resultPendingIntent: PendingIntent? =
            TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(startActivityIntent)
                getPendingIntent(0, PendingIntent.FLAG_IMMUTABLE)
            }
        return resultPendingIntent!!
    }

    private val createRangetestLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                it.data?.data?.let { file_uri -> model.saveRangetestCSV(file_uri) }
            }
        }

    private var serviceSetupJob: Job? = null

    private val mesh =
        object : ServiceClient<IMeshService>(IMeshService.Stub::asInterface) {
            override fun onConnected(service: IMeshService) {
                serviceSetupJob?.cancel()
                serviceSetupJob =
                    lifecycleScope.handledLaunch {
                        serviceRepository.setMeshService(service)
                        debug("connected to mesh service, connectionState=${model.connectionState.value}")
                    }
            }

            override fun onDisconnected() {
                serviceSetupJob?.cancel()
                serviceRepository.setMeshService(null)
            }
        }

    private fun bindMeshService() {
        debug("Binding to mesh service!")
        try {
            MeshService.startService(this)
        } catch (ex: Exception) {
            errormsg("Failed to start service from activity - but ignoring because bind will work ${ex.message}")
        }

        mesh.connect(this, MeshService.createIntent(), BIND_AUTO_CREATE + BIND_ABOVE_CLIENT)
    }

    override fun onStart() {
        super.onStart()

        try {
            bindMeshService()
        } catch (ex: BindFailedException) {
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

            MainMenuAction.EXPORT_RANGETEST -> {
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/csv"
                        putExtra(Intent.EXTRA_TITLE, "rangetest.csv")
                    }
                createRangetestLauncher.launch(intent)
            }

            MainMenuAction.THEME -> {
                chooseThemeDialog()
            }

            MainMenuAction.LANGUAGE -> {
                chooseLangDialog()
            }

            MainMenuAction.SHOW_INTRO -> {
                showAppIntro = true
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

    private fun chooseThemeDialog() {
        val styles =
            mapOf(
                getString(R.string.dynamic) to MODE_DYNAMIC,
                getString(R.string.theme_light) to AppCompatDelegate.MODE_NIGHT_NO,
                getString(R.string.theme_dark) to AppCompatDelegate.MODE_NIGHT_YES,
                getString(R.string.theme_system) to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            )

        val prefs = UIViewModel.getPreferences(this)
        val theme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        debug("Theme from prefs: $theme")
        model.showAlert(
            title = getString(R.string.choose_theme),
            message = "",
            choices = styles.mapValues { (_, value) -> { model.setTheme(value) } },
        )
    }

    private fun chooseLangDialog() {
        val languageTags = LanguageUtils.getLanguageTags(this)
        val lang = LanguageUtils.getLocale()
        debug("Lang from prefs: $lang")
        val langMap = languageTags.mapValues { (_, value) -> { LanguageUtils.setLocale(value) } }

        model.showAlert(title = getString(R.string.preferences_language), message = "", choices = langMap)
    }
}
