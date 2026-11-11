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

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import org.meshtastic.feature.map.terrain.spatial.LatLon
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.terrain.spatial.SpatialSignal

/**
 * Hosts the app on Android XR. In Full Space the activity's window is the main panel, with an orbiter below it to go
 * back; in Home Space it draws as usual with a button to enter Full Space. Off XR it is just [content].
 */
@Composable
fun SpatialShell(
    nodes: List<SpatialNodeSnapshot>,
    imagery: List<TerrainImagery>,
    buildings: TerrainBuildingSource?,
    route: TerrainRoute?,
    contourUnits: ContourUnits,
    buildDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val spatial = LocalSpatialCapabilities.current.isSpatialUiEnabled
    val context = LocalContext.current
    val session = LocalSession.current
    val openAtLaunch = remember(context) { opensMapAtLaunch(context) }
    var mapShown by remember { mutableStateOf(openAtLaunch) }
    if (openAtLaunch && session != null) {
        DisposableEffect(session) {
            session.scene.requestFullSpace()
            onDispose {}
        }
    }
    Box(modifier.fillMaxSize()) {
        content()
        if (spatial) {
            // The main panel keeps its Home Space size; sizing it resizes the window and recreates the activity.
            Orbiter(position = OrbiterPosition.BottomCenter()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpaceToggle(inFullSpace = true)
                    FilledTonalIconButton(onClick = { mapShown = !mapShown }, modifier = Modifier.size(XrTargetSize)) {
                        Icon(MeshtasticIcons.Map, contentDescription = stringResource(Res.string.xr_terrain_map))
                    }
                }
            }
            if (mapShown) {
                val made = if (openAtLaunch) debugNodes() else null
                SpatialTerrainMap(
                    made ?: nodes,
                    imagery,
                    buildings,
                    if (made === CITY) CITY_ROUTE else route,
                    contourUnits,
                    buildDispatcher,
                )
            }
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
        modifier = modifier.size(XrTargetSize),
    ) {
        Icon(
            imageVector = if (inFullSpace) MeshtasticIcons.CloseFullscreen else MeshtasticIcons.OpenInFull,
            contentDescription = label,
        )
    }
}

/**
 * For emulator checks, where nothing can press an orbiter: on a debuggable build, `adb shell setprop log.tag.MeshXrMap
 * DEBUG` opens the terrain map in Full Space at launch.
 */
internal fun opensMapAtLaunch(context: Context): Boolean =
    context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 && Log.isLoggable("MeshXrMap", Log.DEBUG)

/** Made-up nodes in place of the mesh, when one of the switches below is set on top of the map hook. */
private fun debugNodes(): List<SpatialNodeSnapshot>? = when {
    Log.isLoggable("MeshXrPeaks", Log.DEBUG) -> PEAKS
    Log.isLoggable("MeshXrCity", Log.DEBUG) -> CITY
    else -> null
}

private val GOOD = SpatialSignal.GOOD
private val BAD = SpatialSignal.BAD

/** `log.tag.MeshXrCity DEBUG`: downtown Seattle, a circle small enough for a downloaded basemap's buildings. */
private val CITY =
    listOf(
        city(1, 47.6062, -122.3321, "DOWN", 0xFF7886EE).copy(isConnectedRadio = true),
        city(2, 47.6205, -122.3493, "NDL", 0xFFF2C14E).copy(hopsAway = 0, signal = GOOD),
        city(3, 47.5952, -122.3316, "STA", 0xFF2A9D8F).copy(hopsAway = 0, signal = BAD),
        city(4, 47.6131, -122.3166, "CAPH", 0xFF8E3B8C).copy(hopsAway = 1, isInfrastructure = true),
    )

/** A made-up node in a color of its own, as a real node's number gives it one. */
private fun city(id: Long, latitude: Double, longitude: Double, name: String, color: Long) =
    SpatialNodeSnapshot(id, LatLon(latitude, longitude), shortName = name, color = color.toInt())

/** A trace route over [CITY]: out by way of NDL to CAPH, and straight back. */
private val CITY_ROUTE = TerrainRoute(forward = listOf(1, 2, 4), back = listOf(4, 1))

/** `log.tag.MeshXrPeaks DEBUG`: around Mount Rainier, to check relief, towers and links on ground that has some. */
private val PEAKS =
    listOf(
        SpatialNodeSnapshot(1, LatLon(46.786, -121.735), isConnectedRadio = true, shortName = "PARA"),
        SpatialNodeSnapshot(2, LatLon(46.835, -121.731), shortName = "MUIR", hopsAway = 0, signal = SpatialSignal.GOOD),
        SpatialNodeSnapshot(3, LatLon(46.914, -121.642), shortName = "SUNR", hopsAway = 0, signal = SpatialSignal.FAIR),
        SpatialNodeSnapshot(
            4,
            LatLon(46.935, -121.474),
            shortName = "CRYS",
            hopsAway = 0,
            signal = SpatialSignal.BAD,
            isInfrastructure = true,
        ),
        SpatialNodeSnapshot(5, LatLon(46.759, -122.031), shortName = "ASHF", hopsAway = 1, color = 0xFF3D7BE0.toInt()),
        SpatialNodeSnapshot(6, LatLon(46.606, -121.671), shortName = "PACK", hopsAway = 2, isOnline = false),
        SpatialNodeSnapshot(7, LatLon(47.204, -121.991), shortName = "ENUM", hopsAway = 1, isInfrastructure = true),
        SpatialNodeSnapshot(8, LatLon(46.867, -122.266), shortName = "EATN", hopsAway = 2, color = 0xFFB04CD9.toInt()),
    )

/** The pointer target Android XR recommends, above Material's 48 dp minimum. */
internal val XrTargetSize = 56.dp
