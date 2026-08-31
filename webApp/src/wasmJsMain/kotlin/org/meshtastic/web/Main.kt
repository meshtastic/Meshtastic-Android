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
package org.meshtastic.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.web.di.webModule
import org.meshtastic.web.ui.WebMainScreen

/**
 * Meshtastic Web — the wasmJs entry point, using Compose Multiplatform's current [ComposeViewport] bootstrap
 * (`CanvasBasedWindow` is deprecated; confirmed against the pinned `1.12.0` via JetBrains' own `compose-multiplatform`
 * repo examples — `examples/nav_cupcake/webApp`, `examples/imageviewer/webApp`, and the `1.12.0` CHANGELOG — since
 * JetBrains' hosted docs pages didn't carry the exact API surface at time of writing). `viewportContainerId` ("webApp")
 * matches `index.html`'s `<div id="webApp">` and `outputModuleName`/ `commonWebpackConfig.outputFileName` in
 * `build.gradle.kts`.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koinApp = startKoin { modules(webModule()) }
    Logger.i { "Meshtastic Web — Starting" }

    ComposeViewport("webApp") {
        val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
        MeshServiceLifecycle()
        ThemeAndContent(uiViewModel)
    }
}

/** Starts [MeshServiceOrchestrator] on composition and stops it on disposal — same shape as desktopApp's. */
@Composable
private fun MeshServiceLifecycle() {
    val meshServiceController = koinInject<MeshServiceOrchestrator>()
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }
}

/**
 * Resolves the user's theme preference and renders [WebMainScreen]. No locale override on this v0 pass — the browser's
 * own `Accept-Language`/`navigator.language` already drives Compose Multiplatform resource resolution, and
 * `uiPrefs.locale`'s manual override (desktopApp's `Locale.setDefault`) has no JS-locale equivalent wired up yet.
 */
@Suppress("ViewModelForwarding")
@Composable
private fun ThemeAndContent(uiViewModel: UIViewModel) {
    val uiPrefs = koinInject<UiPrefs>()
    val themePref by uiPrefs.theme.collectAsState(initial = -1)
    val isDarkTheme =
        when (themePref) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

    val multiBackstack = rememberMultiBackstack(defaultStartDestination(uiViewModel))

    AppTheme(darkTheme = isDarkTheme) { WebMainScreen(uiViewModel, multiBackstack) }
}

/** Lands on Connections for first-run / no-device-selected; otherwise on Nodes — same rule desktopApp uses. */
private fun defaultStartDestination(uiViewModel: UIViewModel): NavKey {
    val address = uiViewModel.currentDeviceAddressFlow.value
    return if (address.isNullOrBlank() || address == "n") {
        TopLevelDestination.Connect.route
    } else {
        TopLevelDestination.Nodes.route
    }
}
