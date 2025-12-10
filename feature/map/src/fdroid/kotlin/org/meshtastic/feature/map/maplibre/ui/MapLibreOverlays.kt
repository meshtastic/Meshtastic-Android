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

package org.meshtastic.feature.map.maplibre.ui

import android.graphics.PointF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.ui.component.NodeChip
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.feature.map.maplibre.utils.protoShortName
import org.meshtastic.feature.map.maplibre.utils.roleColor
import org.meshtastic.feature.map.maplibre.utils.shortNameFallback

/** Expanded cluster overlay showing nodes in a radial pattern */
@Composable
fun ExpandedClusterOverlay(
    centerPx: PointF,
    members: List<Node>,
    density: Float,
    onNodeClick: (Node) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(onClick = onDismiss)) {
        val centerX = (centerPx.x / density).dp
        val centerY = (centerPx.y / density).dp
        val radiusPx = 72f * density
        val itemSize = 40.dp
        val n = members.size.coerceAtLeast(1)
        members.forEachIndexed { idx, node ->
            val theta = (2.0 * Math.PI * idx / n)
            val x = (centerPx.x + (radiusPx * kotlin.math.cos(theta))).toFloat()
            val y = (centerPx.y + (radiusPx * kotlin.math.sin(theta))).toFloat()
            val xDp = (x / density).dp
            val yDp = (y / density).dp
            val label = (protoShortName(node) ?: shortNameFallback(node)).take(4)
            val itemHeight = 36.dp
            val itemWidth = (40 + label.length * 10).dp
            Surface(
                modifier =
                Modifier.align(Alignment.TopStart)
                    .offset(x = xDp - itemWidth / 2, y = yDp - itemHeight / 2)
                    .size(width = itemWidth, height = itemHeight)
                    .clickable { onNodeClick(node) },
                shape = CircleShape,
                color = roleColor(node),
                shadowElevation = 6.dp,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(text = label, color = Color.White, maxLines = 1) }
            }
        }
    }
}

/** Bottom sheet showing a list of cluster members */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClusterListBottomSheet(
    members: List<Node>,
    mapRef: MapLibreMap?,
    onNodeSelect: (Node) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = "Cluster items (${members.size})", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(members) { node ->
                    Row(
                        modifier =
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable {
                            onNodeSelect(node)
                            node.validPosition?.let { p ->
                                mapRef?.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(
                                        LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                        15.0,
                                    ),
                                )
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NodeChip(
                            node = node,
                            onClick = {
                                onNodeSelect(node)
                                node.validPosition?.let { p ->
                                    mapRef?.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                            15.0,
                                        ),
                                    )
                                }
                            },
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        val longName = node.user.longName
                        if (!longName.isNullOrBlank()) {
                            Text(text = longName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

/** Legend showing role colors */
@Composable
fun MapLegend(nodes: List<Node>, onDismiss: () -> Unit) {
    Surface(modifier = Modifier.padding(16.dp), shape = MaterialTheme.shapes.medium, shadowElevation = 4.dp) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Legend", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.size(8.dp))
            val roles = nodes.map { it.user.role }.distinct().sortedBy { it.name }
            roles.forEach { role ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    val sampleNode = nodes.first { it.user.role == role }
                    Surface(modifier = Modifier.size(16.dp), shape = CircleShape, color = roleColor(sampleNode)) {}
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = role.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

/** Dialog for custom tile URL input */
@Composable
fun CustomTileUrlDialog(
    customTileUrlInput: String,
    onCustomTileUrlInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Tile URL") },
        text = {
            Column {
                Text(
                    text = "Enter tile URL with {z}/{x}/{y} placeholders:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Example: https://tile.openstreetmap.org/{z}/{x}/{y}.png",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                OutlinedTextField(
                    value = customTileUrlInput,
                    onValueChange = onCustomTileUrlInputChange,
                    label = { Text("Tile URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
