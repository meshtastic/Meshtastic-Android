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
package org.meshtastic.feature.settings.search

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.ui.theme.AppTheme

/** Matches for "hop", as the schema labels them, so the row layout is reviewable without a radio. */
private val hopResults =
    listOf(
        ResolvedSettingsEntry(
            title = "Hop limit",
            description = "Maximum number of hops a packet may take before it is dropped.",
            screenTitle = "LoRa",
            route = SettingsRoute.LoRa,
        ),
        ResolvedSettingsEntry(
            title = "Rebroadcast mode",
            description = "How this node repeats packets it hears for other nodes.",
            screenTitle = "Device",
            route = SettingsRoute.Device,
        ),
        ResolvedSettingsEntry(
            title = "Hops away",
            description = null,
            screenTitle = "Node list",
            route = SettingsRoute.NodeList,
        ),
    )

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SettingsSearchResultsPreview() {
    AppTheme { Surface { SettingsSearchResults(results = hopResults, query = "hop", onSelect = {}) } }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SettingsSearchNoResultsPreview() {
    AppTheme { Surface { SettingsSearchResults(results = emptyList(), query = "xyzzy", onSelect = {}) } }
}
