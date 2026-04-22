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
package org.meshtastic.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.decodeToSvgPainter
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.desktop_tray_quit
import org.meshtastic.core.resources.desktop_tray_show
import org.meshtastic.core.resources.desktop_tray_tooltip
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.desktop.data.DesktopPreferencesDataSource
import org.meshtastic.desktop.di.desktopModule
import org.meshtastic.desktop.di.desktopPlatformModule
import org.meshtastic.desktop.ui.DesktopMainScreen
import java.awt.Desktop
import java.util.Locale
import coil3.util.Logger as CoilLogger

/** Meshtastic Desktop — the first non-Android target for the shared KMP module graph. */
private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024L * 1024L // 64 MiB
private const val DISK_CACHE_MAX_BYTES = 32L * 1024L * 1024L // 32 MiB

/**
 * Loads an SVG from JVM classpath resources and returns a [Painter].
 *
 * Uses the CMP 1.11 `decodeToSvgPainter` extension which replaces the deprecated `useResource`/`loadSvgPainter` pair.
 * The SVG bytes are read from the classpath because CMP `composeResources/` only supports XML vector drawables and
 * raster images — not raw SVGs. Since the desktop module is a JVM-only host shell, classpath resource access is safe.
 */
@Composable
private fun svgPainterResource(path: String, density: Density): Painter = remember(path, density) {
    val classLoader =
        requireNotNull(Thread.currentThread().contextClassLoader) {
            "Missing context class loader while loading resource: $path"
        }
    val bytes =
        requireNotNull(classLoader.getResourceAsStream(path)) { "Missing classpath resource: $path" }
            .use { it.readAllBytes() }
    bytes.decodeToSvgPainter(density)
}

@OptIn(ExperimentalCoilApi::class)
fun main(args: Array<String>) = application(exitProcessOnExit = false) {
    val koinApp = remember {
        Logger.i { "Meshtastic Desktop — Starting" }
        startKoin { modules(desktopPlatformModule(), desktopModule()) }
    }
    val systemLocale = remember { Locale.getDefault() }
    val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
    val httpClient = remember { koinApp.koin.get<HttpClient>() }

    DeepLinkHandler(args, uiViewModel)
    MeshServiceLifecycle()
    ThemeAndLocaleProvider(uiViewModel)
}

// ----- Deep link handling -----

/** Processes deep-link URIs from CLI arguments and OS-level URI handlers. */
@Composable
private fun ApplicationScope.DeepLinkHandler(args: Array<String>, uiViewModel: UIViewModel) {
    LaunchedEffect(args) {
        args.forEach { arg ->
            if (
                arg.startsWith("meshtastic://") ||
                arg.startsWith("http://meshtastic.org") ||
                arg.startsWith("https://meshtastic.org")
            ) {
                uiViewModel.handleDeepLink(CommonUri.parse(arg)) {
                    Logger.e { "Invalid Meshtastic URI passed via args: $arg" }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler { event ->
                val uriStr = event.uri.toString()
                uiViewModel.handleDeepLink(CommonUri.parse(uriStr)) { Logger.e { "Invalid URI from OS: $uriStr" } }
            }
        }
    }
}

// ----- Mesh service lifecycle -----

/** Starts [MeshServiceOrchestrator] on composition and stops it on disposal. */
@Composable
private fun MeshServiceLifecycle() {
    val meshServiceController = koinInject<MeshServiceOrchestrator>()
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }
}

// ----- Theme, locale, and application shell -----

/** Resolves the user's theme/locale preferences and renders the full application UI. */
@Composable
@OptIn(ExperimentalCoilApi::class)
private fun ApplicationScope.ThemeAndLocaleProvider(uiViewModel: UIViewModel) {
    val systemLocale = remember { Locale.getDefault() }
    val uiPrefs = koinInject<UiPrefs>()
    val themePref by uiPrefs.theme.collectAsState(initial = -1)
    val localePref by uiPrefs.locale.collectAsState(initial = "")
    val contrastLevelValue by uiPrefs.contrastLevel.collectAsState(initial = 0)
    val contrastLevel = org.meshtastic.core.ui.theme.ContrastLevel.fromValue(contrastLevelValue)
    Locale.setDefault(localePref.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag) ?: systemLocale)

    val isDarkTheme =
        when (themePref) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

    MeshtasticDesktopApp(uiViewModel, isDarkTheme, contrastLevel)
}

// ----- Application chrome (tray, window, navigation) -----

/** Composes the system tray, window, and Coil image loader. */
@Composable
@OptIn(ExperimentalCoilApi::class)
private fun ApplicationScope.MeshtasticDesktopApp(
    uiViewModel: UIViewModel,
    isDarkTheme: Boolean,
    contrastLevel: org.meshtastic.core.ui.theme.ContrastLevel,
) {
    var isAppVisible by remember { mutableStateOf(true) }
    var isWindowReady by remember { mutableStateOf(false) }
    val trayState = rememberTrayState()
    val density = LocalDensity.current
    val appIcon = svgPainterResource("tray_icon_black.svg", density)

    val trayIcon =
        svgPainterResource(if (isSystemInDarkTheme()) "tray_icon_white.svg" else "tray_icon_black.svg", density)

    val notificationManager = koinInject<DesktopNotificationManager>()
    val desktopPrefs = koinInject<DesktopPreferencesDataSource>()
    val windowState = rememberWindowState()

    LaunchedEffect(Unit) {
        notificationManager.notifications.collect { notification -> trayState.sendNotification(notification) }
    }

    WindowBoundsManager(desktopPrefs, windowState) { isWindowReady = true }

    Tray(
        state = trayState,
        icon = trayIcon,
        tooltip = stringResource(Res.string.desktop_tray_tooltip),
        onAction = { isAppVisible = true },
        menu = {
            Item(stringResource(Res.string.desktop_tray_show), onClick = { isAppVisible = true })
            Item(stringResource(Res.string.desktop_tray_quit), onClick = ::exitApplication)
        },
    )

    if (isWindowReady && isAppVisible) {
        MeshtasticWindow(uiViewModel, isDarkTheme, contrastLevel, appIcon, windowState) { isAppVisible = false }
    }
}

