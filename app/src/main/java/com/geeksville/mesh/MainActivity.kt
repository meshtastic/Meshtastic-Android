/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.MainScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.core.android.AndroidEnvironment
import no.nordicsemi.kotlin.ble.environment.android.compose.LocalEnvironmentOwner
import org.meshtastic.core.model.util.dispatchMeshtasticUri
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.channel_invalid
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.feature.intro.AppIntroductionScreen
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: UIViewModel by viewModels()

    /**
     * Activity-lifecycle-aware client that binds to the mesh service. Note: This is used implicitly as it registers
     * itself as a LifecycleObserver in its init block.
     */
    @Inject internal lateinit var meshServiceClient: MeshServiceClient

    @Inject internal lateinit var androidEnvironment: AndroidEnvironment

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        setContent {
            val theme by model.theme.collectAsStateWithLifecycle()
            val dynamic = theme == MODE_DYNAMIC
            val dark =
                when (theme) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    else -> isSystemInDarkTheme()
                }

            // Apply modern edge-to-edge drawing with theme-aware system bars
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            )

            // Ensure the navigation bar remains seamless on modern Android versions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }

            @Suppress("SpreadOperator")
            CompositionLocalProvider(*(LocalEnvironmentOwner provides androidEnvironment)) {
                AppTheme(dynamicColor = dynamic, darkTheme = dark) {
                    val appIntroCompleted by model.appIntroCompleted.collectAsStateWithLifecycle()

                    // Signal to the system that the initial UI is "fully drawn"
                    // once we've decided whether to show the intro or the main screen.
                    ReportDrawnWhen { true }

                    if (appIntroCompleted) {
                        MainScreen(uIViewModel = model)
                    } else {
                        AppIntroductionScreen(onDone = { model.onAppIntroCompleted() })
                    }
                }
            }
        }

        // Listen for new intents (e.g. deep links, NFC) without overriding onNewIntent
        addOnNewIntentListener { intent -> handleIntent(intent) }

        handleIntent(intent)
    }

    @Suppress("NestedBlockDepth")
    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        when (appLinkAction) {
            Intent.ACTION_VIEW -> {
                appLinkData?.let { handleMeshtasticUri(it) }
            }

            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val rawMessages =
                    IntentCompat.getParcelableArrayExtra(
                        intent,
                        NfcAdapter.EXTRA_NDEF_MESSAGES,
                        NdefMessage::class.java,
                    )
                if (rawMessages != null) {
                    for (rawMsg in rawMessages) {
                        val msg = rawMsg as NdefMessage
                        for (record in msg.records) {
                            record.toUri()?.let { handleMeshtasticUri(it) }
                        }
                    }
                }
            }

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                Logger.d { "USB device attached" }
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
                Logger.w { "Unexpected action $appLinkAction" }
            }
        }
    }

    private fun handleMeshtasticUri(uri: Uri) {
        Logger.d { "Handling Meshtastic URI: $uri" }
        uri.dispatchMeshtasticUri(
            onChannel = { model.setRequestChannelSet(it) },
            onContact = { model.setSharedContactRequested(it) },
            onInvalid = { lifecycleScope.launch { showToast(Res.string.channel_invalid) } },
        )
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
