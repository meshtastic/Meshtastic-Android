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
package org.meshtastic.app.node.component

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.meshtastic.core.model.Node
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun InlineMap(node: Node, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val map = remember {
        MapView(context).apply {
            layoutParams =
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

            // Default osmdroid tile source.
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)

            controller.setZoom(15.0)
        }
    }

    LaunchedEffect(node.num) {
        val point = GeoPoint(node.latitude, node.longitude)

        map.overlays.clear()

        val marker =
            Marker(map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
        map.overlays.add(marker)

        map.controller.animateTo(point)
    }

    AndroidView(factory = { map }, modifier = modifier)
}
