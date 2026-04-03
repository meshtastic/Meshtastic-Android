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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toPath
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import org.meshtastic.core.common.util.MeshtasticUri
import org.meshtastic.core.database.desktopDataDir
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.rememberMultiBackstack
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

/** Meshtastic Desktop — the first non-Android target for the shared KMP module graph. */
private val LocalAppLocale = staticCompositionLocalOf { "" }

private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024L * 1024L // 64 MiB
private const val DISK_CACHE_MAX_BYTES = 32L * 1024L * 1024L // 32 MiB

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
@OptIn(ExperimentalCoilApi::class)
fun main(args: Array<String>) = application(exitProcessOnExit = false) {
    Logger.i { "Meshtastic Desktop — Starting" }

    val koinApp = remember { startKoin { modules(desktopPlatformModule(), desktopModule()) } }
    val systemLocale = remember { Locale.getDefault() }
    val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
    val httpClient = remember { koinApp.koin.get<HttpClient>() }

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

    val meshServiceController = remember { koinApp.koin.get<MeshServiceOrchestrator>() }
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }

    val uiPrefs = remember { koinApp.koin.get<UiPrefs>() }
    val themePref by uiPrefs.theme.collectAsState(initial = -1)
    val localePref by uiPrefs.locale.collectAsState(initial = "")

    Locale.setDefault(localePref.takeIf { it.isNotEmpty() }?.let(Locale::forLanguageTag) ?: systemLocale)

    val isDarkTheme =
        when (themePref) {
            1 -> false
            2 -> true
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
        notificationManager.notifications.collect { notification -> trayState.sendNotification(notification) }
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
            Item("Quit", onClick = ::exitApplication)
        },
    )

    if (isWindowReady && isAppVisible) {
        val multiBackstack = rememberMultiBackstack(TopLevelDestination.Connections.route)
        val backStack = multiBackstack.activeBackStack

        Window(
            onCloseRequest = { isAppVisible = false },
            title = "Meshtastic Desktop",
            icon = appIcon,
            state = windowState,
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown || !event.isMetaPressed) return@Window false
                when {
                    event.key == Key.Q -> {
                        exitApplication()
                        true
                    }
                    event.key == Key.Comma -> {
                        if (
                            TopLevelDestination.Settings != TopLevelDestination.fromNavKey(backStack.lastOrNull())
                        ) {
                            multiBackstack.navigateTopLevel(TopLevelDestination.Settings.route)
                        }
                        true
                    }
                    event.key == Key.One -> {
                        multiBackstack.navigateTopLevel(TopLevelDestination.Conversations.route)
                        true
                    }
                    event.key == Key.Two -> {
                        multiBackstack.navigateTopLevel(TopLevelDestination.Nodes.route)
                        true
                    }
                    event.key == Key.Three -> {
                        multiBackstack.navigateTopLevel(TopLevelDestination.Map.route)
                        true
                    }
                    event.key == Key.Four -> {
                        multiBackstack.navigateTopLevel(TopLevelDestination.Connections.route)
                        true
                    }
                    event.key == Key.Slash -> {
                        backStack.add(SettingsRoutes.About)
                        true
                    }
                    else -> false
                }
            },
        ) {
            setSingletonImageLoaderFactory { context ->
                val cacheDir = desktopDataDir() + "/image_cache_v3"
                ImageLoader.Builder(context)
                    .components {
                        add(KtorNetworkFetcherFactory(httpClient = httpClient))
                        // Render SVGs to a bitmap on Desktop to avoid Skiko vector rendering artifacts
                        // that show up as solid/black hardware images.
                        add(SvgDecoder.Factory(renderToBitmap = true))
                    }
                    .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_MAX_BYTES).build() }
                    .diskCache {
                        DiskCache.Builder().directory(cacheDir.toPath()).maxSizeBytes(DISK_CACHE_MAX_BYTES).build()
                    }
                    .crossfade(true)
                    .build()
            }

            CompositionLocalProvider(LocalAppLocale provides localePref) {
                AppTheme(darkTheme = isDarkTheme) { DesktopMainScreen(uiViewModel, multiBackstack) }
            }
        }
    }
}
