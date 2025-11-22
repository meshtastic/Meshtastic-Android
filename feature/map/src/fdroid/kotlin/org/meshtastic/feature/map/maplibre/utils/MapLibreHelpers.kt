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

package org.meshtastic.feature.map.maplibre.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.proto.ConfigProtos

/** Check if the app has any location permission */
fun hasAnyLocationPermission(context: Context): Boolean {
    val fine =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    val coarse =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/** Get protocol-defined short name if present */
fun protoShortName(node: Node): String? {
    val s = node.user.shortName
    return if (s.isNullOrBlank()) null else s
}

/** Fallback short name generation */
fun shortNameFallback(node: Node): String {
    val long = node.user.longName
    if (!long.isNullOrBlank()) return safeSubstring(long, 4)
    val hex = node.num.toString(16).uppercase()
    return if (hex.length >= 4) hex.takeLast(4) else hex
}

/** Safely take up to maxLength characters, respecting emoji boundaries */
fun safeSubstring(text: String, maxLength: Int): String {
    if (text.length <= maxLength) return text

    // Use grapheme cluster breaking to respect emoji boundaries
    var count = 0
    var lastSafeIndex = 0

    val breakIterator = java.text.BreakIterator.getCharacterInstance()
    breakIterator.setText(text)

    var start = breakIterator.first()
    var end = breakIterator.next()

    while (end != java.text.BreakIterator.DONE && count < maxLength) {
        lastSafeIndex = end
        count++
        end = breakIterator.next()
    }

    return if (lastSafeIndex > 0) text.substring(0, lastSafeIndex) else text.take(maxLength)
}

/**
 * Remove emojis from text for MapLibre rendering Returns null if the text is emoji-only (so caller can use fallback
 * like hex ID)
 */
fun stripEmojisForMapLabel(text: String): String? {
    if (text.isEmpty()) return null

    // Filter to keep only characters that MapLibre can reliably render
    val filtered =
        text
            .filter { ch ->
                ch.code in 0x20..0x7E || // Basic ASCII printable characters
                    ch.code in 0xA0..0xFF // Latin-1 supplement (accented characters)
            }
            .trim()

    // If filtering removed everything, return null (caller should use fallback)
    return if (filtered.isEmpty()) null else filtered
}

/** Select one label per grid cell in the current viewport, prioritizing favorites and recent nodes */
fun selectLabelsForViewport(map: MapLibreMap, nodes: List<Node>, density: Float): Set<Int> {
    val bounds = map.projection.visibleRegion.latLngBounds
    val visible =
        nodes.filter { n ->
            val p = n.validPosition ?: return@filter false
            val lat = p.latitudeI * DEG_D
            val lon = p.longitudeI * DEG_D
            bounds.contains(LatLng(lat, lon))
        }
    if (visible.isEmpty()) return emptySet()

    // Priority: favorites first, then more recently heard
    val sorted = visible.sortedWith(compareByDescending<Node> { it.isFavorite }.thenByDescending { it.lastHeard })

    // Dynamic cell size by zoom so more labels appear as you zoom in
    val zoom = map.cameraPosition.zoom
    val baseCellDp =
        when {
            zoom < 10 -> 96f
            zoom < 11 -> 88f
            zoom < 12 -> 80f
            zoom < 13 -> 72f
            zoom < 14 -> 64f
            zoom < 15 -> 56f
            zoom < 16 -> 48f
            else -> 36f
        }
    val cellSizePx = (baseCellDp * density).toInt().coerceAtLeast(32)
    val occupied = HashSet<Long>()
    val chosen = LinkedHashSet<Int>()
    for (n in sorted) {
        val p = n.validPosition ?: continue
        val pt = map.projection.toScreenLocation(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
        val cx = (pt.x / cellSizePx).toInt()
        val cy = (pt.y / cellSizePx).toInt()
        val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
        if (occupied.add(key)) {
            chosen.add(n.num)
        }
    }
    return chosen
}

/** Human friendly "x min ago" from epoch seconds */
fun formatSecondsAgo(lastHeardEpochSeconds: Int): String {
    val now = System.currentTimeMillis() / 1000
    val delta = (now - lastHeardEpochSeconds).coerceAtLeast(0)
    val minutes = delta / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        delta < 60 -> "$delta s ago"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        else -> "$days d ago"
    }
}

/** Simple haversine distance between two nodes in kilometers */
fun distanceKmBetween(a: Node, b: Node): Double? {
    val pa = a.validPosition ?: return null
    val pb = b.validPosition ?: return null
    val lat1 = pa.latitudeI * DEG_D
    val lon1 = pa.longitudeI * DEG_D
    val lat2 = pb.latitudeI * DEG_D
    val lon2 = pb.longitudeI * DEG_D
    val radius = 6371.0 // km (Earth's radius)
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val s1 = kotlin.math.sin(dLat / 2)
    val s2 = kotlin.math.sin(dLon / 2)
    val aTerm = s1 * s1 + kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) * s2 * s2
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(aTerm), kotlin.math.sqrt(1 - aTerm))
    return radius * c
}

/** Get hex color for a node based on its role */
fun roleColorHex(node: Node): String = when (node.user.role) {
    ConfigProtos.Config.DeviceConfig.Role.ROUTER -> "#D32F2F" // red (infrastructure)
    ConfigProtos.Config.DeviceConfig.Role.ROUTER_CLIENT -> "#00897B" // teal
    ConfigProtos.Config.DeviceConfig.Role.REPEATER -> "#7B1FA2" // purple
    ConfigProtos.Config.DeviceConfig.Role.TRACKER -> "#8E24AA" // purple (lighter)
    ConfigProtos.Config.DeviceConfig.Role.SENSOR -> "#1E88E5" // blue
    ConfigProtos.Config.DeviceConfig.Role.TAK,
    ConfigProtos.Config.DeviceConfig.Role.TAK_TRACKER,
    -> "#F57C00" // orange (TAK)
    ConfigProtos.Config.DeviceConfig.Role.CLIENT -> "#2E7D32" // green
    ConfigProtos.Config.DeviceConfig.Role.CLIENT_BASE -> "#1976D2" // blue (client base)
    ConfigProtos.Config.DeviceConfig.Role.CLIENT_MUTE -> "#9E9D24" // olive
    ConfigProtos.Config.DeviceConfig.Role.CLIENT_HIDDEN -> "#546E7A" // blue-grey
    ConfigProtos.Config.DeviceConfig.Role.LOST_AND_FOUND -> "#AD1457" // magenta
    ConfigProtos.Config.DeviceConfig.Role.ROUTER_LATE -> "#E57373" // light red (late router)
    null,
    ConfigProtos.Config.DeviceConfig.Role.UNRECOGNIZED,
    -> "#2E7D32" // default green
}

/** Get Color object for a node based on its role */
fun roleColor(node: Node): Color = Color(android.graphics.Color.parseColor(roleColorHex(node)))

/** Apply filters to node list */
fun applyFilters(
    all: List<Node>,
    filter: BaseMapViewModel.MapFilterState,
    enabledRoles: Set<ConfigProtos.Config.DeviceConfig.Role>,
    ourNodeNum: Int? = null,
    isLocationTrackingEnabled: Boolean = false,
): List<Node> {
    var out = all
    if (filter.onlyFavorites) out = out.filter { it.isFavorite }
    if (enabledRoles.isNotEmpty()) out = out.filter { enabledRoles.contains(it.user.role) }
    return out
}
