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

import android.animation.ValueAnimator
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.xr.compose.platform.LocalSession
import androidx.xr.compose.platform.LocalSpatialCapabilities
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.BoundingBox
import androidx.xr.runtime.math.IntSize2d
import androidx.xr.runtime.math.Pose
import androidx.xr.runtime.math.Quaternion
import androidx.xr.runtime.math.Vector3
import androidx.xr.runtime.math.Vector4
import androidx.xr.scenecore.AlphaMode
import androidx.xr.scenecore.CustomMesh
import androidx.xr.scenecore.Entity
import androidx.xr.scenecore.GltfModel
import androidx.xr.scenecore.GltfModelEntity
import androidx.xr.scenecore.InputEvent
import androidx.xr.scenecore.InteractableComponent
import androidx.xr.scenecore.KhronosUnlitMaterial
import androidx.xr.scenecore.MeshEntity
import androidx.xr.scenecore.MeshSubsetTopology
import androidx.xr.scenecore.PanelEntity
import androidx.xr.scenecore.Space
import androidx.xr.scenecore.VertexAttribute
import androidx.xr.scenecore.VertexAttributeType
import androidx.xr.scenecore.VertexLayout
import androidx.xr.scenecore.scene
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.map_offline_terrain_contours
import org.meshtastic.core.resources.xr_terrain_buildings
import org.meshtastic.core.resources.xr_terrain_empty
import org.meshtastic.core.resources.xr_terrain_flat
import org.meshtastic.core.resources.xr_terrain_layer_elevation
import org.meshtastic.core.resources.xr_terrain_loading
import org.meshtastic.core.resources.xr_terrain_map
import org.meshtastic.core.resources.xr_terrain_unavailable
import org.meshtastic.core.resources.xr_terrain_zoom
import org.meshtastic.feature.map.terrain.GeoBounds
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
import org.meshtastic.feature.map.terrain.spatial.Vec3
import java.util.concurrent.Executor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private enum class TerrainStatus {
    Loading,
    Ready,
    Flat,
    Failed,
}

/** What the map stands on once built, and what to say about it. */
@Suppress("LongParameterList")
private class BuiltTerrain(
    val geometry: SpatialTerrainGeometry,
    val terrain: TerrainMeshData,
    /** The table's side, apart from [terrain] when an image is draped on it. */
    val skirt: TerrainMeshData?,
    val bezel: TerrainMeshData,
    val markers: TerrainMeshData?,
    val buildings: TerrainMeshData?,
    val contours: TerrainMeshData?,
    val chips: List<TerrainChip>,
    /** Where a comet flies in from each node the radio hears, and along each way of the trace route. */
    val links: Map<Long, CometPath>,
    val routes: List<CometPath>,
    /** JPEG to drape over [terrain], null to draw it in its elevation colors. */
    val image: ByteArray?,
    val flat: Boolean,
)

/**
 * The mesh's ground as a round table beside the app panel: a [CustomMesh] heightfield on a turning wheel with compass
 * letters, and a zoom slider in an orbiter. Draws nothing without 3D content, so it only shows in Full Space.
 */
