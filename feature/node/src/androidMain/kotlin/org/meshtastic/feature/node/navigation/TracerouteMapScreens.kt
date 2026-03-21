/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.navigation

import androidx.compose.runtime.Composable
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.TracerouteMapScreen as AndroidTracerouteMapScreen

@Composable
actual fun TracerouteMapScreen(
    destNum: Int,
    requestId: Int,
    logUuid: String?,
    onNavigateUp: () -> Unit,
) {
    val metricsViewModel = koinViewModel<MetricsViewModel>(key = "metrics-$destNum") { parametersOf(destNum) }
    metricsViewModel.setNodeId(destNum)

    AndroidTracerouteMapScreen(
        metricsViewModel = metricsViewModel,
        requestId = requestId,
        logUuid = logUuid,
        onNavigateUp = onNavigateUp,
    )
}
