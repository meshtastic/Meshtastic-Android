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
package org.meshtastic.feature.discovery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.meshtastic.core.ui.icon.ArrowBack
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.DiscoveryMapNode
import org.meshtastic.core.ui.util.DiscoveryNeighborType
import org.meshtastic.core.ui.util.LocalDiscoveryMapProvider
import org.meshtastic.feature.discovery.DiscoveryMapViewModel

/**
 * Full-screen map showing all discovered nodes from a scan session. Delegates to the flavor-specific map implementation
 * via [LocalDiscoveryMapProvider].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryMapScreen(viewModel: DiscoveryMapViewModel, onNavigateUp: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val allNodes by viewModel.allNodes.collectAsStateWithLifecycle()
    val discoveryMap = LocalDiscoveryMapProvider.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discovery Map") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) { Icon(MeshtasticIcons.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val currentSession = session
        if (currentSession == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val mapNodes =
            allNodes.mapNotNull { entity ->
                val lat = entity.latitude ?: return@mapNotNull null
                val lon = entity.longitude ?: return@mapNotNull null
                if (lat == 0.0 && lon == 0.0) return@mapNotNull null
                DiscoveryMapNode(
                    latitude = lat,
                    longitude = lon,
                    shortName = entity.shortName,
                    longName = entity.longName,
                    neighborType =
                    if (entity.neighborType == "direct") {
                        DiscoveryNeighborType.DIRECT
                    } else {
                        DiscoveryNeighborType.MESH
                    },
                    snr = entity.snr,
                    rssi = entity.rssi,
                    messageCount = entity.messageCount,
                    sensorPacketCount = entity.sensorPacketCount,
                )
            }

        discoveryMap(
            currentSession.userLatitude,
            currentSession.userLongitude,
            mapNodes,
            Modifier.fillMaxSize().padding(padding),
        )
    }
}
