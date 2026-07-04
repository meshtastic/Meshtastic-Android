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
package org.meshtastic.app.node.metrics

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.util.TracerouteMapOverlayInsets

fun getTracerouteMapOverlayInsets(): TracerouteMapOverlayInsets = TracerouteMapOverlayInsets(
    overlayAlignment = Alignment.BottomCenter,
    overlayPadding = PaddingValues(bottom = 16.dp),
    contentHorizontalAlignment = Alignment.CenterHorizontally,
)
