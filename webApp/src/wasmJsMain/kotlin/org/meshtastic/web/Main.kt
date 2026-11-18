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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.plugin.module.dsl.startKoin
import org.meshtastic.core.database.WebDatabaseAccess
import org.meshtastic.core.database.webDatabaseAccess
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.web_database_busy
import org.meshtastic.core.resources.web_database_unavailable
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.web.di.WebKoinApp
import org.meshtastic.web.ui.WebMainScreen

/** The wasmJs entry point. `webApp` is the `<div>` id in `index.html` and the webpack output name. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    val koinApp = startKoin<WebKoinApp> {}
    Logger.i { "Meshtastic Web — Starting" }

    ComposeViewport("webApp") {
        val access by webDatabaseAccess.collectAsState()
        when (access) {
            WebDatabaseAccess.Ready -> {
                val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
                MeshServiceLifecycle()
                ThemeAndContent(uiViewModel)
            }

            WebDatabaseAccess.Busy -> DatabaseUnavailable(Res.string.web_database_busy)

            WebDatabaseAccess.Failed -> DatabaseUnavailable(Res.string.web_database_unavailable)

            WebDatabaseAccess.Starting -> Unit
        }
    }
}

/** Shown instead of the app while this tab cannot hold the database; nothing has opened it yet. */
@Composable
private fun DatabaseUnavailable(message: StringResource) {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(message),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
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

/** Resolves the theme preference and renders [WebMainScreen]. The browser's language drives resource resolution. */
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
