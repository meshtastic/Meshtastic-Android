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
package org.meshtastic.app.xr

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.xr.SpatialShell

@Composable
fun AppSpatialShell(content: @Composable () -> Unit) {
    val nodes by koinInject<NodeRepository>().nodeDBbyNum.collectAsState()
    val positions =
        remember(nodes) {
            nodes.values.filter { it.validPosition != null }.map { LatLon(it.latitude, it.longitude) }
        }
    SpatialShell(
        nodePositions = positions,
        buildDispatcher = koinInject<CoroutineDispatchers>().default,
        content = content,
    )
}
