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
package org.meshtastic.desktop.tray

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Light-on-dark tray glyph, used when the system theme is dark. */
internal const val TRAY_ICON_LIGHT = "tray_icon_white.svg"

/** Dark-on-light tray glyph, used when the system theme is light. */
internal const val TRAY_ICON_DARK = "tray_icon_black.svg"

/** StatusNotifierItem `Id` — the stable application identity hosts key their own settings off. */
private const val SNI_TRAY_APP_ID = "meshtastic-desktop"

/**
 * The one thread every blocking D-Bus call is confined to.
 *
 * Two reasons it is single-threaded rather than a pool. It keeps this work off the composition thread: opening the
 * connection, claiming the bus name and registering are each a round trip, and a desktop with no watcher could
 * otherwise stall the UI. And because the thread runs one task at a time, an install and the close that follows it are
 * strictly ordered without any handshake — which is what makes disposal safe while an install is still in flight.
 */
private val trayDispatcher =
    Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "sni-tray").apply { isDaemon = true } }
        .asCoroutineDispatcher()

/** Scope for that thread. Deliberately process-lived: a close must still run after its effect is gone. */
private val trayScope = CoroutineScope(SupervisorJob() + trayDispatcher)

/**
 * Installs the Linux StatusNotifierItem tray for as long as this composition lives.
 *
 * [onRegistrationChange] reports whether a host is actually showing the item, and can fire more than once: a watcher
 * may be absent at startup and appear later, or restart and lose its registry. It decides whether the close button may
 * hide the window rather than quit — see [canHideToTray].
 *
 * Keying on [isDarkTheme] re-registers the item with a freshly rasterized icon when the system theme flips. That is
 * coarser than emitting SNI's `NewIcon` signal, but it keeps the icon correct without hand-rolling signal plumbing, and
 * the item is cheap to republish.
 *
 * Callbacks arrive on D-Bus threads, so each hops onto the composition's dispatcher before touching Compose state; the
 * D-Bus work itself never runs on the composition thread.
 */
@Composable
internal fun SniTrayEffect(
    isDarkTheme: Boolean,
    title: String,
    tooltip: String,
    menuItems: List<TrayMenuItem>,
    onActivate: () -> Unit,
    onRegistrationChange: (Boolean) -> Unit,
) {
    val currentMenu by rememberUpdatedState(menuItems)
    val currentActivate by rememberUpdatedState(onActivate)
    val currentRegistrationChange by rememberUpdatedState(onRegistrationChange)
    val uiScope = rememberCoroutineScope()

    DisposableEffect(isDarkTheme, title, tooltip) {
        // Registration reports run through a scope this effect owns, so one cannot outlive it. uiScope
        // survives a keyed re-run, so without this an install still in flight could land
        // `registered = true` after onDispose had already reported false — leaving the close button
        // trusting a tray that was queued for teardown while its replacement was not up yet.
        val registrationScope = CoroutineScope(uiScope.coroutineContext + Job())
        // Written and read only on trayDispatcher's single thread, which is what makes it safe without
        // synchronisation: the dispose below queues behind the install, whether or not it has finished.
        var tray: SniTray? = null
        trayScope.launch {
            tray =
                SniTray.install(
                    appId = SNI_TRAY_APP_ID,
                    title = title,
                    tooltip = tooltip,
                    iconRasters = loadTrayIconRasters(if (isDarkTheme) TRAY_ICON_LIGHT else TRAY_ICON_DARK),
                    menuProvider = { currentMenu },
                    onActivate = { uiScope.launch { currentActivate() } },
                    onItemClicked = { item -> uiScope.launch { item.onClick() } },
                    onRegistrationChange = { registered ->
                        registrationScope.launch { currentRegistrationChange(registered) }
                    },
                )
            if (tray == null) Logger.i { "Linux tray unavailable; the close button will quit instead" }
        }
        onDispose {
            // Silence this effect's registration reports before saying false, so a late `true` from an
            // install still in flight cannot overwrite it. Activation and menu clicks deliberately stay on
            // uiScope: those are user intent and should not be dropped mid theme-change.
            registrationScope.cancel()
            currentRegistrationChange(false)
            trayScope.launch {
                tray?.close()
                tray = null
            }
        }
    }
}
