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
package org.meshtastic.feature.intro

import androidx.compose.runtime.Composable

/**
 * What the intro flow needs from the platform beyond core:ui's permission states.
 *
 * @property bluetoothRequiresLocation BLE scanning is gated by the location permission (Android before 12).
 * @property inBrowser the browser owns the permissions: radios are picked in its chooser at connect time, and a blocked
 *   permission comes back only through its site settings, which a page cannot open.
 * @property settingsNavigator opens the system settings pages, or null where there are none; the critical-alerts step
 *   is offered only with one.
 */
internal class IntroPlatform(
    val bluetoothRequiresLocation: Boolean = false,
    val inBrowser: Boolean = false,
    val settingsNavigator: IntroSettingsNavigator? = null,
)

@Composable internal expect fun rememberIntroPlatform(): IntroPlatform
