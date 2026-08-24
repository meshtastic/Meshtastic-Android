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
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.node_layout_section_title
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.component.NodeLayoutSettings

/**
 * Dedicated settings screen for node list display options (density and field visibility). Provides a focused interface
 * for customizing how nodes are rendered in the list view.
 *
 * Material 3 expressive design highlights:
 * - **Section organization**: NodeLayoutSettings component handles grouped layout with cards
 * - **Visual hierarchy**: MainAppBar provides clear screen identity and navigation
 * - **Spacing rhythm**: 16dp content padding with 16dp section gaps for consistent rhythm
 * - **Scrollability**: Full vertical scroll support for all display variations
 * - **Responsive preview**: Live preview updates as density and field options change
 *
 * @param settingsViewModel Provides access to node display preferences and update methods
 * @param onNavigateUp Callback when user requests navigation back (back button in app bar)
 */
@Composable
fun NodeListScreen(settingsViewModel: SettingsViewModel, onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    val settings by settingsViewModel.nodeListSettings.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.node_layout_section_title),
                canNavigateUp = true,
                onNavigateUp = onNavigateUp,
                ourNode = null,
                showNodeChip = false,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            NodeLayoutSettings(
                state = settings,
                onDensityChange = { settingsViewModel.setNodeListDensity(it.name) },
                onShowPowerChange = { settingsViewModel.setShouldShowPower(it) },
                onShowLastHeardChange = { settingsViewModel.setShouldShowLastHeard(it) },
                onLastHeardIsRelativeChange = { settingsViewModel.setLastHeardIsRelative(it) },
                onShowLocationChange = { settingsViewModel.setShouldShowLocation(it) },
                onShowHopsChange = { settingsViewModel.setShouldShowHops(it) },
                onShowSignalChange = { settingsViewModel.setShouldShowSignal(it) },
                onShowChannelChange = { settingsViewModel.setShouldShowChannel(it) },
                onShowRoleChange = { settingsViewModel.setShouldShowRole(it) },
                onShowTelemetryChange = { settingsViewModel.setShouldShowTelemetry(it) },
            )
        }
    }
}
