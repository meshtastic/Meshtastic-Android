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
@file:Suppress("MagicNumber", "TooManyFunctions")

package org.meshtastic.feature.xr

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.compose.spatial.Orbiter
import androidx.xr.compose.spatial.OrbiterPosition
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.CustomMesh
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.InputEvent
import androidx.xr.scenecore.InteractableComponent
import androidx.xr.scenecore.KhronosUnlitMaterial
import androidx.xr.scenecore.MeshEntity
import androidx.xr.scenecore.MeshSubsetTopology
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.VertexAttribute
import androidx.xr.scenecore.VertexAttributeType
import androidx.xr.scenecore.VertexLayout
import androidx.xr.scenecore.scene
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.xr_terrain_empty
import org.meshtastic.core.resources.xr_terrain_flat
import org.meshtastic.core.resources.xr_terrain_loading
import org.meshtastic.core.resources.xr_terrain_unavailable
import org.meshtastic.core.resources.xr_terrain_zoom
import org.meshtastic.feature.map.terrain.MapterhornEndpoints
import org.meshtastic.feature.map.terrain.spatial.SpatialCircle
import org.meshtastic.feature.map.terrain.spatial.SpatialFrame
import org.meshtastic.feature.map.terrain.spatial.SpatialHeightGrid
import org.meshtastic.feature.map.terrain.spatial.SpatialMapArea
import org.meshtastic.feature.map.terrain.spatial.SpatialModelTransform
import org.meshtastic.feature.map.terrain.spatial.SpatialNodeSnapshot
import org.meshtastic.feature.map.terrain.spatial.SpatialTerrainGeometry
import org.meshtastic.feature.map.terrain.spatial.SpatialVolumeFit
import org.meshtastic.feature.map.terrain.spatial.SpatialWheelDrag
import org.meshtastic.feature.map.terrain.spatial.SpatialWheelLayout
import org.meshtastic.feature.map.terrain.spatial.SpatialWheelMotion
import org.meshtastic.feature.map.terrain.spatial.SpatialZoom
import java.util.concurrent.Executor
import kotlin.math.PI
import kotlin.math.pow

private enum class TerrainStatus {
    Loading,
    Ready,
    Flat,
    Failed,
}

/** What the map stands on once built, and what to say about it. */
private class BuiltTerrain(
    val geometry: SpatialTerrainGeometry,
    val terrain: TerrainMeshData,
    val bezel: TerrainMeshData,
    val flat: Boolean,
)

/**
 * The mesh's ground as a round table beside the app panel: a [CustomMesh] heightfield on a turning wheel with compass
 * letters, and a zoom slider in an orbiter. Draws nothing without 3D content, so it only shows in Full Space.
 */
@Composable
fun SpatialTerrainMap(
    nodes: List<SpatialNodeSnapshot>,
    buildDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
) {
    val session = LocalSession.current
    if (session == null || !LocalSpatialCapabilities.current.isContent3dEnabled) return
    // Taken once the first position arrives: node updates after that must not move the circle, reset the zoom or
    // restart the build.
    val full = remember(nodes.isNotEmpty()) { SpatialMapArea.meshCircle(withLocalAnchor(nodes)) }
    if (full == null) {
        Notice(stringResource(Res.string.xr_terrain_empty), modifier)
        return
    }
    var slider by remember(full) { mutableFloatStateOf(0f) }
    var committed by remember(full) { mutableFloatStateOf(0f) }
    var status by remember(full) { mutableStateOf(TerrainStatus.Loading) }
    val maxZoom = SpatialZoom.maximumZoom(full.radiusMeters)
    val circle = SpatialZoom.circle(full, maxZoom.pow(committed.toDouble()), null)
    val motion = remember { SpatialWheelMotion() }
    var wheel by remember { mutableStateOf<Entity?>(null) }
    val http = remember { HttpClient() }
    DisposableEffect(http) { onDispose { http.close() } }

    TerrainEntities(
        session,
        circle,
        http,
        motion,
        buildDispatcher,
        onWheel = { wheel = it },
        onStatus = { status = it },
    )
    WheelFrames(wheel, motion)

    if (maxZoom > 1) {
        Orbiter(position = OrbiterPosition.TopCenter()) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(min = 240.dp)) {
                    Text(stringResource(Res.string.xr_terrain_zoom), style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = slider,
                        onValueChange = { slider = it },
                        onValueChangeFinished = { committed = slider },
                    )
                }
            }
        }
    }
    when (status) {
        TerrainStatus.Loading -> Notice(stringResource(Res.string.xr_terrain_loading), modifier)
        TerrainStatus.Flat -> Notice(stringResource(Res.string.xr_terrain_flat), modifier, Alignment.BottomCenter)
        TerrainStatus.Failed -> Notice(stringResource(Res.string.xr_terrain_unavailable), modifier)
        TerrainStatus.Ready -> Unit
    }
}