// ----- Window bounds persistence -----

/** Restores window geometry from preferences and persists changes via [snapshotFlow]. */
@Composable
private fun WindowBoundsManager(
    desktopPrefs: DesktopPreferencesDataSource,
    windowState: WindowState,
    onReady: () -> Unit,
) {
    LaunchedEffect(Unit) {
        val initialWidth = desktopPrefs.windowWidth.first()
        val initialHeight = desktopPrefs.windowHeight.first()
        val initialX = desktopPrefs.windowX.first()
        val initialY = desktopPrefs.windowY.first()

        windowState.size = DpSize(initialWidth.dp, initialHeight.dp)
        windowState.position =
            if (!initialX.isNaN() && !initialY.isNaN()) {
                WindowPosition(initialX.dp, initialY.dp)
            } else {
                WindowPosition(Alignment.Center)
            }

        onReady()

        snapshotFlow {
            val x = if (windowState.position.isSpecified) windowState.position.x.value else Float.NaN
            val y = if (windowState.position.isSpecified) windowState.position.y.value else Float.NaN
            listOf(windowState.size.width.value, windowState.size.height.value, x, y)
        }
            .collect { bounds ->
                desktopPrefs.setWindowBounds(width = bounds[0], height = bounds[1], x = bounds[2], y = bounds[3])
            }
    }
}

// ----- Main window with keyboard shortcuts and Coil -----

/** Renders the main application window with keyboard shortcuts, Coil image loading, and the Compose UI tree. */
@Composable
@OptIn(ExperimentalCoilApi::class)
private fun ApplicationScope.MeshtasticWindow(
    uiViewModel: UIViewModel,
    isDarkTheme: Boolean,
    contrastLevel: org.meshtastic.core.ui.theme.ContrastLevel,
    appIcon: Painter,
    windowState: WindowState,
    onCloseRequest: () -> Unit,
) {
    val multiBackstack =
        rememberMultiBackstack(
            // Land on Connections for first-run / no-device-selected; otherwise on Nodes.
            if (uiViewModel.currentDeviceAddressFlow.value.let { it.isNullOrBlank() || it == "n" }) {
                TopLevelDestination.Connections.route
            } else {
                TopLevelDestination.Nodes.route
            },
        )

    Window(
        onCloseRequest = onCloseRequest,
        title = "Meshtastic Desktop",
        icon = appIcon,
        state = windowState,
        onPreviewKeyEvent = { event -> handleKeyboardShortcut(event, multiBackstack, ::exitApplication) },
    ) {
        CoilImageLoaderSetup()
        AppTheme(darkTheme = isDarkTheme, contrastLevel = contrastLevel) {
            DesktopMainScreen(uiViewModel, multiBackstack)
        }
    }
}

/** Configures the Coil singleton [ImageLoader] with Ktor networking, SVG decoding, and caching. */
@Composable
@OptIn(ExperimentalCoilApi::class)
private fun CoilImageLoaderSetup() {
    val httpClient = koinInject<HttpClient>()
    val buildConfigProvider = koinInject<BuildConfigProvider>()

    setSingletonImageLoaderFactory { context ->
        val cacheDir = desktopDataDir() + "/image_cache_v3"
        ImageLoader.Builder(context)
            .components {
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = httpClient,
                        concurrentRequestStrategy = DeDupeConcurrentRequestStrategy(),
                    ),
                )
                // Render SVGs to a bitmap on Desktop to avoid Skiko vector rendering artifacts
                // that show up as solid/black hardware images.
                add(SvgDecoder.Factory(renderToBitmap = true))
            }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_MAX_BYTES).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.toPath()).maxSizeBytes(DISK_CACHE_MAX_BYTES).build() }
            .logger(if (buildConfigProvider.isDebug) DebugLogger(minLevel = CoilLogger.Level.Verbose) else null)
            .crossfade(true)
            .build()
    }
}

// ----- Keyboard shortcuts -----

/** Handles Cmd-key shortcuts. Returns `true` if the event was consumed. */
private fun handleKeyboardShortcut(
    event: androidx.compose.ui.input.key.KeyEvent,
    multiBackstack: MultiBackstack,
    exitApplication: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown || !event.isMetaPressed) return false
    val backStack = multiBackstack.activeBackStack
    return when (event.key) {
        Key.Q -> {
            exitApplication()
            true
        }
        Key.Comma -> {
            if (TopLevelDestination.Settings != TopLevelDestination.fromNavKey(backStack.lastOrNull())) {
                multiBackstack.navigateTopLevel(TopLevelDestination.Settings.route)
            }
            true
        }
        Key.One -> {
            multiBackstack.navigateTopLevel(TopLevelDestination.Conversations.route)
            true
        }
        Key.Two -> {
            multiBackstack.navigateTopLevel(TopLevelDestination.Nodes.route)
            true
        }
        Key.Three -> {
            multiBackstack.navigateTopLevel(TopLevelDestination.Map.route)
            true
        }
        Key.Four -> {
            multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route)
            true
        }
        Key.Slash -> {
            backStack.add(SettingsRoute.About)
            true
        }
        else -> false
    }
}
