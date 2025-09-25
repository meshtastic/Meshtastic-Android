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
import android.graphics.Color
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.android.GeeksvilleApplication
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.MainScreen
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import com.geeksville.mesh.ui.intro.AppIntroductionScreen
import com.geeksville.mesh.ui.sharing.toSharedContact
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity :
    AppCompatActivity(),
    Logging {
    private val model: UIViewModel by viewModels()

    // This is aware of the Activity lifecycle and handles binding to the mesh service.
    @Inject internal lateinit var meshServiceClient: MeshServiceClient

    @Inject internal lateinit var uiPreferencesDataSource: UiPreferencesDataSource

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge(
            // Disable three-button navbar scrim on pre-Q devices
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Disable three-button navbar scrim
            window.setNavigationBarContrastEnforced(false)
        }

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val appIntroCompleted = uiPreferencesDataSource.appIntroCompleted.value
                if (appIntroCompleted) {
                    (application as GeeksvilleApplication).askToRate(this@MainActivity)
                }
            }
        }

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

                val appIntroCompleted by model.appIntroCompleted.collectAsStateWithLifecycle()
                if (appIntroCompleted) {
                    MainScreen(uIViewModel = model)
                } else {
                    AppIntroductionScreen(
                        onDone = {
                            model.onAppIntroCompleted()
                            (application as GeeksvilleApplication).askToRate(this@MainActivity)
                        },
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

    private fun showSettingsPage() {
        createSettingsIntent().send()
    }
}
