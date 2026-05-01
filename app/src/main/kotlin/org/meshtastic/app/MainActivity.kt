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
package org.meshtastic.app

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
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.ReportDrawnWhen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaControlIntent
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.eygraber.uri.toKmpUri
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.app.cast.TvDashboardPresentation
import org.meshtastic.app.ui.MainScreen
import org.meshtastic.core.barcode.rememberBarcodeScanner
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.nfc.NfcScannerEffect
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.service.MeshServiceClient
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.util.LocalAnalyticsIntroProvider
import org.meshtastic.core.ui.util.LocalBarcodeScannerProvider
import org.meshtastic.core.ui.util.LocalBarcodeScannerSupported
import org.meshtastic.core.ui.util.LocalNfcScannerProvider
import org.meshtastic.core.ui.util.LocalNfcScannerSupported
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.intro.AppIntroductionScreen
import org.meshtastic.feature.intro.IntroViewModel

class MainActivity : FragmentActivity() {
    private val model: UIViewModel by viewModel()

    private val usbRepository: UsbRepository by inject()

    /**
     * Activity-lifecycle-aware client that binds to the mesh service. Note: This is used implicitly as it registers
     * itself as a LifecycleObserver in its init block.
     */
    internal val meshServiceClient: MeshServiceClient by inject { parametersOf(this) }

    private var castContext: CastContext? = null
    private var castSession: CastSession? = null
    
    private var mediaRouter: MediaRouter? = null
    private var presentation: android.app.Presentation? = null

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Logger.d { "Cast: Route selected: ${route.name}" }
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updatePresentation(route)
        }

        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Logger.d { "Cast: Route unselected: ${route.name}" }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            updatePresentation(null)
        }

        override fun onRoutePresentationDisplayChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
            Logger.d { "Cast: Presentation display changed for ${route.name}. Display: ${route.presentationDisplay}" }
            updatePresentation(route)
        }
    }

    private fun updatePresentation(route: MediaRouter.RouteInfo?) {
        val display = route?.presentationDisplay
        Logger.d { "Cast: updatePresentation with display: $display" }
        if (display != null) {
            if (presentation == null || presentation?.display != display) {
                Logger.i { "Cast: Showing TvDashboardPresentation on display ${display.displayId}" }
                presentation?.dismiss()
                presentation = TvDashboardPresentation(this, display)
                try {
                    presentation?.show()
                } catch (e: WindowManager.InvalidDisplayException) {
                    Logger.e(e) { "Cast: Failed to show presentation" }
                    presentation = null
                }
            }
        } else {
            if (presentation != null) {
                Logger.i { "Cast: Dismissing presentation" }
                presentation?.dismiss()
                presentation = null
            }
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            castSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            castSession = session
            invalidateOptionsMenu()
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            castSession = null
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        // Eagerly evaluate lazy Koin dependency so it registers its LifecycleObserver
        meshServiceClient.hashCode()

        super.onCreate(savedInstanceState)
        
        mediaRouter = MediaRouter.getInstance(this)

        try {
            castContext = CastContext.getSharedInstance(this)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to get CastContext" }
        }

        enableEdgeToEdge()

        // Explicitly set the cutout mode to ALWAYS for Android 15+ to satisfy Play Console recommendations.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        // Ensure the navigation bar remains seamless on modern Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            // Bridge Koin-provided ImageLoader (with flavor-specific HttpClient, SVG, debug logger)
            // to Coil's singleton so all AsyncImage composables use the custom configuration.
            setSingletonImageLoaderFactory { get<ImageLoader>() }

            val theme by model.theme.collectAsStateWithLifecycle()
            val contrastLevelValue by model.contrastLevel.collectAsStateWithLifecycle()
            val contrastLevel = org.meshtastic.core.ui.theme.ContrastLevel.fromValue(contrastLevelValue)
            val dynamic = theme == MODE_DYNAMIC
            val dark =
                when (theme) {
                    AppCompatDelegate.MODE_NIGHT_YES -> true
                    AppCompatDelegate.MODE_NIGHT_NO -> false
                    else -> isSystemInDarkTheme()
                }

            // Update system bar style when theme changes
            androidx.compose.runtime.SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
            }

            AppCompositionLocals {
                AppTheme(dynamicColor = dynamic, darkTheme = dark, contrastLevel = contrastLevel) {
                    val appIntroCompleted by model.appIntroCompleted.collectAsStateWithLifecycle()

                    // Signal to the system that the initial UI is "fully drawn"
                    // once we've decided whether to show the intro or the main screen.
                    ReportDrawnWhen { true }

                    if (appIntroCompleted) {
                        MainScreen()
                    } else {
                        val introViewModel = koinViewModel<IntroViewModel>()
                        AppIntroductionScreen(onDone = { model.onAppIntroCompleted() }, viewModel = introViewModel)
                    }
                }
            }
        }

        // Listen for new intents (e.g. deep links, NFC) without overriding onNewIntent
        addOnNewIntentListener { intent -> handleIntent(intent) }

        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        castContext?.sessionManager?.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
        castSession = castContext?.sessionManager?.currentCastSession
        
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
            .build()
        mediaRouter?.addCallback(selector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        
        // Belt-and-suspenders for the Android 12+ attach-intent quirk
        // resumed while a USB device is already attached (e.g. process restart, returning
        // from another app), the manifest-declared attach intent may have already fired
        // before UsbRepository was constructed. Re-poll deviceList here so the UI reflects
        // reality without requiring the user to physically replug.
        usbRepository.refreshState()
    }

    override fun onPause() {
        super.onPause()
        // If we are currently casting, we don't want to remove the callback yet
        // as it might kill the presentation when the screen turns off.
        if (presentation == null) {
            mediaRouter?.removeCallback(mediaRouterCallback)
        }
        castContext?.sessionManager?.removeSessionManagerListener(sessionManagerListener, CastSession::class.java)
    }

    override fun onStop() {
        super.onStop()
        // We only dismiss if the activity is truly being destroyed or finished
        if (isFinishing) {
            presentation?.dismiss()
            presentation = null
            mediaRouter?.removeCallback(mediaRouterCallback)
        }
    }

    @Composable
    private fun AppCompositionLocals(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalBarcodeScannerProvider provides { onResult -> rememberBarcodeScanner(onResult) },
            LocalNfcScannerProvider provides { onResult, onDisabled -> NfcScannerEffect(onResult, onDisabled) },
            LocalBarcodeScannerSupported provides true,
            LocalNfcScannerSupported provides true,
            LocalAnalyticsIntroProvider provides { AnalyticsIntro() },
        ) {
            MapLocals(content = content)
        }
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
                // Android 12+ delivers ACTION_USB_DEVICE_ATTACHED only to manifest-declared
                // receivers, so the runtime-registered UsbBroadcastReceiver inside UsbRepository
                // never sees this event. Forward it explicitly so the serialDevices StateFlow
                // refreshes and the device shows up in the Connect → Serial tab.
                usbRepository.refreshState()
                showConnectionsPage()
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

        model.handleDeepLink(uri.toKmpUri()) { lifecycleScope.launch { showToast(Res.string.channel_invalid) } }
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

    private fun createConnectionsIntent(): PendingIntent {
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

    private fun showConnectionsPage() {
        createConnectionsIntent().send()
    }
}
