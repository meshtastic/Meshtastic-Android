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

package org.meshtastic.feature.map.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.meshtastic.feature.map.maplibre.utils.MapLibreTileCacheManager
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("LongMethod")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TileCacheManagementSheet(
    cacheManager: MapLibreTileCacheManager,
    currentBounds: LatLngBounds?,
    currentZoom: Double?,
    styleUrl: String?,
    onDismiss: () -> Unit,
) {
    var hotAreas by remember { mutableStateOf<List<MapLibreTileCacheManager.HotArea>>(emptyList()) }
    var cacheSizeBytes by remember { mutableStateOf<Long?>(null) }
    var lastUpdateTime by remember { mutableStateOf<Long?>(null) }
    var isCaching by remember { mutableStateOf(false) }

    fun refreshData() {
        hotAreas = cacheManager.getHotAreas()
        CoroutineScope(Dispatchers.IO).launch {
            cacheSizeBytes = cacheManager.getCacheSizeBytes()
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Tile Cache Management",
                style = MaterialTheme.typography.headlineSmall,
            )
            HorizontalDivider()
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cache Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Hot Areas: ${hotAreas.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                cacheSizeBytes?.let { size ->
                    val sizeMb = size / (1024.0 * 1024.0)
                    Text(
                        text = "Estimated Cache Size: ${String.format("%.2f", sizeMb)} MB",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                lastUpdateTime?.let { time ->
                    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    Text(
                        text = "Last Update: ${dateFormat.format(Date(time))}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            HorizontalDivider()
        }

        // Cache current area button
        item {
            val canCache = currentBounds != null && currentZoom != null && styleUrl != null
            Button(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                onClick = {
                    if (canCache) {
                        isCaching = true
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                cacheManager.cacheCurrentArea(currentBounds!!, currentZoom!!, styleUrl!!)
                                refreshData()
                            } catch (e: Exception) {
                                Timber.tag("TileCacheManagementSheet").e(e, "Error caching area: ${e.message}")
                            } finally {
                                isCaching = false
                            }
                        }
                    }
                },
                enabled = canCache && !isCaching,
            ) {
                Text(
                    when {
                        isCaching -> "Caching..."
                        styleUrl == null -> "Caching unavailable (custom tiles)"
                        else -> "Cache This Area Now"
                    },
                )
            }
        }
        if (styleUrl == null) {
            item {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    text = "Tile caching is only available when using standard map styles, not custom raster tiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
                text = "Cached Areas",
                style = MaterialTheme.typography.titleSmall,
            )
        }

        if (hotAreas.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = "No cached areas yet. Areas you view frequently will be automatically cached.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(hotAreas, key = { it.id }) { area ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Area ${area.id.take(8)}",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Column {
                            Text(
                                text = "Visits: ${area.visitCount} | Time: ${area.totalTimeSec}s",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "Zoom: ${String.format("%.1f", area.zoom)} | " +
                                    "Center: ${String.format("%.4f", area.centerLat)}, ${String.format("%.4f", area.centerLon)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (area.offlineRegionId != null) {
                                Text(
                                    text = "✓ Cached (Region ID: ${area.offlineRegionId})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text(
                                    text = "⏳ Caching in progress...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    },
                    trailingContent = {
                        if (area.offlineRegionId != null) {
                            IconButton(
                                onClick = {
                                    // TODO: Implement delete for individual region
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete cached area",
                                )
                            }
                        }
                    },
                )
                HorizontalDivider()
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = {
                    // Clear all cache
                    CoroutineScope(Dispatchers.IO).launch {
                        cacheManager.clearCache()
                        hotAreas = cacheManager.getHotAreas()
                        cacheSizeBytes = cacheManager.getCacheSizeBytes()
                    }
                },
            ) {
                Text("Clear All Cache")
            }
        }
    }
}

