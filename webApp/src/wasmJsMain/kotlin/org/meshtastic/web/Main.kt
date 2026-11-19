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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import co.touchlab.kermit.Logger
import org.koin.compose.koinInject
import org.koin.core.KoinApplication
import org.koin.plugin.module.dsl.startKoin
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.WebDatabaseAccess
import org.meshtastic.core.database.webDatabaseAccess
import org.meshtastic.core.navigation.NotificationDeepLinks
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.web_database_unavailable
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.service.UnreadBadgeSync
import org.meshtastic.core.ui.navigation.startDestination
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.theme.isDarkTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.web.di.WebKoinApp
import org.meshtastic.web.lifecycle.TabHandoff
import org.meshtastic.web.navigation.WebIntroGate
import org.meshtastic.web.navigation.isWebDeepLink
import org.meshtastic.web.platform.NotificationClicks
import org.meshtastic.web.platform.cacheLoadedResources
import org.meshtastic.web.platform.requestPersistentStorage
import org.meshtastic.web.ui.DatabaseBusy
import org.meshtastic.web.ui.DatabaseUnavailable
import org.meshtastic.web.ui.MovedToAnotherTab
import org.meshtastic.web.ui.PreloadEmojiFont
import org.meshtastic.web.ui.WebLoading
import org.meshtastic.web.ui.WebMainScreen

/**
 * A DOMException the browser rejects a promise with from its own callback, as Web Bluetooth's `gatt.connect()` does,
 * has no `stack`. Kotlin/Wasm's `JsException.jsStack` reads it as non-null, so printing such an exception throws a
 * NullPointerException inside kotlinx.coroutines and wedges the coroutine that failed. An empty stack on the prototype
 * fills the gap; an exception's own stack still wins.
 */
private fun giveDomExceptionsAStack(): Unit = js(
    """{
        if (typeof DOMException !== 'undefined' && !Object.getOwnPropertyDescriptor(DOMException.prototype, 'stack')) {
            Object.defineProperty(DOMException.prototype, 'stack', { get() { return ''; }, configurable: true });
        }
    }""",
)

/**
 * Compose resolves resources and [androidx.compose.ui.text.intl.Locale.current] from `navigator.languages`, which a
 * page cannot set. This getter answers with the app's language while one is chosen ([chooseLanguage]) and with the
 * browser's otherwise, the override Compose Multiplatform's resource docs give for web.
 */
private fun installLanguageOverride(): Unit = js(
    """{
        const browser = Object.getOwnPropertyDescriptor(Navigator.prototype, 'languages');
        Object.defineProperty(Navigator.prototype, 'languages', {
            configurable: true,
            get() { return globalThis.__meshtasticLanguage ? [globalThis.__meshtasticLanguage] : browser.get.call(this); },
        });
    }""",
)

/** Sets the app's language as a BCP 47 tag, or the browser's with a blank one. */
@Suppress("UnusedParameter") // read by name inside the js() body
private fun chooseLanguage(tag: String): Unit = js("{ globalThis.__meshtasticLanguage = tag || null; }")

private fun removeStaticLoader(): Unit =
    js("{ const loader = document.getElementById('loading'); if (loader) loader.remove(); }")

/** The wasmJs entry point. `webApp` is the `<div>` id in `index.html` and the webpack output name. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    giveDomExceptionsAStack()
    installLanguageOverride()
    // Before Compose: the navigation shell rewrites the address, and a notification may have opened this page.
    NotificationClicks.start()
    val koinApp = startKoin<WebKoinApp> {}
    Logger.i { "Meshtastic Web — Starting" }

    ComposeViewport("webApp") {
        // Compose has drawn by the time effects run, so index.html's static spinner can go.
        LaunchedEffect(Unit) { removeStaticLoader() }
        // Reading webDatabaseAccess starts the worker that queues for the database, so a tab that just gave the
        // database away does not read it until the user takes the database back.
        var waitingForUser by remember { mutableStateOf(TabHandoff.movedAway) }
        var asked by remember { mutableStateOf(false) }
        if (waitingForUser) {
            MovedToAnotherTab(
                onUseHere = {
                    waitingForUser = false
                    asked = true
                    TabHandoff.requestRelease()
                },
            )
        } else {
            DatabaseGate(koinApp, askedForDatabase = asked)
        }
    }
}

@Composable
private fun DatabaseGate(koinApp: KoinApplication, askedForDatabase: Boolean) {
    val access by webDatabaseAccess.collectAsState()
    when (access) {
        WebDatabaseAccess.Ready -> {
            DisposableEffect(Unit) {
                val handoff = TabHandoff.honorReleaseRequests()
                onDispose { handoff.stop() }
            }
            val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
            MeshServiceLifecycle()
            AppShellServices(uiViewModel)
            ThemeAndContent(uiViewModel)
        }

        WebDatabaseAccess.Busy -> DatabaseBusy(alreadyAsked = askedForDatabase, onUseHere = TabHandoff::requestRelease)

        WebDatabaseAccess.Failed -> DatabaseUnavailable(Res.string.web_database_unavailable)

        WebDatabaseAccess.Starting -> WebLoading()
    }
}

/** Starts [MeshServiceOrchestrator] on composition and stops it on disposal, as desktopApp's does. */
@Composable
private fun MeshServiceLifecycle() {
    val meshServiceController = koinInject<MeshServiceOrchestrator>()
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }
}

/**
 * The installed-app side of the shell, once the database is open: keeps the origin's storage from eviction, puts the
 * unread count on the app icon, hands the service worker what this page loaded, and opens notification clicks.
 */
@Suppress("ViewModelForwarding")
@Composable
private fun AppShellServices(uiViewModel: UIViewModel) {
    val unreadBadgeSync = koinInject<UnreadBadgeSync>()
    LaunchedEffect(Unit) {
        requestPersistentStorage()
        cacheLoadedResources()
    }
    LaunchedEffect(unreadBadgeSync) { unreadBadgeSync.run() }
    LaunchedEffect(uiViewModel) {
        NotificationClicks.deepLinks.collect { deepLink ->
            if (isWebDeepLink(deepLink)) {
                uiViewModel.handleDeepLink(CommonUri.parse(NotificationDeepLinks.uri(deepLink)))
            } else {
                Logger.i { "Notification destination has no web screen: ${deepLink.substringBefore('/')}" }
            }
        }
    }
}

/** Applies the theme and language preferences and renders [WebMainScreen]. */
@Suppress("ViewModelForwarding")
@Composable
private fun ThemeAndContent(uiViewModel: UIViewModel) {
    val uiPrefs = koinInject<UiPrefs>()
    val themePref by uiPrefs.theme.collectAsState(initial = -1)
    val languagePref by uiPrefs.locale.collectAsState(initial = "")
    chooseLanguage(languagePref)

    val multiBackstack = rememberMultiBackstack(startDestination(uiViewModel.currentDeviceAddressFlow.value))

    PreloadEmojiFont()

    // Compose reads the language when it resolves a resource, not as state, so a new language recomposes from here.
    // The back stack is kept above it.
    key(languagePref) {
        AppTheme(darkTheme = isDarkTheme(themePref)) {
            WebIntroGate(uiViewModel) { WebMainScreen(uiViewModel, multiBackstack) }
        }
    }
}
