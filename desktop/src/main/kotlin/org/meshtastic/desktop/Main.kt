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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.meshtastic.core.common.util.MeshtasticUri
import org.meshtastic.core.navigation.MeshtasticNavSavedStateConfig
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.navigateTopLevel
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.desktop.data.DesktopPreferencesDataSource
import org.meshtastic.desktop.di.desktopModule
import org.meshtastic.desktop.di.desktopPlatformModule
import org.meshtastic.desktop.ui.DesktopMainScreen
import java.awt.Desktop
import java.util.Locale

/**
 * Meshtastic Desktop — the first non-Android target for the shared KMP module graph.
 *
 * Launches a Compose Desktop window with a Navigation 3 shell that mirrors the Android app's navigation architecture:
 * shared routes from `core:navigation`, a `NavigationRail` for top-level destinations, and `NavDisplay` for rendering
 * the current backstack entry.
 */
/**
 * Static CompositionLocal used as a recomposition trigger for locale changes. When the value changes,
 * [staticCompositionLocalOf] forces the **entire subtree** under the provider to recompose — unlike [key] which
 * destroys and recreates state (including the navigation backstack). During recomposition, CMP Resources'
 * `rememberResourceEnvironment` re-reads `Locale.current` (which wraps `java.util.Locale.getDefault()`) and picks up
 * the new locale, causing all `stringResource()` calls to resolve in the updated language.
 */
private val LocalAppLocale = staticCompositionLocalOf { "" }

private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024L * 1024L // 64 MiB
private const val DISK_CACHE_MAX_BYTES = 32L * 1024L * 1024L // 32 MiB

/**
 * Loads a [Painter] from a Java classpath resource path (e.g. `"icon.png"`).
 *
 * This replaces the deprecated `androidx.compose.ui.res.painterResource(String)` API. Desktop native-distribution icons
 * (`.icns`, `.ico`) remain in `src/main/resources` for the packaging plugin; this helper reads the same directory at
 * runtime.
 */
