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
package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.bottom_nav_settings
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.feature.settings.radio.RadioConfigItemList
import org.meshtastic.feature.settings.radio.RadioConfigState

/**
 * The settings tab as the desktop app lays it out: `DesktopSettingsScreen`'s app bar and scrolling column, which needs
 * two ViewModels, over the settings feature's own [RadioConfigItemList] - the configuration, backup and advanced
 * sections a connected local radio gets. The app-level sections that follow them fall below an 800 px window.
 */
@Composable
internal fun SettingsScreen() {
    MarketingTheme {
        AppShell(TopLevelDestination.Settings) {
            Scaffold(
                topBar = {
                    MainAppBar(
                        title = stringResource(Res.string.bottom_nav_settings),
                        ourNode = null,
                        showNodeChip = false,
                        canNavigateUp = false,
                        onNavigateUp = {},
                        onClickChip = {},
                        actions = {},
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioConfigItemList(
                        state = RadioConfigState(isLocal = true, connected = true),
                        isManaged = false,
                        isOtaCapable = false,
                        onNavigate = {},
                    )
                }
            }
        }
    }
}
