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
package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.module_settings
import org.meshtastic.core.resources.remotely_administrating
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.component.ExpressiveSection
import org.meshtastic.feature.settings.navigation.ModuleRoute
import org.meshtastic.feature.settings.radio.RadioConfigViewModel

@Composable
fun ModuleConfigurationScreen(
    viewModel: RadioConfigViewModel,
    excludedModulesUnlocked: Boolean,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit,
) {
    val state by viewModel.radioConfigState.collectAsStateWithLifecycle()
    val destNode by viewModel.destNode.collectAsStateWithLifecycle()

    val modules =
        remember(state.metadata, excludedModulesUnlocked) {
            if (excludedModulesUnlocked) {
                ModuleRoute.entries
            } else {
                ModuleRoute.filterExcludedFrom(state.metadata, state.userConfig.role)
            }
        }

    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.module_settings),
                subtitle =
                if (state.isLocal) {
                    destNode?.user?.long_name
                } else {
                    val remoteName = destNode?.user?.long_name ?: ""
                    stringResource(Res.string.remotely_administrating, remoteName)
                },
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onBack,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ExpressiveSection(title = stringResource(Res.string.module_settings)) {
                modules.forEach {
                    ListItem(
                        text = stringResource(it.title),
                        leadingIcon = it.icon?.let { res -> vectorResource(res) },
                        enabled = state.connected && !state.responseState.isWaiting(),
                    ) {
                        onNavigate(it.route)
                    }
                }
            }
        }
    }
}