@Composable
fun SpatialTerrainMap(
    nodes: List<SpatialNodeSnapshot>,
    imagery: List<TerrainImagery>,
    buildingSource: TerrainBuildingSource?,
    route: TerrainRoute?,
    contourUnits: ContourUnits,
    buildDispatcher: CoroutineDispatcher,
    modifier: Modifier = Modifier,
) {
    // Null until the source says it has some, so the chip is only offered when it does something.
    val buildings by
        produceState<TerrainBuildingSource?>(null, buildingSource) {
            value = buildingSource?.takeIf { hasBuildings(it) }
        }
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
    var ground by remember { mutableStateOf<Ground?>(null) }
    val http = remember { HttpClient() }
    val cache = remember(full) { TerrainCache() }
    var layerId by rememberSaveable { mutableStateOf(imagery.firstOrNull()?.id) }
    val layer = imagery.firstOrNull { it.id == layerId }
    var buildingsShown by rememberSaveable { mutableStateOf(true) }
    val raised = buildings?.takeIf { buildingsShown }
    val context = LocalContext.current
    // `log.tag.MeshXrTip DEBUG` on top of the map hook: for a console screenshot, which sees the table edge on.
    val tipped = remember(context) { debugSwitch(context, "MeshXrTip") }
    // `log.tag.MeshXrContours DEBUG` on top of the map hook starts with the lines on, as nothing can press the chip.
    var contoursShown by rememberSaveable { mutableStateOf(debugSwitch(context, "MeshXrContours")) }
    // Made-up nodes are never heard, so their comets are sent in turn.
    val demo = remember(context) { debugSwitch(context, "MeshXrCity") || debugSwitch(context, "MeshXrPeaks") }
    DisposableEffect(http) { onDispose { http.close() } }

    val stage = rememberTableStage(session, motion, tipped, stringResource(Res.string.xr_terrain_map))
    TerrainEntities(
        stage,
        session,
        circle,
        nodes,
        TerrainLayers(layer, raised, contourUnits.takeIf { contoursShown }, route),
        http,
        cache,
        motion,
        buildDispatcher,
        onGround = { ground = it },
        onStatus = { status = it },
    )
    TableFrames(stage, ground, motion, nodes, demo)

    TableControls(stage, session) {
        MapControls(credits = listOfNotNull(layer?.attribution, raised?.attribution).distinct()) {
            LayerChips(imagery, layer, onSelect = { layerId = it?.id }) {
                ToggleChip(stringResource(Res.string.map_offline_terrain_contours), contoursShown) {
                    contoursShown = !contoursShown
                }
                if (buildings != null) {
                    ToggleChip(stringResource(Res.string.xr_terrain_buildings), buildingsShown) {
                        buildingsShown = !buildingsShown
                    }
                }
            }
            if (maxZoom > 1) {
                Text(stringResource(Res.string.xr_terrain_zoom), style = MaterialTheme.typography.labelLarge)
                Slider(
                    slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = { committed = slider },
                    modifier = Modifier.heightIn(min = XrTargetSize),
                )
            }
        }
    }
    StatusNotice(status, modifier)
}

@Composable
private fun StatusNotice(status: TerrainStatus, modifier: Modifier = Modifier) {
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

/** The control panel's face: [content], then who to credit for what is on the table. */
@Composable
private fun MapControls(credits: List<String>, content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(TARGET_GAP),
        ) {
            content()
            if (credits.isNotEmpty()) {
                Text(credits.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected, onClick, label = { Text(label) }, modifier = Modifier.heightIn(min = XrTargetSize))
}

/** [overlays], the chips for what is drawn over the ground, then one per basemap and one for the elevation colors. */
@Composable
private fun LayerChips(
    imagery: List<TerrainImagery>,
    selected: TerrainImagery?,
    onSelect: (TerrainImagery?) -> Unit,
    overlays: @Composable () -> Unit,
) {
    // Wrapped onto as many rows as they need: a row that scrolls is hard to work with a pointed ray. Targets keep
    // Material's 8 dp apart both ways, so one is not hit for its neighbor.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(TARGET_GAP),
        verticalArrangement = Arrangement.spacedBy(TARGET_GAP),
    ) {
        overlays()
        for (layer in imagery) {
            FilterChip(
                selected = layer.id == selected?.id,
                onClick = { onSelect(layer) },
                label = { Text(layer.label) },
                modifier = Modifier.heightIn(min = XrTargetSize),
            )
        }
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(Res.string.xr_terrain_layer_elevation)) },
            modifier = Modifier.heightIn(min = XrTargetSize),
        )
    }
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

/**
 * The table itself, which stays where the user put it while the ground on it is rebuilt: the root with its grab bar,
 * the plate the wheel is turned by, the wheel everything on the ground turns with, and the material they all share.
 *
 * @param rootScale the root's scale in the main panel's units
 */
