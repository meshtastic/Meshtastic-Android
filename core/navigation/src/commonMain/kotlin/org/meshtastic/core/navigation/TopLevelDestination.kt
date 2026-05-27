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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bottom_nav_settings
import org.meshtastic.core.resources.connect
import org.meshtastic.core.resources.nodes

/**
 * Shared top-level destinations for the application shell.
 *
 * KV Phase 3: Reduced to three destinations for demo build. Messages and Map are intentionally excluded from the bottom
 * nav. Their nav graphs remain registered in Main.kt for deep link safety.
 */
enum class TopLevelDestination(val label: StringResource, val route: Route) {
    Nodes(Res.string.nodes, NodesRoute.Nodes),
    Connect(Res.string.connect, ConnectionsRoute.Connections),
    Settings(Res.string.bottom_nav_settings, SettingsRoute.Settings()),
    ;

    companion object {
        fun fromNavKey(key: NavKey?): TopLevelDestination? =
            entries.find { dest -> key?.let { it::class == dest.route::class } == true }
    }
}