/**
 * [nodes] with an anchor for [SpatialMapArea.fit], which leaves out nodes too far from it: the connected radio, else
 * the node nearest the median position, which a few far nodes cannot drag away the way a centroid is.
 */
internal fun withLocalAnchor(nodes: List<SpatialNodeSnapshot>): List<SpatialNodeSnapshot> {
    if (nodes.isEmpty() || nodes.any { it.isConnectedRadio }) return nodes
    val latitude = nodes.map { it.coordinate.latitude }.sorted()[nodes.size / 2]
    val longitude = nodes.map { it.coordinate.longitude }.sorted()[nodes.size / 2]
    val anchor = nodes.minBy {
        val dLat = it.coordinate.latitude - latitude
        val dLon = it.coordinate.longitude - longitude
        dLat * dLat + dLon * dLon
    }
    return nodes.map { if (it.id == anchor.id) it.copy(isConnectedRadio = true) else it }
}

@Composable
private fun Notice(text: String, modifier: Modifier = Modifier, alignment: Alignment = Alignment.Center) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = alignment) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
            Text(
                text,
                modifier = Modifier.padding(16.dp).widthIn(max = 360.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Steps the wheel's ease and coast once per frame and turns the entity to match. */
@Composable
private fun WheelFrames(wheel: Entity?, motion: SpatialWheelMotion) {
    LaunchedEffect(wheel) {
        val entity = wheel ?: return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / NANOS_PER_SECOND
                last = now
                if (motion.step(dt)) entity.setPose(Pose(Vector3.Zero, yawRotation(motion.shown)))
            }
        }
    }
}