@Composable
private fun classpathPainterResource(path: String): Painter {
    val bitmap: ImageBitmap =
        remember(path) {
            val bytes = Thread.currentThread().contextClassLoader!!.getResourceAsStream(path)!!.readAllBytes()
            Image.makeFromEncoded(bytes).toComposeImageBitmap()
        }
    return remember(bitmap) { BitmapPainter(bitmap) }
}

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun main(args: Array<String>) = application(exitProcessOnExit = false) {
    Logger.i { "Meshtastic Desktop — Starting" }

    val koinApp = remember { startKoin { modules(desktopPlatformModule(), desktopModule()) } }
    val systemLocale = remember { Locale.getDefault() }
    val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }

    LaunchedEffect(args) {
        args.forEach { arg ->
            if (
                arg.startsWith("meshtastic://") ||
                arg.startsWith("http://meshtastic.org") ||
                arg.startsWith("https://meshtastic.org")
            ) {
                uiViewModel.handleDeepLink(MeshtasticUri(arg)) {
                    Logger.e { "Invalid Meshtastic URI passed via args: $arg" }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_URI)) {
            Desktop.getDesktop().setOpenURIHandler { event ->
                val uriStr = event.uri.toString()
                uiViewModel.handleDeepLink(MeshtasticUri(uriStr)) { Logger.e { "Invalid URI from OS: $uriStr" } }
            }
        }
    }

    // Start the mesh service processing chain (desktop equivalent of Android's MeshService)
    val meshServiceController = remember { koinApp.koin.get<MeshServiceOrchestrator>() }
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }

    val uiPrefs = remember { koinApp.koin.get<UiPrefs>() }
    val themePref by uiPrefs.theme.collectAsState(initial = -1) // -1 is SYSTEM usually
    val localePref by uiPrefs.locale.collectAsState(initial = "")

    // Apply persisted locale to the JVM default synchronously so CMP Resources sees
    // it during the current composition frame. Empty string falls back to the startup
    // system locale captured before any app-specific override was applied.
    Locale.setDefault(localePref.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag) ?: systemLocale)

    val isDarkTheme =
        when (themePref) {
            1 -> false // MODE_NIGHT_NO
            2 -> true // MODE_NIGHT_YES
            else -> isSystemInDarkTheme()
        }

    var isAppVisible by remember { mutableStateOf(true) }
    var isWindowReady by remember { mutableStateOf(false) }
    val trayState = rememberTrayState()
    val appIcon = classpathPainterResource("icon.png")

    @Suppress("DEPRECATION")
    val trayIcon =
        androidx.compose.ui.res.painterResource(
            if (isSystemInDarkTheme()) "tray_icon_white.svg" else "tray_icon_black.svg",
        )

    val notificationManager = remember { koinApp.koin.get<DesktopNotificationManager>() }
    val desktopPrefs = remember { koinApp.koin.get<DesktopPreferencesDataSource>() }
    val windowState = rememberWindowState()

    LaunchedEffect(Unit) {
        notificationManager.notifications.collect { notification ->
            Logger.d { "Main.kt: Received notification for Tray: title=${notification.title}" }
            trayState.sendNotification(notification)
        }
    }

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

        isWindowReady = true

        snapshotFlow {
            val x = if (windowState.position.isSpecified) windowState.position.x.value else Float.NaN
            val y = if (windowState.position.isSpecified) windowState.position.y.value else Float.NaN
            listOf(windowState.size.width.value, windowState.size.height.value, x, y)
        }
            .collect { bounds ->
                desktopPrefs.setWindowBounds(width = bounds[0], height = bounds[1], x = bounds[2], y = bounds[3])
            }
    }

    Tray(
        state = trayState,
        icon = trayIcon,
        tooltip = "Meshtastic Desktop",
        onAction = { isAppVisible = true },
        menu = {
            Item("Show Meshtastic", onClick = { isAppVisible = true })
            Item(
                "Test Notification",
                onClick = {
                    trayState.sendNotification(
                        Notification(
                            "Meshtastic",
                            "This is a test notification from the System Tray",
                            Notification.Type.Info,
                        ),
                    )
                },
            )
            Item("Quit", onClick = ::exitApplication)
        },
    )

    if (isWindowReady && isAppVisible) {
        val backStack =
            rememberNavBackStack(MeshtasticNavSavedStateConfig, TopLevelDestination.Connections.route as NavKey)

        Window(
            onCloseRequest = { isAppVisible = false },
            title = "Meshtastic Desktop",
            icon = appIcon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown || !event.isMetaPressed) return@Window false
                when {
                    // ⌘Q  → Quit
                    event.key == Key.Q -> {
                        exitApplication()
                        true
                    }
                    // ⌘,  → Settings
                    event.key == Key.Comma -> {
                        if (
                            TopLevelDestination.Settings != TopLevelDestination.fromNavKey(backStack.lastOrNull())
                        ) {
                            backStack.navigateTopLevel(TopLevelDestination.Settings.route)
                        }
                        true
                    }
                    // ⌘⇧T → Toggle theme
                    event.key == Key.T && event.isShiftPressed -> {
                        uiPrefs.setTheme(if (isDarkTheme) 1 else 2)
                        true
                    }
                    // ⌘1  → Conversations
                    event.key == Key.One -> {
                        backStack.navigateTopLevel(TopLevelDestination.Conversations.route)
                        true
                    }
                    // ⌘2  → Nodes
                    event.key == Key.Two -> {
                        backStack.navigateTopLevel(TopLevelDestination.Nodes.route)
                        true
                    }
                    // ⌘3  → Map
                    event.key == Key.Three -> {
                        backStack.navigateTopLevel(TopLevelDestination.Map.route)
                        true
                    }
                    // ⌘4  → Connections
                    event.key == Key.Four -> {
                        backStack.navigateTopLevel(TopLevelDestination.Connections.route)
                        true
                    }
                    // ⌘/  → About
                    event.key == Key.Slash -> {
                        backStack.add(SettingsRoutes.About)
                        true
                    }
                    else -> false
                }
            },
        ) {
            // Configure Coil ImageLoader for desktop with SVG decoding and network fetching.
            // This is the desktop equivalent of the Android app's NetworkModule.provideImageLoader().
            setSingletonImageLoaderFactory { context ->
                val cacheDir = System.getProperty("user.home") + "/.meshtastic/image_cache"
                ImageLoader.Builder(context)
                    .components {
                        add(KtorNetworkFetcherFactory())
                        add(SvgDecoder.Factory())
                    }
                    .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_MAX_BYTES).build() }
                    .diskCache {
                        DiskCache.Builder().directory(cacheDir.toPath()).maxSizeBytes(DISK_CACHE_MAX_BYTES).build()
                    }
                    .crossfade(true)
                    .build()
            }

            // Providing localePref via a staticCompositionLocalOf forces the entire subtree to
            // recompose when the locale changes — CMP Resources' rememberResourceEnvironment then
            // re-reads Locale.current and all stringResource() calls update.  Unlike key(), this
            // preserves remembered state (including the navigation backstack).
            CompositionLocalProvider(LocalAppLocale provides localePref) {
                AppTheme(darkTheme = isDarkTheme) { DesktopMainScreen(uiViewModel) }
            }
        }
    }
}
