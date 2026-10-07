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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Light-on-dark tray glyph, used when the system theme is dark. */
internal const val TRAY_ICON_LIGHT = "tray_icon_white.svg"

/** Dark-on-light tray glyph, used when the system theme is light. */
internal const val TRAY_ICON_DARK = "tray_icon_black.svg"

/** StatusNotifierItem `Id` — the stable application identity hosts key their own settings off. */
private const val SNI_TRAY_APP_ID = "meshtastic-desktop"

/**
 * The one thread every blocking D-Bus call is confined to.
 *
 * Two reasons it is single-threaded. It keeps this work off the composition thread: opening the connection, claiming
 * the bus name and registering are each a round trip, and a desktop with no watcher could otherwise stall the UI. And
 * because it runs one task at a time, an install and the close that follows it are strictly ordered without any
 * handshake — which is what makes disposal safe while an install is still in flight.
 *
 * A bare executor rather than a CoroutineScope: ordered blocking work on one thread is all this needs, and
 * `CoroutineScopeConstructionTest` rightly forbids production code from minting its own scopes.
 */
private val trayExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "sni-tray").apply { isDaemon = true }
}

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

    // Rasterized on the composition thread, deliberately, and handed to the background install already
    // done. Skia must be initialised from the UI thread: doing it first from the tray thread left the
    // EDT spinning forever inside FontMgr.<clinit> -> _nDefault, so the window never appeared and no
    // queued UI work ever ran. Only D-Bus belongs off this thread; this is pure CPU over two small SVGs.
    val iconRasters =
        remember(isDarkTheme) { loadTrayIconRasters(if (isDarkTheme) TRAY_ICON_LIGHT else TRAY_ICON_DARK) }

    DisposableEffect(isDarkTheme, title, tooltip, iconRasters) {
        // Gates this effect's registration reports. uiScope outlives a keyed re-run, so without it an
        // install still in flight could land `registered = true` after onDispose had reported false,
        // leaving the close button trusting a tray already queued for teardown while its replacement was
        // not up yet. Checked twice: once where the report originates on a D-Bus thread, and again on the
        // UI thread, since the hop can be queued before disposal and run after it.
        val reporting = AtomicBoolean(true)
        // Written and read only on trayExecutor's single thread, which is what makes it safe without
        // synchronisation: the dispose below queues behind the install, whether or not it has finished.
        var tray: SniTray? = null
        trayExecutor.execute {
            tray =
                SniTray.install(
                    appId = SNI_TRAY_APP_ID,
                    title = title,
                    tooltip = tooltip,
                    iconRasters = iconRasters,
                    menuProvider = { currentMenu },
                    onActivate = { uiScope.launch { currentActivate() } },
                    onItemClicked = { item -> uiScope.launch { item.onClick() } },
                    onRegistrationChange = { registered ->
                        if (reporting.get()) {
                            uiScope.launch { if (reporting.get()) currentRegistrationChange(registered) }
                        }
                    },
                )
            if (tray == null) Logger.i { "Linux tray unavailable; the close button will quit instead" }
        }
        onDispose {
            // Silence this effect's reports before saying false, so a late `true` cannot overwrite it.
            // Activation and menu clicks deliberately stay ungated: those are user intent and should not
            // be dropped mid theme-change.
            reporting.set(false)
            currentRegistrationChange(false)
            trayExecutor.execute {
                tray?.close()
                tray = null
            }
        }
    }
}