private class TableStage(
    val root: Entity,
    val wheel: Entity,
    val material: KhronosUnlitMaterial,
    val rootScale: Float,
    private val plate: Plate,
    private val handle: CustomMesh,
) {
    /** Puts the controls where the root is now; set while they are up. */
    var placeControls: (() -> Unit)? = null
    private var rootPose: Pose? = null

    /** The root's pose in the main panel's frame, as of now. */
    fun currentRootPose(): Pose = root.getPose().also { rootPose = it }

    /** The system moves the root without telling the app, so each frame asks where it is and moves the controls. */
    fun followRoot() {
        if (isGone) return
        if (root.getPose() != rootPose) placeControls?.invoke()
    }

    private var ground: (() -> Unit)? = null

    /** Set once the table is out of the scene: the frame loop can hold it a frame longer and must not turn it. */
    var isGone = false
        private set

    private val hung = ArrayList<Entity>()

    /** Takes on an entity that goes with the table, so it goes before the root does if nothing lets it go sooner. */
    fun hang(entity: Entity) {
        hung += entity
    }

    fun letGo(entity: Entity) {
        if (hung.remove(entity)) destroy(listOf(entity), emptyList())
    }

    /** Puts a new ground on the table in place of any there. [remove] takes it off again. */
    fun show(remove: () -> Unit): Any {
        clear()
        ground = remove
        return remove
    }

    /** Takes the ground off, [shown] alone when given: a build that was replaced must not take off its successor. */
    fun clear(shown: Any? = null) {
        val remove = ground ?: return
        if (shown != null && shown !== remove) return
        ground = null
        remove()
    }

    /** The ground first: the native side aborts if the material goes while an entity still holds it. */
    fun dispose() {
        isGone = true
        clear()
        destroy(hung.toList(), emptyList())
        hung.clear()
        destroy(listOf(plate.entity, wheel, root), listOf(plate.mesh, handle))
        material.close()
    }
}

/** Null until the table is in the scene. It is made once, not with each ground, so a rebuild leaves it where it is. */
@Composable
private fun rememberTableStage(
    session: Session,
    motion: SpatialWheelMotion,
    tipped: Boolean,
    description: String,
): TableStage? {
    var stage by remember { mutableStateOf<TableStage?>(null) }
    // The wheel's input callbacks and its per-frame steps share one state, so both run on the main thread.
    val mainExecutor = remember {
        val handler = Handler(Looper.getMainLooper())
        Executor { handler.post(it) }
    }
    LaunchedEffect(session, tipped) {
        // Before any entity: a cancel while this suspends would leave them in the scene with nothing to dispose them.
        val material = KhronosUnlitMaterial.create(session, AlphaMode.OPAQUE)
        material.setBaseColorFactor(Vector4(1f, 1f, 1f, 1f))
        val handle = createMesh(session, handleMeshData(TABLE_DISK_RADIUS))
        val root = createRoot(session, tipped, handle, material, mainExecutor)
        root.entity.contentDescription = description
        val wheel = Entity.create(session, "wheel", Pose(Vector3.Zero, yawRotation(motion.shown)), root.entity)
        val plate = createPlate(session, root.entity, TABLE_DISK_RADIUS, material, motion, mainExecutor)
        val made = TableStage(root.entity, wheel, material, root.scale, plate, handle)
        made.hang(root.bar)
        stage = made
        try {
            awaitCancellation()
        } finally {
            stage = null
            made.dispose()
        }
    }
    return stage
}

/**
 * The map's controls on a panel below the table's front edge, leaning back to face the user, which goes where the table
 * is moved and is there while the ground is rebuilt. It is not a child of the table: a drag on a child of a movable
 * entity, the zoom slider's, moves the entity, so the panel is placed beside it and follows its pose instead.
 */
