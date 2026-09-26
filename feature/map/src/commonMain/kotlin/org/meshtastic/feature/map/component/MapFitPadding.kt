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
package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp

/**
 * How far the mesh map keeps each framed node from the edges, so the node's chip clears the map's own chrome.
 *
 * Asymmetric because the chrome is: [MapControlsOverlay] reaches 72dp down from the top, the zoom pair reaches 72dp in
 * from the lower trailing corner, and the logo and attribution run along the foot. A node chip is about 64 by 28dp,
 * centred on its point, so each edge also leaves half a chip.
 */
val MeshMapFitPadding = PaddingValues(start = 40.dp, top = 88.dp, end = 104.dp, bottom = 56.dp)
