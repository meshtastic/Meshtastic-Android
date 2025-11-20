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
import org.meshtastic.feature.map.maplibre.utils.MapLibreTileCacheManager
import timber.log.Timber

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TileCacheManagementSheet(
    cacheManager: MapLibreTileCacheManager,
    onDismiss: () -> Unit,
) {
    var isClearing by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                modifier = Modifier.padding(16.dp),
                text = "Map Cache",
                style = MaterialTheme.typography.headlineSmall,
            )
            HorizontalDivider()
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About Map Caching",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "Map tiles are automatically cached by MapLibre as you view the map. " +
                        "This improves performance and allows limited offline viewing of previously visited areas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "The cache is managed automatically and will be cleared if your device runs low on storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()
        }

        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Cache Management",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = "If you're experiencing issues with outdated map tiles or want to free up storage space, you can clear the cache below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Button(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = {
                    isClearing = true
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            cacheManager.clearCache()
                            Timber.tag("TileCacheManagementSheet").d("Cache cleared successfully")
                        } catch (e: Exception) {
                            Timber.tag("TileCacheManagementSheet").e(e, "Error clearing cache: ${e.message}")
                        } finally {
                            withContext(Dispatchers.Main) {
                                isClearing = false
                            }
                        }
                    }
                },
                enabled = !isClearing,
            ) {
                Text(if (isClearing) "Clearing..." else "Clear Map Cache")
            }
        }

        item {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                text = "Note: Clearing the cache will require re-downloading tiles as you view the map again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

