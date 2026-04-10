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
package org.meshtastic.app.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.Single
import org.meshtastic.core.ui.util.MapViewProvider

@Single
class GoogleMapViewProvider : MapViewProvider {
    @Composable
    override fun MapView(modifier: Modifier, viewModel: Any, navigateToNodeDetails: (Int) -> Unit, waypointId: Int?) {
        val mapViewModel: MapViewModel = koinViewModel()
        LaunchedEffect(waypointId) { mapViewModel.setWaypointId(waypointId) }
        org.meshtastic.app.map.MapView(
            modifier = modifier,
            mapViewModel = mapViewModel,
            navigateToNodeDetails = navigateToNodeDetails,
        )
    }
}
