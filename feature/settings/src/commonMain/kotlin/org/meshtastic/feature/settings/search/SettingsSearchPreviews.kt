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
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.app_settings
import org.meshtastic.core.resources.device
import org.meshtastic.core.resources.lora
import org.meshtastic.core.resources.node_layout_section_title
import org.meshtastic.core.resources.schema_device_rebroadcast_mode
import org.meshtastic.core.resources.schema_lora_hop_limit
import org.meshtastic.core.resources.schema_lora_hop_limit_description
import org.meshtastic.core.ui.theme.AppTheme

/**
 * What a query for "hop" matches, resolved from the same resources the index reads, so the preview shows the wording
 * the schema actually ships and follows it into every other language.
 */
@Composable
private fun hopResults(): List<ResolvedSettingsEntry> = listOf(
    ResolvedSettingsEntry(
        title = stringResource(Res.string.schema_lora_hop_limit),
        description = stringResource(Res.string.schema_lora_hop_limit_description),
        screenTitle = stringResource(Res.string.lora),
        route = SettingsRoute.LoRa,
    ),
    // A field the schema labels but does not explain, which is the commonest shape in the index.
    ResolvedSettingsEntry(
        title = stringResource(Res.string.schema_device_rebroadcast_mode),
        description = null,
        screenTitle = stringResource(Res.string.device),
        route = SettingsRoute.Device,
    ),
    // An app-level entry, with no schema behind it at all.
    ResolvedSettingsEntry(
        title = stringResource(Res.string.node_layout_section_title),
        description = null,
        screenTitle = stringResource(Res.string.app_settings),
        route = SettingsRoute.NodeList,
        isAppLocal = true,
    ),
)

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SettingsSearchResultsPreview() {
    AppTheme { Surface { SettingsSearchResults(results = hopResults(), query = "hop", onSelect = {}) } }
}

@Suppress("PreviewPublic")
@PreviewLightDark
@Composable
fun SettingsSearchNoResultsPreview() {
    AppTheme { Surface { SettingsSearchResults(results = emptyList(), query = "xyzzy", onSelect = {}) } }
}
