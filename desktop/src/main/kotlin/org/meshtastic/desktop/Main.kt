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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
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
import kotlinx.coroutines.flow.first
import org.koin.core.context.startKoin
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.desktop.data.DesktopPreferencesDataSource
import org.meshtastic.desktop.di.desktopModule
import org.meshtastic.desktop.di.desktopPlatformModule
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.desktop.ui.DesktopMainScreen
import org.meshtastic.desktop.ui.navSavedStateConfig
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

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun main() = application(exitProcessOnExit = false) {
    Logger.i { "Meshtastic Desktop — Starting" }

    val koinApp = remember { startKoin { modules(desktopPlatformModule(), desktopModule()) } }
    val systemLocale = remember { Locale.getDefault() }

    // Start the mesh service processing chain (desktop equivalent of Android's MeshService)
    val meshServiceController = remember { koinApp.koin.get<MeshServiceOrchestrator>() }
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }

    val uiPrefs = remember { koinApp.koin.get<UiPreferencesDataSource>() }
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
    val appIcon = painterResource("icon.png")

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
        icon = appIcon,
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
        Window(
            onCloseRequest = { isAppVisible = false },
            title = "Meshtastic Desktop",
            icon = appIcon,
            state = windowState,
        ) {
            val backStack =
                rememberNavBackStack(navSavedStateConfig, TopLevelDestination.Connections.route as NavKey)

            MenuBar {
                Menu("File") {
                    Item("Settings", shortcut = KeyShortcut(Key.Comma, meta = true)) {
                        if (
                            TopLevelDestination.Settings != TopLevelDestination.fromNavKey(backStack.lastOrNull())
                        ) {
                            backStack.add(TopLevelDestination.Settings.route)
                            while (backStack.size > 1) {
                                backStack.removeAt(0)
                            }
                        }
                    }
                    Separator()
                    Item("Quit", shortcut = KeyShortcut(Key.Q, meta = true)) { exitApplication() }
                }
                Menu("View") {
                    Item("Toggle Theme", shortcut = KeyShortcut(Key.T, meta = true, shift = true)) {
                        val newTheme = if (isDarkTheme) 1 else 2 // 1 = Light, 2 = Dark
                        uiPrefs.setTheme(newTheme)
                    }
                }
                Menu("Navigate") {
                    Item("Conversations", shortcut = KeyShortcut(Key.One, meta = true)) {
                        backStack.add(TopLevelDestination.Conversations.route)
                        while (backStack.size > 1) {
                            backStack.removeAt(0)
                        }
                    }
                    Item("Nodes", shortcut = KeyShortcut(Key.Two, meta = true)) {
                        backStack.add(TopLevelDestination.Nodes.route)
                        while (backStack.size > 1) {
                            backStack.removeAt(0)
                        }
                    }
                    Item("Map", shortcut = KeyShortcut(Key.Three, meta = true)) {
                        backStack.add(TopLevelDestination.Map.route)
                        while (backStack.size > 1) {
                            backStack.removeAt(0)
                        }
                    }
                    Item("Connections", shortcut = KeyShortcut(Key.Four, meta = true)) {
                        backStack.add(TopLevelDestination.Connections.route)
                        while (backStack.size > 1) {
                            backStack.removeAt(0)
                        }
                    }
                }
                Menu("Help") { Item("About") { backStack.add(SettingsRoutes.About) } }
            }

            // Providing localePref via a staticCompositionLocalOf forces the entire subtree to
            // recompose when the locale changes — CMP Resources' rememberResourceEnvironment then
            // re-reads Locale.current and all stringResource() calls update.  Unlike key(), this
            // preserves remembered state (including the navigation backstack).
            CompositionLocalProvider(LocalAppLocale provides localePref) {
                AppTheme(darkTheme = isDarkTheme) { DesktopMainScreen(backStack) }
            }
        }
    }
}
