/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.ui.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.geeksville.mesh.model.MetricsViewModel
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.MapView

const val DegD = 1e-7

@Composable
fun NodeMapScreen(
    uiViewModel: UIViewModel,
    metricsViewModel: MetricsViewModel = hiltViewModel(),
) {
    val state by metricsViewModel.state.collectAsState()
    val positions = state.positionLogs
    val destNum = state.node?.num
    MapView(
        uiViewModel = uiViewModel,
        focusedNodeNum = destNum,
        nodeTrack = positions,
        navigateToNodeDetails = {}
    )
}
