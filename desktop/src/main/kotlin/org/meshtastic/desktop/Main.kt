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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import org.koin.core.context.startKoin
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.desktop.di.desktopModule
import org.meshtastic.desktop.di.desktopPlatformModule
import org.meshtastic.desktop.radio.DesktopMeshServiceController
import org.meshtastic.desktop.ui.DesktopMainScreen
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

fun main() = application {
    Logger.i { "Meshtastic Desktop — Starting" }

    val koinApp = remember { startKoin { modules(desktopPlatformModule(), desktopModule()) } }
    val systemLocale = remember { Locale.getDefault() }

    // Start the mesh service processing chain (desktop equivalent of Android's MeshService)
    val meshServiceController = remember { koinApp.koin.get<DesktopMeshServiceController>() }
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "Meshtastic Desktop",
        state = rememberWindowState(width = 1024.dp, height = 768.dp),
    ) {
        // Providing localePref via a staticCompositionLocalOf forces the entire subtree to
        // recompose when the locale changes — CMP Resources' rememberResourceEnvironment then
        // re-reads Locale.current and all stringResource() calls update.  Unlike key(), this
        // preserves remembered state (including the navigation backstack).
        CompositionLocalProvider(LocalAppLocale provides localePref) {
            AppTheme(darkTheme = isDarkTheme) { DesktopMainScreen() }
        }
    }
}
