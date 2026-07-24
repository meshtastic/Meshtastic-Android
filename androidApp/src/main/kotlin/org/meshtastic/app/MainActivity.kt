/*
 * Copyright (c) 2026 Meshtastic LLC
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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import co.touchlab.kermit.Logger
import com.eygraber.uri.toKmpUri
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.app.intro.AnalyticsIntro
import org.meshtastic.app.map.getMapViewProvider
import org.meshtastic.app.map.sitePlannerAvailable
import org.meshtastic.app.node.component.InlineMap
import org.meshtastic.app.node.metrics.getTracerouteMapOverlayInsets
import org.meshtastic.app.ui.MainScreen
import org.meshtastic.core.barcode.rememberBarcodeScanner
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.network.repository.UsbRepository
import org.meshtastic.core.nfc.NfcScannerEffect
import org.meshtastic.core.nfc.NfcWriterEffect
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channel_invalid
import org.meshtastic.core.service.MeshService
import org.meshtastic.core.service.startService
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.EventFontResolver
import org.meshtastic.core.ui.theme.EventTheme
import org.meshtastic.core.ui.theme.EventThemeToggle
import org.meshtastic.core.ui.theme.LocalEventTheme
import org.meshtastic.core.ui.theme.LocalEventThemeToggle
import org.meshtastic.core.ui.theme.MODE_DYNAMIC
import org.meshtastic.core.ui.util.LocalAnalyticsIntroProvider
import org.meshtastic.core.ui.util.LocalBarcodeScannerProvider
import org.meshtastic.core.ui.util.LocalBarcodeScannerSupported
import org.meshtastic.core.ui.util.LocalDiscoveryMapProvider
import org.meshtastic.core.ui.util.LocalEventBranding
import org.meshtastic.core.ui.util.LocalInlineMapProvider
import org.meshtastic.core.ui.util.LocalMapMainScreenProvider
import org.meshtastic.core.ui.util.LocalMapViewProvider
import org.meshtastic.core.ui.util.LocalNfcScannerProvider
import org.meshtastic.core.ui.util.LocalNfcScannerSupported
import org.meshtastic.core.ui.util.LocalNfcWriterProvider
import org.meshtastic.core.ui.util.LocalNodeMapScreenProvider
import org.meshtastic.core.ui.util.LocalNodeTrackMapProvider
import org.meshtastic.core.ui.util.LocalSitePlannerAvailable
import org.meshtastic.core.ui.util.LocalTracerouteMapOverlayInsetsProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapProvider
import org.meshtastic.core.ui.util.LocalTracerouteMapScreenProvider
import org.meshtastic.core.ui.util.accentColorOrNull
import org.meshtastic.core.ui.util.showToast
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.NO_DEVICE_SELECTED
import org.meshtastic.feature.intro.AppIntroductionScreen
import org.meshtastic.feature.intro.IntroViewModel
import org.meshtastic.feature.map.MapScreen
import org.meshtastic.feature.map.SharedMapViewModel
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.TracerouteMapScreen

class MainActivity : AppCompatActivity() {
    private val model: UIViewModel by viewModel()

    private val usbRepository: UsbRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SKIP_ONBOARDING, false)) {
            model.onAppIntroCompleted()
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
            val theme by model.theme.collectAsStateWithLifecycle()
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
                AppTheme(dynamicColor = dynamic, darkTheme = dark) {
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

    override fun onStart() {
        super.onStart()
        MeshService.startService(this)
    }

    override fun onResume() {
        super.onResume()
        // Belt-and-suspenders for the Android 12+ attach-intent quirk: if the activity is
        // resumed while a USB device is already attached (e.g. process restart, returning
        // from another app), the manifest-declared attach intent may have already fired
        // before UsbRepository was constructed. Re-poll deviceList here so the UI reflects
        // reality without requiring the user to physically replug.
        usbRepository.refreshState()
    }

    @Suppress("LongMethod")
    @Composable
    private fun AppCompositionLocals(content: @Composable () -> Unit) {
        val eventEdition by model.eventEdition.collectAsStateWithLifecycle()
        val eventThemeEnabled by model.eventThemeEnabled.collectAsStateWithLifecycle()
        val eventFontResolver = koinInject<EventFontResolver>()
        // Resolve the ambient event theme once per edition: an accent wash (works on any flavor) and/or downloadable
        // fonts (Google flavor only). Applied app-wide only while on event firmware and not opted out.
        val eventAccent = eventEdition?.accentColorOrNull()
        val resolvedEventFonts =
            remember(eventEdition, eventFontResolver) { eventFontResolver.resolve(eventEdition?.theme?.fonts) }
        CompositionLocalProvider(
            LocalEventBranding provides eventEdition,
            LocalEventTheme provides
                if (eventThemeEnabled && eventEdition != null) {
                    EventTheme(accent = eventAccent, fonts = resolvedEventFonts)
                } else {
                    null
                },
            LocalEventThemeToggle provides
                EventThemeToggle(enabled = eventThemeEnabled, onChange = model::setEventThemeEnabled),
            LocalBarcodeScannerProvider provides { onResult -> rememberBarcodeScanner(onResult) },
            LocalNfcScannerProvider provides { onResult, onDisabled -> NfcScannerEffect(onResult, onDisabled) },
            LocalNfcWriterProvider provides { url, onResult, onDisabled -> NfcWriterEffect(url, onResult, onDisabled) },
            LocalBarcodeScannerSupported provides true,
            LocalNfcScannerSupported provides true,
            LocalAnalyticsIntroProvider provides { AnalyticsIntro() },
            LocalMapViewProvider provides getMapViewProvider(),
            LocalSitePlannerAvailable provides sitePlannerAvailable(),
            LocalInlineMapProvider provides { node, modifier -> InlineMap(node, modifier) },
            LocalNodeTrackMapProvider provides
                { destNum, positions, modifier, selectedPositionTime, onPositionSelected ->
                    org.meshtastic.app.map.node.NodeTrackMap(
                        destNum,
                        positions,
                        modifier,
                        selectedPositionTime,
                        onPositionSelected,
                    )
                },
            LocalTracerouteMapOverlayInsetsProvider provides getTracerouteMapOverlayInsets(),
            LocalTracerouteMapProvider provides
                { overlay, nodePositions, onMappableCountChanged, modifier ->
                    org.meshtastic.app.map.traceroute.TracerouteMap(
                        tracerouteOverlay = overlay,
                        tracerouteNodePositions = nodePositions,
                        onMappableCountChanged = onMappableCountChanged,
                        modifier = modifier,
                    )
                },
            LocalDiscoveryMapProvider provides
                { userLat, userLon, nodes, modifier ->
                    org.meshtastic.app.map.discovery.DiscoveryMap(userLat, userLon, nodes, modifier)
                },
            LocalNodeMapScreenProvider provides
                { destNum, onNavigateUp ->
                    val vm = koinViewModel<NodeMapViewModel>()
                    vm.setDestNum(destNum)
                    org.meshtastic.app.map.node.NodeMapScreen(vm, onNavigateUp = onNavigateUp)
                },
            LocalTracerouteMapScreenProvider provides
                { destNum, requestId, logUuid, onNavigateUp ->
                    val metricsViewModel = koinViewModel<MetricsViewModel> { parametersOf(destNum) }
                    metricsViewModel.setNodeId(destNum)

                    TracerouteMapScreen(
                        metricsViewModel = metricsViewModel,
                        requestId = requestId,
                        logUuid = logUuid,
                        onNavigateUp = onNavigateUp,
                    )
                },
            LocalMapMainScreenProvider provides
                { onClickNodeChip, navigateToNodeDetails, waypointId ->
                    val viewModel = koinViewModel<SharedMapViewModel>()
                    MapScreen(
                        viewModel = viewModel,
                        onClickNodeChip = onClickNodeChip,
                        navigateToNodeDetails = navigateToNodeDetails,
                        waypointId = waypointId,
                    )
                },
            content = content,
        )
    }

    @Suppress("NestedBlockDepth")
    private fun handleIntent(intent: Intent) {
        val appLinkAction = intent.action
        val appLinkData: Uri? = intent.data

        when (appLinkAction) {
            Intent.ACTION_VIEW -> handleViewIntent(appLinkData)

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
                showConnectionsPageIfNoDeviceSelected()
            }

            Intent.ACTION_MAIN -> {}

            Intent.ACTION_SEND -> handleSendIntent(intent)

            else -> {
                Logger.w { "Unexpected action $appLinkAction" }
            }
        }
    }

    private fun handleViewIntent(uri: Uri?) {
        when {
            uri == null -> {}

            // A file handed to us via "Open in Meshtastic" (Files, Share Sheet, drag-and-drop) rather than a
            // meshtastic:// / https deep link — import it as a map overlay.
            uri.scheme == "content" || uri.scheme == "file" -> importMapFile(uri)

            else -> handleMeshtasticUri(uri)
        }
    }

    private fun handleSendIntent(intent: Intent) {
        val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
        when {
            stream != null -> importMapFile(stream)

            // shared .geojson/.kml file (e.g. Site Planner)
            text != null -> createShareIntent(text).send()
        }
    }

    private fun handleMeshtasticUri(uri: Uri) {
        Logger.d { "Handling Meshtastic URI: $uri" }

        model.handleDeepLink(uri.toKmpUri()) { lifecycleScope.launch { showToast(Res.string.channel_invalid) } }
    }

    /**
     * Hand a map file received via an OS "Open in / Send to Meshtastic" intent to the map, then bring the Map tab
     * forward so the imported overlay is visible. Only the Google-flavor map consumes this (see [MapFileImportBus]);
     * the read grant on [uri] lives as long as this activity, which is long enough for the map to copy the file in.
     */
    private fun importMapFile(uri: Uri) {
        Logger.d { "Importing shared map file: $uri" }
        MapFileImportBus.pending.value = uri
        handleMeshtasticUri("$DEEP_LINK_BASE_URI/map".toUri())
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

    private fun showConnectionsPageIfNoDeviceSelected() {
        val selectedAddress = model.currentDeviceAddressFlow.value
        if (!selectedAddress.isNullOrBlank() && selectedAddress != NO_DEVICE_SELECTED) return

        handleMeshtasticUri("$DEEP_LINK_BASE_URI/connections".toUri())
    }

    private companion object {
        const val EXTRA_SKIP_ONBOARDING = "skip_onboarding"
    }
}