@Composable
private fun TableControls(stage: TableStage?, session: Session, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val composition = rememberCompositionContext()
    // A view outside the activity's own tree finds neither of these by itself, and Compose will not start without.
    val lifecycle = LocalLifecycleOwner.current
    val savedState = LocalSavedStateRegistryOwner.current
    val shown by rememberUpdatedState(content)
    DisposableEffect(stage) {
        val table = stage ?: return@DisposableEffect onDispose {}
        val density = context.resources.displayMetrics.density
        val width = (CONTROLS_WIDTH_DP * density).toInt()
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(lifecycle)
        view.setViewTreeSavedStateRegistryOwner(savedState)
        val main = session.scene.mainPanelEntity
        val panel = PanelEntity.create(session, view, IntSize2d(width, width / 2), "map controls", Pose.Identity, main)
        table.hang(panel)
        fun place() {
            // In the main panel's units, from the root's pose: the top edge under the grab bar, the rest hanging
            // forward and down from there.
            val root = table.currentRootPose()
            val half = panel.size.height / 2
            val lean = CONTROLS_LEAN_DEGREES * PI.toFloat() / 180f
            val top = Vector3(0f, CONTROLS_TOP, TABLE_DISK_RADIUS + SpatialWheelLayout.REACH + CONTROLS_GAP)
            val middle = top * table.rootScale + Vector3(0f, -half * cos(lean), half * sin(lean))
            // The system's 32 dp panel corner, which a panel made here does not get: dp to meters by the panel's own
            // size in pixels and in its units.
            panel.cornerRadius = PANEL_CORNER_DP * density * panel.size.width / panel.sizeInPixels.width
            val leaning = Quaternion.fromAxisAngle(Vector3.Right, -CONTROLS_LEAN_DEGREES)
            panel.setPose(Pose(root.transformPoint(middle), root.rotation * leaning))
        }
        table.placeControls = ::place
        place()
        view.setParentCompositionContext(composition)
        view.setContent {
            // As tall as what is on it, measured free of the panel's current height.
            Box(
                Modifier.wrapContentHeight(align = Alignment.Top, unbounded = true).onSizeChanged {
                    if (!table.isGone && it.height > 0 && it.height != panel.sizeInPixels.height) {
                        panel.sizeInPixels = IntSize2d(width, it.height)
                        place()
                    }
                },
            ) {
                shown()
            }
        }
        onDispose {
            table.placeControls = null
            table.letGo(panel)
        }
    }
}

/**
 * Once per frame, steps the wheel's ease and coast and turns the entity to match, and moves the comets. A node whose
 * last-heard time has changed since the last look sends its comet in.
 */
@Composable
private fun TableFrames(
    stage: TableStage?,
    ground: Ground?,
    motion: SpatialWheelMotion,
    nodes: List<SpatialNodeSnapshot>,
    demo: Boolean,
) {
    LaunchedEffect(stage, ground) {
        val table = stage ?: return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0.0 else (now - last) / NANOS_PER_SECOND
                last = now
                val turned = motion.step(dt)
                if (turned && !table.isGone) {
                    table.wheel.setPose(Pose(Vector3.Zero, yawRotation(motion.shown)))
                    ground?.faceFront(motion.shown)
                }
                table.followRoot()
                ground?.comets?.step(dt, demo)
            }
        }
    }
    val heard = remember(ground) { HashMap<Long, Int>() }
    // After each composition rather than in a coroutine: it only compares times, and a change sends a comet.
    SideEffect {
        for (node in nodes) {
            val before = heard.put(node.id, node.lastHeard)
            if (before != null && before != node.lastHeard) ground?.comets?.heard(node.id)
        }
    }
}