@Composable
private fun TerrainEntities(
    session: Session,
    circle: SpatialCircle,
    http: HttpClient,
    motion: SpatialWheelMotion,
    buildDispatcher: CoroutineDispatcher,
    onWheel: (Entity?) -> Unit,
    onStatus: (TerrainStatus) -> Unit,
) {
    val wheelChanged by rememberUpdatedState(onWheel)
    val statusChanged by rememberUpdatedState(onStatus)
    // The wheel's input callbacks and its per-frame steps share one state, so both run on the main thread.
    val mainExecutor = remember {
        val handler = Handler(Looper.getMainLooper())
        Executor { handler.post(it) }
    }
    LaunchedEffect(session, circle) {
        statusChanged(TerrainStatus.Loading)
        val built =
            try {
                withContext(buildDispatcher) { buildTerrain(circle, http) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The message names tile coordinates, which place the mesh to a few kilometers.
                Logger.w { "Terrain map failed to build: ${e::class.simpleName}" }
                statusChanged(TerrainStatus.Failed)
                return@LaunchedEffect
            }
        val root = createRoot(session, built.geometry)
        val wheel = Entity.create(session, "wheel", Pose(Vector3.Zero, yawRotation(motion.shown)), root)
        val material = KhronosUnlitMaterial.create(session, AlphaMode.OPAQUE)
        material.setBaseColorFactor(Vector4(1f, 1f, 1f, 1f))
        val meshes = listOf(built.terrain, built.bezel).map { createMesh(session, it) }
        val entities = meshes.map { MeshEntity.create(session, it, listOf(material), 0, Pose.Identity, wheel) }
        val plate = createPlate(session, root, built.geometry.transform.diskRadius, material, motion, mainExecutor)
        wheelChanged(wheel)
        statusChanged(if (built.flat) TerrainStatus.Flat else TerrainStatus.Ready)
        try {
            awaitCancellation()
        } finally {
            wheelChanged(null)
            destroy(entities + plate.entity + wheel + root, meshes + plate.mesh, material)
        }
    }
}

/**
 * Entities go before the meshes and material they borrow: the native side aborts if either is released while an entity
 * still holds it, and detaching an entity does not release it.
 */
@Suppress("DEPRECATION")
private fun destroy(entities: List<Entity>, meshes: List<CustomMesh>, material: KhronosUnlitMaterial) {
    entities.forEach { it.dispose() }
    meshes.forEach { it.close() }
    material.close()
}

/** A root beside the main panel's right edge, scaled so the table is its real size whatever the panel's scale. */
private fun createRoot(session: Session, geometry: SpatialTerrainGeometry): Entity {
    val panel = session.scene.mainPanelEntity
    val size = panel.size
    val scale = panel.getScale(Space.ACTIVITY)
    val fit = SpatialVolumeFit(TABLE_VOLUME_METERS, TABLE_VOLUME_METERS, geometry.transform.diskRadius)
    val reach = fit.diskRadius + SpatialWheelLayout.REACH + TABLE_GAP_METERS
    val at = Vector3((size.width / 2f + reach / scale), -size.height * DROP_FRACTION, 0f)
    return Entity.create(session, "terrain-map", Pose(at), panel).also { it.setScale(fit.scale / scale) }
}

/**
 * A flat disk under the terrain that does not turn, so a ray's hit on it is in the table's own frame: the hand's point
 * around the middle is what [SpatialWheelDrag] follows.
 */
private fun createPlate(
    session: Session,
    root: Entity,
    diskRadius: Float,
    material: KhronosUnlitMaterial,
    motion: SpatialWheelMotion,
    mainExecutor: Executor,
): Plate {
    val acc = MeshAccumulator()
    val radius = diskRadius + SpatialWheelLayout.GAP + SpatialWheelLayout.BEZEL_WIDTH
    acc.box(0f, -PLATE_HALF, 0f, radius * PLATE_FILL, PLATE_HALF, radius * PLATE_FILL, Rgb(0.1f, 0.1f, 0.12f))
    val mesh = createMesh(session, acc.build())
    val entity = MeshEntity.create(session, mesh, listOf(material), 0, Pose.Identity, root)
    var drag: SpatialWheelDrag? = null
    entity.addComponent(
        InteractableComponent.create(session, mainExecutor) { event ->
            val hit = event.hitInfoList.firstOrNull()?.hitPosition ?: return@create
            val seconds = event.timestamp / MILLIS_PER_SECOND
            when (event.action) {
                InputEvent.Action.DOWN -> drag = SpatialWheelDrag(motion.grab(), hit.x, hit.z)

                InputEvent.Action.MOVE -> drag?.let { motion.drag(it.update(hit.x, hit.z), seconds) }

                InputEvent.Action.UP,
                InputEvent.Action.CANCEL,
                -> {
                    drag = null
                    motion.release(seconds)
                }

                else -> Unit
            }
        },
    )
    return Plate(entity, mesh)
}

/** The plate's entity and the mesh it borrows, which is released after it. */
private class Plate(val entity: Entity, val mesh: CustomMesh)

private fun createMesh(session: Session, data: TerrainMeshData): CustomMesh {
    val layout =
        VertexLayout.Builder()
            .addAttribute(VertexAttribute.POSITION, VertexAttributeType.FLOAT3)
            .addAttribute(VertexAttribute.NORMAL, VertexAttributeType.FLOAT3)
            .addAttribute(VertexAttribute.COLOR, VertexAttributeType.UBYTE4_NORM)
            .build()
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var minZ = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    var maxZ = -Float.MAX_VALUE
    for (i in 0 until data.vertexCount) {
        minX = minOf(minX, data.positions[i * 3])
        maxX = maxOf(maxX, data.positions[i * 3])
        minY = minOf(minY, data.positions[i * 3 + 1])
        maxY = maxOf(maxY, data.positions[i * 3 + 1])
        minZ = minOf(minZ, data.positions[i * 3 + 2])
        maxZ = maxOf(maxZ, data.positions[i * 3 + 2])
    }
    return CustomMesh.BuilderFromMeshData(session, layout)
        .addVertexData(data.vertexBuffer())
        .setIndexData(data.indexBuffer())
        .addSubset(MeshSubsetTopology.TRIANGLES, 0, data.indices.size)
        .setBounds(BoundingBox.fromMinMax(Vector3(minX, minY, minZ), Vector3(maxX, maxY, maxZ)))
        .build()
}

private suspend fun buildTerrain(circle: SpatialCircle, http: HttpClient): BuiltTerrain {
    val frame = SpatialFrame(circle.bounds)
    val source = MapterhornElevationSource(http)
    val plan =
        SpatialHeightGrid.plan(
            frame,
            maxTriangles = MAX_TRIANGLES,
            dataMaxZoom = minOf(source.finestDataZoom(), MapterhornEndpoints.TILES_MAX_ZOOM),
        )
    val grid = SpatialHeightGrid.sample(frame, plan, source).cleaned()
    val transform = SpatialModelTransform(frame, grid.baseElevation.toDouble(), grid.maxElevation.toDouble())
    val geometry = SpatialTerrainGeometry(grid, transform)
    return BuiltTerrain(
        geometry = geometry,
        terrain = terrainMeshData(geometry, grid.baseElevation.toDouble(), grid.maxElevation.toDouble()),
        bezel = bezelMeshData(transform.diskRadius, geometry.rimTop),
        flat = !grid.hasAnyTerrain,
    )
}

/** A rotation about +y by [radians], the sense the wheel's yaw is in. */
private fun yawRotation(radians: Float): Quaternion =
    Quaternion.fromAxisAngle(Vector3.Up, radians * 180f / PI.toFloat())

private const val MAX_TRIANGLES = 90_000
private const val TABLE_VOLUME_METERS = 0.8f
private const val TABLE_GAP_METERS = 0.12f
private const val DROP_FRACTION = 0.25f
private const val PLATE_HALF = 0.004f
private const val PLATE_FILL = 1.02f
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MILLIS_PER_SECOND = 1_000.0
