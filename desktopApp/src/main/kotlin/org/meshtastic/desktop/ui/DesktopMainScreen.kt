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
package org.meshtastic.desktop.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.appshell.MeshtasticMainScreen
import org.meshtastic.core.navigation.MultiBackstack
import org.meshtastic.core.ui.viewmodel.UIViewModel

/** The shared [MeshtasticMainScreen] on a surface, with room under it for the desktop window's bottom edge. */
@Suppress("ViewModelForwarding")
@Composable
fun DesktopMainScreen(uiViewModel: UIViewModel, multiBackstack: MultiBackstack, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize()) {
        MeshtasticMainScreen(
            uiViewModel = uiViewModel,
            multiBackstack = multiBackstack,
            hostModifier = Modifier.padding(bottom = 24.dp),
        )
    }
}