/** Builds the ground for [circle] and [layers] and puts it on [stage]'s wheel, again whenever either changes. */
@Composable
private fun TerrainEntities(
    stage: TableStage?,
    session: Session,
    circle: SpatialCircle,
    nodes: List<SpatialNodeSnapshot>,
    layers: TerrainLayers,
    http: HttpClient,
    cache: TerrainCache,
    motion: SpatialWheelMotion,
    buildDispatcher: CoroutineDispatcher,
    onGround: (Ground?) -> Unit,
    onStatus: (TerrainStatus) -> Unit,
) {
    val groundChanged by rememberUpdatedState(onGround)
    val statusChanged by rememberUpdatedState(onStatus)
    // Read when a build starts: a node update alone must not rebuild the map.
    val pinned by rememberUpdatedState(nodes)
    // Keyed on whether there are buildings, not on which source: the caller may hand over a new one on any
    // recomposition.
    val current by rememberUpdatedState(layers)
    LaunchedEffect(stage, circle, layers.imagery, layers.buildings != null, layers.contours, layers.route) {
        val table = stage ?: return@LaunchedEffect
        statusChanged(TerrainStatus.Loading)
        val built =
            try {
                withContext(buildDispatcher) { buildTerrain(circle, pinned, current, http, cache) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // The message names tile coordinates, which place the mesh to a few kilometers.
                Logger.w { "Terrain map failed to build: ${e::class.simpleName}" }
                statusChanged(TerrainStatus.Failed)
                return@LaunchedEffect
            }
        // The draped terrain is a model: SceneCore takes a run-time image only inside one. So is each chip.
        val models = ArrayList<GltfModel>()
        val chipModels = ArrayList<Pair<TerrainChip, GltfModel>>()
        var made = false
        try {
            built.image?.let { image ->
                val glb = withContext(buildDispatcher) { terrainGlb(built.terrain, image, "image/jpeg") }
                models += GltfModel.create(session, glb, "terrain")
            }
            val quad = chipMeshData()
            for (chip in built.chips) {
                val png = withContext(buildDispatcher) { ChipImage.draw(chip.name, chip.color) }
                val glb = terrainGlb(quad, png, "image/png", cutout = true)
                chipModels += chip to GltfModel.create(session, glb, "chip")
            }
            made = true
        } finally {
            // A cancel part way leaves models with nothing to close them.
            if (!made) {
                models.forEach { it.close() }
                chipModels.forEach { it.second.close() }
            }
        }
        // Nothing suspends from here to the hand-over: a cancel in between would leave entities with nothing to
        // dispose them.
        val ground = createGround(session, table, built, models, chipModels, motion.shown)
        val shown = table.show {
            ground.retire()
            destroy(ground.entities, ground.meshes)
            models.forEach { it.close() }
            chipModels.forEach { it.second.close() }
        }
        groundChanged(ground)
        statusChanged(if (built.flat) TerrainStatus.Flat else TerrainStatus.Ready)
        try {
            awaitCancellation()
        } finally {
            groundChanged(null)
            table.clear(shown)
        }
    }
}

/** A chip's entity and where it stands on the wheel. */
private class ChipEntity(val entity: Entity, val at: Vector3)

/** What one build put on the wheel. */
private class Ground(
    val entities: List<Entity>,
    val meshes: List<CustomMesh>,
    val comets: TerrainComets?,
    private val chips: List<ChipEntity>,
) {
    private var retired = false

    /** Called before the entities are taken out of the scene: the frame loop can hold this a frame longer. */
    fun retire() {
        retired = true
        comets?.retire()
    }

    /** Turns each chip against the wheel, which is [yaw] round, so it faces the front whichever way the map is. */
    fun faceFront(yaw: Float) {
        if (retired) return
        val against = yawRotation(-yaw)
        for (chip in chips) chip.entity.setPose(Pose(chip.at, against))
    }
}

private fun createGround(
    session: Session,
    table: TableStage,
    built: BuiltTerrain,
    models: List<GltfModel>,
    chipModels: List<Pair<TerrainChip, GltfModel>>,
    yaw: Float,
): Ground {
    val material = table.material
    val plain =
        listOfNotNull(
            built.terrain.takeIf { built.image == null },
            built.skirt,
            built.bezel,
            built.markers,
            built.buildings,
            built.contours,
        )
    val meshes = plain.map { createMesh(session, it) }
    val drawn = meshes.map { MeshEntity.create(session, it, listOf(material), 0, Pose.Identity, table.wheel) }
    val entities = drawn + models.map { GltfModelEntity.create(session, it, Pose.Identity, table.wheel) }
    val against = yawRotation(-yaw)
    val chips = chipModels.map { (chip, model) ->
        val at = Vector3(chip.at.x, chip.at.y, chip.at.z)
        ChipEntity(GltfModelEntity.create(session, model, Pose(at, against), table.wheel), at)
    }
    // Nothing flies with animations turned off in the system settings.
    val comets = if (ValueAnimator.areAnimatorsEnabled()) createComets(session, built, table.wheel, material) else null
    return Ground(
        comets?.entities.orEmpty() + chips.map { it.entity } + entities,
        meshes + comets?.meshes.orEmpty(),
        comets?.comets,
        chips,
    )
}

/**
 * Entities go before the meshes and material they borrow: the native side aborts if either is released while an entity
 * still holds it, and detaching an entity does not release it.
 */
@Suppress("DEPRECATION")
private fun destroy(entities: List<Entity>, meshes: List<CustomMesh>) {
    entities.forEach { it.dispose() }
    meshes.forEach { it.close() }
}

/**
 * A root beside the main panel's right edge, scaled so the table is its real size whatever the panel's scale. The root
 * draws the grab bar at the table's front: the system moves an entity by its own shape, so pinching the bar carries the
 * whole table, and the ring around the wheel is left for turning it.
 */
private fun createRoot(
    session: Session,
    tipped: Boolean,
    handle: CustomMesh,
    material: KhronosUnlitMaterial,
    executor: Executor,
): Root {
    val panel = session.scene.mainPanelEntity
    val size = panel.size
    val scale = panel.getScale(Space.ACTIVITY)
    val fit = SpatialVolumeFit(TABLE_VOLUME_METERS, TABLE_VOLUME_METERS, TABLE_DISK_RADIUS)
    val reach = fit.diskRadius + SpatialWheelLayout.REACH + TABLE_GAP_METERS
    val at = Vector3((size.width / 2f + reach / scale), -size.height * DROP_FRACTION, 0f)
    val pose =
        if (tipped) {
            // In front of the panel's lower half, its far side tipped up, so the console's camera sees the table
            // and the controls under its front edge.
            val below = Vector3(0f, -size.height / 2f - TIPPED_DROP_METERS / scale, TIPPED_FORWARD_METERS / scale)
            Pose(below, Quaternion.fromAxisAngle(Vector3.Right, TIPPED_DEGREES))
        } else {
            Pose(at)
        }
    val root = Entity.create(session, "table", pose, panel)
    root.setScale(fit.scale / scale)
    // The bar is the grip. The system's own movable only moves its entity from its own decorations, which a bar drawn
    // here is not, so the bar takes the drag itself and swings the table round the user.
    val bar = MeshEntity.create(session, handle, listOf(material), 0, Pose.Identity, root)
    var drag: RayDrag? = null
    bar.addComponent(
        InteractableComponent.create(session, executor) { event ->
            val origin = Vec3(event.origin.x, event.origin.y, event.origin.z)
            val direction = Vec3(event.direction.x, event.direction.y, event.direction.z)
            when (event.action) {
                InputEvent.Action.DOWN -> {
                    val grip = bar.getPose(Space.ACTIVITY).translation
                    val away = Vector3(grip.x - origin.x, grip.y - origin.y, grip.z - origin.z)
                    drag = RayDrag(origin, direction, away.length)
                }

                InputEvent.Action.MOVE ->
                    drag?.let {
                        val moved = it.move(origin, direction)
                        // From the activity's space into the main panel's, where the table's pose is kept.
                        val inPanel =
                            panel.getPose(Space.ACTIVITY).rotation.inverse *
                                Vector3(moved.x, moved.y, moved.z) *
                                (1f / panel.getScale(Space.ACTIVITY))
                        val now = root.getPose()
                        root.setPose(Pose(now.translation + inPanel, now.rotation))
                    }

                InputEvent.Action.UP,
                InputEvent.Action.CANCEL,
                -> drag = null

                else -> Unit
            }
        },
    )
    return Root(root, bar, fit.scale / scale)
}

/** The table's root, and its scale in the main panel's units. */
private class Root(val entity: Entity, val bar: Entity, val scale: Float)

/** The comets, their entities and the one mesh each color of comet shares. */
private class CometEntities(val comets: TerrainComets, val entities: List<Entity>, val meshes: List<CustomMesh>)

/** A hidden comet on the wheel for each link, the first [MAX_LINK_COMETS] of them, and each way of the trace route. */
private fun createComets(
    session: Session,
    built: BuiltTerrain,
    wheel: Entity,
    material: KhronosUnlitMaterial,
): CometEntities {
    val meshes = HashMap<Rgb, CustomMesh>()
    val entities = ArrayList<Entity>()
    fun comet(path: CometPath): Comet {
        val mesh = meshes.getOrPut(path.color) { createMesh(session, cometMeshData(path.color)) }
        val entity = MeshEntity.create(session, mesh, listOf(material), 0, Pose.Identity, wheel)
        entity.setEnabled(false)
        entities += entity
        return Comet(path, entity)
    }
    val links = built.links.entries.take(MAX_LINK_COMETS).associate { it.key to comet(it.value) }
    return CometEntities(TerrainComets(links, built.routes.map(::comet)), entities, meshes.values.toList())
}

/** A bar under the table's front edge, like a window's grab bar, in model units. */
private fun handleMeshData(diskRadius: Float): TerrainMeshData {
    val acc = MeshAccumulator()
    val front = diskRadius + SpatialWheelLayout.REACH + HANDLE_GAP
    acc.box(0f, PLATE_TOP - HANDLE_DROP, front, HANDLE_HALF_LENGTH, HANDLE_HALF, HANDLE_HALF, Rgb(0.82f, 0.84f, 0.88f))
    return acc.build()
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
    // Out past the knobs, so the ring around the wheel is there to point at and drag.
    val radius = diskRadius + SpatialWheelLayout.REACH + PLATE_MARGIN
    acc.disk(radius, PLATE_TOP - 2 * PLATE_HALF, PLATE_TOP, Rgb(0.1f, 0.1f, 0.12f))
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

/** What to draw on the ground: the image over it, the buildings, the contour lines and a trace route. */
private class TerrainLayers(
    val imagery: TerrainImagery?,
    val buildings: TerrainBuildingSource?,
    val contours: ContourUnits?,
    val route: TerrainRoute?,
)

private suspend fun buildTerrain(
    circle: SpatialCircle,
    nodes: List<SpatialNodeSnapshot>,
    layers: TerrainLayers,
    http: HttpClient,
    cache: TerrainCache,
): BuiltTerrain = coroutineScope {
    val imagery = layers.imagery
    val buildings = layers.buildings
    val contours = layers.contours
    val frame = SpatialFrame(circle.bounds)
    // Fetched while the elevation is sampled, and not again when only an overlay or the route changed.
    val kept = imagery?.let { cache.image(circle, it.id) }
    val draped =
        imagery
            ?.takeIf { kept == null }
            ?.let { layer ->
                async { terrainImage(frame.bounds, layer, http)?.also { cache.keep(circle, layer.id, it) } }
            }
    val footprints = buildings?.let { async { footprintsOrNone(it, frame.bounds) } }
    val grid = cache.grid(circle) ?: sampleGround(frame, http).also { cache.keep(circle, it) }
    val relief = (grid.maxElevation - grid.baseElevation).toDouble()
    val transform =
        SpatialModelTransform(
            frame,
            grid.baseElevation.toDouble(),
            grid.maxElevation.toDouble(),
            verticalExaggeration = reliefExaggeration(relief, maxOf(frame.widthMeters, frame.depthMeters)),
        )
    val geometry = SpatialTerrainGeometry(grid, transform)
    val image = kept ?: draped?.await()
    val markers = nodeMarkers(geometry, nodes, circle)
    val lines = contours?.let { TerrainContours.meshData(geometry, it, onImage = image != null) }
    val raised = footprints?.await()?.let { TerrainBuildings.meshData(it, geometry) }
    Logger.d { "Terrain triangles: contours ${lines.triangleCount}, buildings ${raised.triangleCount}" }
    BuiltTerrain(
        geometry = geometry,
        terrain = terrainMeshData(geometry, grid.baseElevation.toDouble(), grid.maxElevation.toDouble(), image != null),
        skirt = if (image != null) skirtMeshData(geometry) else null,
        bezel = bezelMeshData(transform.diskRadius),
        markers = markerMeshData(markers, layers.route),
        buildings = raised,
        contours = lines,
        chips = terrainChips(markers),
        links = linkPaths(markers),
        routes = routePaths(markers, layers.route),
        image = image,
        flat = !grid.hasAnyTerrain,
    )
}

private val TerrainMeshData?.triangleCount: Int
    get() = if (this == null) 0 else indices.size / 3

/** A `log.tag.<tag> DEBUG` switch, read only where the map hook is on. */
private fun debugSwitch(context: Context, tag: String) = opensMapAtLaunch(context) && Log.isLoggable(tag, Log.DEBUG)

private suspend fun sampleGround(frame: SpatialFrame, http: HttpClient): SpatialHeightGrid {
    val source = MapterhornElevationSource(http)
    val plan =
        SpatialHeightGrid.plan(
            frame,
            maxTriangles = MAX_TRIANGLES,
            dataMaxZoom = minOf(source.finestDataZoom(), MapterhornEndpoints.TILES_MAX_ZOOM),
        )
    return SpatialHeightGrid.sample(frame, plan, source).cleaned()
}

private suspend fun hasBuildings(source: TerrainBuildingSource): Boolean = try {
    source.available()
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Logger.w { "Buildings could not be checked: ${e::class.simpleName}" }
    false
}

/** The map is drawn without buildings when they cannot be read. */
private suspend fun footprintsOrNone(source: TerrainBuildingSource, bounds: GeoBounds): List<BuildingFootprint> = try {
    source.footprints(bounds)
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    Logger.w { "Buildings could not be read: ${e::class.simpleName}" }
    emptyList()
}

/** A rotation about +y by [radians], the sense the wheel's yaw is in. */
private fun yawRotation(radians: Float): Quaternion =
    Quaternion.fromAxisAngle(Vector3.Up, radians * 180f / PI.toFloat())

private const val MAX_TRIANGLES = 90_000

/** The map is a circle in a square one model unit on a side, whatever ground it shows. */
private const val TABLE_DISK_RADIUS = 0.5f
private const val CONTROLS_WIDTH_DP = 480
private const val PANEL_CORNER_DP = 32f
private val TARGET_GAP = 8.dp
private const val CONTROLS_LEAN_DEGREES = 35f
private const val CONTROLS_TOP = -0.08f
private const val CONTROLS_GAP = 0.05f
private const val MAX_LINK_COMETS = 24
private const val TABLE_VOLUME_METERS = 0.8f
private const val TABLE_GAP_METERS = 0.12f
private const val DROP_FRACTION = 0.25f
private const val PLATE_HALF = 0.004f
private const val PLATE_MARGIN = 0.012f
private const val HANDLE_GAP = 0.04f
private const val HANDLE_DROP = 0.03f
private const val HANDLE_HALF_LENGTH = 0.14f
private const val HANDLE_HALF = 0.02f
private const val TIPPED_DROP_METERS = -0.3f
private const val TIPPED_FORWARD_METERS = 0.35f
private const val TIPPED_DEGREES = 50f

/** Under the terrain's skirt and the bezel, which reach this far below the ground. */
private const val PLATE_TOP = -0.02f
private const val NANOS_PER_SECOND = 1_000_000_000.0
private const val MILLIS_PER_SECOND = 1_000.0
