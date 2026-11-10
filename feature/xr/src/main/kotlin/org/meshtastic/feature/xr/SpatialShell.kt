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
package org.meshtastic.feature.xr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterPosition
import androidx.xr.scenecore.scene
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.xr_full_space
import org.meshtastic.core.resources.xr_home_space
import org.meshtastic.core.resources.xr_terrain_map
import org.meshtastic.core.ui.icon.CloseFullscreen
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.OpenInFull
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot

/**
 * Hosts the app on Android XR. In Full Space the activity's window is the main panel, with an orbiter below it to go
 * back; in Home Space it draws as usual with a button to enter Full Space. Off XR it is just [content].
 */
@Composable
fun SpatialShell(
    nodes: List<SpatialNodeSnapshot>,
    buildDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spatial = LocalSpatialCapabilities.current.isSpatialUiEnabled
    var mapShown by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        content()
        if (spatial) {
            // The main panel keeps its Home Space size; sizing it resizes the window and recreates the activity.
            Orbiter(position = OrbiterPosition.BottomCenter()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceToggle(inFullSpace = true)
                    FilledTonalIconButton(onClick = { mapShown = !mapShown }) {
                        Icon(MeshtasticIcons.Map, contentDescription = stringResource(Res.string.xr_terrain_map))
                    }
                }
            }
            if (mapShown) SpatialTerrainMap(nodes, buildDispatcher)
        } else {
            SpaceToggle(
                inFullSpace = false,
                modifier = Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(16.dp),
            )
        }
    }
}

/** Draws nothing without an XR session, so phones and tablets never see it. */
@Composable
private fun SpaceToggle(inFullSpace: Boolean, modifier: Modifier = Modifier) {
    val session = LocalSession.current ?: return
    val label = stringResource(if (inFullSpace) Res.string.xr_home_space else Res.string.xr_full_space)
    FilledTonalIconButton(
        onClick = { if (inFullSpace) session.scene.requestHomeSpace() else session.scene.requestFullSpace() },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (inFullSpace) MeshtasticIcons.CloseFullscreen else MeshtasticIcons.OpenInFull,
            contentDescription = label,
        )
    }
}
