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
import kotlinx.coroutines.launch

/** Light-on-dark tray glyph, used when the system theme is dark. */
internal const val TRAY_ICON_LIGHT = "tray_icon_white.svg"

/** Dark-on-light tray glyph, used when the system theme is light. */
internal const val TRAY_ICON_DARK = "tray_icon_black.svg"

/** StatusNotifierItem `Id` — the stable application identity hosts key their own settings off. */
private const val SNI_TRAY_APP_ID = "meshtastic-desktop"

/**
 * Installs the Linux StatusNotifierItem tray for as long as this composition lives.
 *
 * [onRegistrationChange] reports whether a host actually accepted the item, which is what decides whether the close
 * button may hide the window rather than quit — see [canHideToTray].
 *
 * Keying on [isDarkTheme] re-registers the item with a freshly rasterized icon when the system theme flips. That is
 * coarser than emitting SNI's `NewIcon` signal, but it keeps the icon correct without hand-rolling signal plumbing, and
 * the item is cheap to republish.
 *
 * Menu clicks and activations arrive on dbus-java reader threads, so both hop onto the composition's dispatcher before
 * touching any Compose state.
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
    val scope = rememberCoroutineScope()

    DisposableEffect(isDarkTheme, title, tooltip) {
        val tray =
            SniTray.install(
                appId = SNI_TRAY_APP_ID,
                title = title,
                tooltip = tooltip,
                iconRasters = loadTrayIconRasters(if (isDarkTheme) TRAY_ICON_LIGHT else TRAY_ICON_DARK),
                menuProvider = { currentMenu },
                onActivate = { scope.launch { currentActivate() } },
                onItemClicked = { item -> scope.launch { item.onClick() } },
            )
        currentRegistrationChange(tray != null)
        onDispose {
            tray?.close()
            currentRegistrationChange(false)
        }
    }
}
