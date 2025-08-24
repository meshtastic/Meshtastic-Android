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

@file:Suppress("MagicNumber")

package com.geeksville.mesh.ui.map

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.TripOrigin
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarDefaults.ScreenOffset
import androidx.compose.material3.FloatingToolbarExitDirection.Companion.End
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberFloatingToolbarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.ConfigProtos.Config.DisplayConfig.DisplayUnits
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.MeshProtos.Position
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.warn
import com.geeksville.mesh.copy
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.components.ClusterItemsListDialog
import com.geeksville.mesh.ui.map.components.CustomMapLayersSheet
import com.geeksville.mesh.ui.map.components.CustomTileProviderManagerSheet
import com.geeksville.mesh.ui.map.components.EditWaypointDialog
import com.geeksville.mesh.ui.map.components.MapControlsOverlay
import com.geeksville.mesh.ui.map.components.NodeClusterMarkers
import com.geeksville.mesh.ui.map.components.WaypointMarkers
import com.geeksville.mesh.ui.metrics.HEADING_DEG
import com.geeksville.mesh.ui.metrics.formatPositionTime
import com.geeksville.mesh.ui.node.DEG_D
import com.geeksville.mesh.ui.node.components.NodeChip
import com.geeksville.mesh.util.formatAgo
import com.geeksville.mesh.util.metersIn
import com.geeksville.mesh.util.mpsToKmph
import com.geeksville.mesh.util.mpsToMph
import com.geeksville.mesh.util.toString
import com.geeksville.mesh.waypoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerInfoWindowComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.TileOverlay
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.google.maps.android.compose.widgets.DisappearingScaleBar
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.DateFormat

private const val MIN_TRACK_POINT_DISTANCE_METERS = 20f

@Suppress("ReturnCount")
private fun filterNodeTrack(nodeTrack: List<Position>?): List<Position> {
    if (nodeTrack.isNullOrEmpty()) return emptyList()

    val sortedTrack = nodeTrack.sortedBy { it.time }
    if (sortedTrack.size <= 2) return sortedTrack.map { it }

    val filteredPoints = mutableListOf<MeshProtos.Position>()
    var lastAddedPointProto = sortedTrack.first()
    filteredPoints.add(lastAddedPointProto)

    for (i in 1 until sortedTrack.size - 1) {
        val currentPointProto = sortedTrack[i]
        val currentPoint = currentPointProto.toLatLng()
        val lastAddedPoint = lastAddedPointProto.toLatLng()
        val distanceResults = FloatArray(1)
        Location.distanceBetween(
            lastAddedPoint.latitude,
            lastAddedPoint.longitude,
            currentPoint.latitude,
            currentPoint.longitude,
            distanceResults,
        )
        if (distanceResults[0] > MIN_TRACK_POINT_DISTANCE_METERS) {
            filteredPoints.add(currentPointProto)
            lastAddedPointProto = currentPointProto
        }
    }

    val lastOriginalPointProto = sortedTrack.last()
    if (filteredPoints.last() != lastOriginalPointProto) {
        val distanceResults = FloatArray(1)
        val lastAddedPoint = lastAddedPointProto.toLatLng()
        val lastOriginalPoint = lastOriginalPointProto.toLatLng()
        Location.distanceBetween(
            lastAddedPoint.latitude,
            lastAddedPoint.longitude,
            lastOriginalPoint.latitude,
            lastOriginalPoint.longitude,
            distanceResults,
        )
        if (distanceResults[0] > MIN_TRACK_POINT_DISTANCE_METERS || filteredPoints.size == 1) {
            filteredPoints.add(lastAddedPointProto)
        }
    }
    return filteredPoints
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(MapsComposeExperimentalApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MapView(
    uiViewModel: UIViewModel,
    mapViewModel: MapViewModel = hiltViewModel(),
    navigateToNodeDetails: (Int) -> Unit,
    focusedNodeNum: Int? = null,
    nodeTrack: List<Position>? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mapLayers by mapViewModel.mapLayers.collectAsStateWithLifecycle()
    var hasLocationPermission by remember { mutableStateOf(false) }
    val displayUnits by mapViewModel.displayUnits.collectAsStateWithLifecycle()

    LocationPermissionsHandler { isGranted -> hasLocationPermission = isGranted }

    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName = uri.getFileName(context)
                    mapViewModel.addMapLayer(uri, fileName)
                }
            }
        }

    var mapFilterMenuExpanded by remember { mutableStateOf(false) }
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    val ourNodeInfo by uiViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    val savedCameraPosition by mapViewModel.cameraPosition.collectAsStateWithLifecycle()

    val selectedGoogleMapType by mapViewModel.selectedGoogleMapType.collectAsStateWithLifecycle()
    val currentCustomTileProviderUrl by mapViewModel.selectedCustomTileProviderUrl.collectAsStateWithLifecycle()

    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileManagerSheet by remember { mutableStateOf(false) }

    val defaultLatLng = LatLng(0.0, 0.0)
    val cameraPositionState = rememberCameraPositionState {
        position =
            savedCameraPosition?.let {
                CameraPosition(LatLng(it.targetLat, it.targetLng), it.zoom, it.tilt, it.bearing)
            } ?: CameraPosition.fromLatLngZoom(defaultLatLng, 7f)
    }

    val floatingToolbarState = rememberFloatingToolbarState()
    val exitAlwaysScrollBehavior =
        FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = End, state = floatingToolbarState)

    LaunchedEffect(cameraPositionState.isMoving, floatingToolbarState.offsetLimit) {
        val targetOffset =
            if (cameraPositionState.isMoving) {
                floatingToolbarState.offsetLimit
            } else {
                mapViewModel.onCameraPositionChanged(cameraPositionState.position)
                0f
            }
        if (floatingToolbarState.offset != targetOffset) {
            if (targetOffset == 0f || floatingToolbarState.offsetLimit != 0f) {
                launch {
                    animate(initialValue = floatingToolbarState.offset, targetValue = targetOffset) { value, _ ->
                        floatingToolbarState.offset = value
                    }
                }
            }
        }
    }

    val allNodes by
        mapViewModel.nodes
            .map { nodes -> nodes.filter { node -> node.validPosition != null } }
            .collectAsStateWithLifecycle(listOf())
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle(emptyMap())
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }

    val filteredNodes =
        if (mapFilterState.onlyFavorites) {
            allNodes.filter { it.isFavorite || it.num == ourNodeInfo?.num }
        } else {
            allNodes
        }

    val nodeClusterItems =
        filteredNodes.map { node ->
            val latLng = LatLng(node.position.latitudeI * DEG_D, node.position.longitudeI * DEG_D)
            NodeClusterItem(
                node = node,
                nodePosition = latLng,
                nodeTitle = "${node.user.shortName} ${formatAgo(node.position.time)}",
                nodeSnippet = "${node.user.longName}",
            )
        }
    val isConnected by uiViewModel.isConnectedStateFlow.collectAsStateWithLifecycle()
    val theme by uiViewModel.theme.collectAsStateWithLifecycle()
    val dark =
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> isSystemInDarkTheme()
            else -> isSystemInDarkTheme()
        }
    val mapColorScheme =
        when (dark) {
            true -> ComposeMapColorScheme.DARK
            else -> ComposeMapColorScheme.LIGHT
        }

    var showLayersBottomSheet by remember { mutableStateOf(false) }

    val onAddLayerClicked = {
        val intent =
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                val mimeTypes =
                    arrayOf(
                        "application/vnd.google-earth.kml+xml",
                        "application/vnd.google-earth.kmz",
                        "application/vnd.geo+json",
                        "application/geo+json",
                        "application/json",
                    )
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            }
        filePickerLauncher.launch(intent)
    }
    val onRemoveLayer = { layerId: String -> mapViewModel.removeMapLayer(layerId) }
    val onToggleVisibility = { layerId: String -> mapViewModel.toggleLayerVisibility(layerId) }

    val effectiveGoogleMapType =
        if (currentCustomTileProviderUrl != null) {
            MapType.NONE
        } else {
            selectedGoogleMapType
        }

    var showClusterItemsDialog by remember { mutableStateOf<List<NodeClusterItem>?>(null) }

    Scaffold(modifier = Modifier.nestedScroll(exitAlwaysScrollBehavior)) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            GoogleMap(
                mapColorScheme = mapColorScheme,
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings =
                MapUiSettings(
                    zoomControlsEnabled = true,
                    mapToolbarEnabled = true,
                    compassEnabled = true,
                    myLocationButtonEnabled = hasLocationPermission,
                    rotationGesturesEnabled = true,
                    scrollGesturesEnabled = true,
                    tiltGesturesEnabled = true,
                    zoomGesturesEnabled = true,
                ),
                properties =
                MapProperties(mapType = effectiveGoogleMapType, isMyLocationEnabled = hasLocationPermission),
                onMapLongClick = { latLng ->
                    if (isConnected) {
                        val newWaypoint = waypoint {
                            latitudeI = (latLng.latitude / DEG_D).toInt()
                            longitudeI = (latLng.longitude / DEG_D).toInt()
                        }
                        editingWaypoint = newWaypoint
                    }
                },
                onMapLoaded = {
                    if (
                        savedCameraPosition?.targetLat == defaultLatLng.latitude &&
                        savedCameraPosition?.targetLng == defaultLatLng.longitude
                    ) {
                        val pointsToBound: List<LatLng> =
                            when {
                                !nodeTrack.isNullOrEmpty() -> nodeTrack.map { it.toLatLng() }

                                allNodes.isNotEmpty() || displayableWaypoints.isNotEmpty() ->
                                    allNodes.mapNotNull { it.toLatLng() } + displayableWaypoints.map { it.toLatLng() }

                                else -> emptyList()
                            }

                        if (pointsToBound.isNotEmpty()) {
                            val bounds = LatLngBounds.builder().apply { pointsToBound.forEach(::include) }.build()

                            val padding = if (!pointsToBound.isEmpty()) 100 else 48

                            try {
                                coroutineScope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                                }
                            } catch (e: IllegalStateException) {
                                warn("MapView Could not animate to bounds: ${e.message}")
                            }
                        }
                    }
                },
            ) {
                key(currentCustomTileProviderUrl) {
                    currentCustomTileProviderUrl?.let { url ->
                        mapViewModel.createUrlTileProvider(url)?.let { tileProvider ->
                            TileOverlay(tileProvider = tileProvider, fadeIn = true, transparency = 0f, zIndex = -1f)
                        }
                    }
                }

                if (nodeTrack != null && focusedNodeNum != null) {
                    val originalLatLngs =
                        nodeTrack.sortedBy { it.time }.map { LatLng(it.latitudeI * DEG_D, it.longitudeI * DEG_D) }
                    val filteredLatLngs = filterNodeTrack(nodeTrack)

                    val focusedNode = allNodes.find { it.num == focusedNodeNum }
                    val polylineColor = focusedNode?.colors?.let { Color(it.first) } ?: Color.Blue
                    if (originalLatLngs.isNotEmpty()) {
                        focusedNode?.let {
                            MarkerComposable(
                                state = rememberUpdatedMarkerState(position = originalLatLngs.first()),
                                zIndex = 1f,
                            ) {
                                NodeChip(node = it, isThisNode = false, isConnected = false, onAction = {})
                            }
                        }
                    }

                    val pointsForMarkers =
                        if (originalLatLngs.isNotEmpty() && focusedNode != null) {
                            filteredLatLngs.drop(1)
                        } else {
                            filteredLatLngs
                        }

                    pointsForMarkers.forEachIndexed { index, position ->
                        val markerState = rememberUpdatedMarkerState(position = position.toLatLng())
                        val dateFormat = remember {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                        }
                        val alpha = 1 - (index.toFloat() / pointsForMarkers.size.toFloat())
                        MarkerInfoWindowComposable(
                            state = markerState,
                            title = stringResource(R.string.position),
                            snippet = formatAgo(position.time),
                            zIndex = alpha,
                            infoContent = {
                                PositionInfoWindowContent(
                                    position = position,
                                    dateFormat = dateFormat,
                                    displayUnits = displayUnits,
                                )
                            },
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.TripOrigin,
                                contentDescription = stringResource(R.string.track_point),
                                modifier = Modifier.padding(8.dp),
                                tint = polylineColor.copy(alpha = alpha),
                            )
                        }
                    }
                    if (filteredLatLngs.size > 1) {
                        Polyline(
                            points = filteredLatLngs.map { it.toLatLng() },
                            jointType = JointType.ROUND,
                            endCap = RoundCap(),
                            startCap = RoundCap(),
                            geodesic = true,
                            color = polylineColor,
                            width = 8f,
                            zIndex = 0f,
                        )
                    }
                } else {
                    NodeClusterMarkers(
                        nodeClusterItems = nodeClusterItems,
                        mapFilterState = mapFilterState,
                        navigateToNodeDetails = navigateToNodeDetails,
                        onClusterClick = { cluster ->
                            val items = cluster.items.toList()
                            val allSameLocation = items.size > 1 && items.all { it.position == items.first().position }

                            if (allSameLocation) {
                                showClusterItemsDialog = items
                            } else {
                                val bounds = LatLngBounds.builder()
                                cluster.items.forEach { bounds.include(it.position) }
                                coroutineScope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 100),
                                    )
                                }
                                debug("Cluster clicked! $cluster")
                            }
                            true
                        },
                    )
                }

                WaypointMarkers(
                    displayableWaypoints = displayableWaypoints,
                    mapFilterState = mapFilterState,
                    myNodeNum = uiViewModel.myNodeNum ?: 0,
                    isConnected = isConnected,
                    unicodeEmojiToBitmapProvider = ::unicodeEmojiToBitmap,
                    onEditWaypointRequest = { waypointToEdit -> editingWaypoint = waypointToEdit },
                )

                MapEffect(mapLayers) { map ->
                    mapLayers.forEach { layerItem ->
                        coroutineScope.launch {
                            mapViewModel.loadMapLayerIfNeeded(map, layerItem)
                            when (layerItem.layerType) {
                                LayerType.KML -> {
                                    layerItem.kmlLayerData?.let { kmlLayer ->
                                        if (layerItem.isVisible && !kmlLayer.isLayerOnMap) {
                                            kmlLayer.addLayerToMap()
                                        } else if (!layerItem.isVisible && kmlLayer.isLayerOnMap) {
                                            kmlLayer.removeLayerFromMap()
                                        }
                                    }
                                }
                                LayerType.GEOJSON -> {
                                    layerItem.geoJsonLayerData?.let { geoJsonLayer ->
                                        if (layerItem.isVisible && !geoJsonLayer.isLayerOnMap) {
                                            geoJsonLayer.addLayerToMap()
                                        } else if (!layerItem.isVisible && geoJsonLayer.isLayerOnMap) {
                                            geoJsonLayer.removeLayerFromMap()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DisappearingScaleBar(cameraPositionState = cameraPositionState)

            editingWaypoint?.let { waypointToEdit ->
                EditWaypointDialog(
                    waypoint = waypointToEdit,
                    onSendClicked = { updatedWp ->
                        var finalWp = updatedWp
                        if (updatedWp.id == 0) {
                            finalWp = finalWp.copy { id = uiViewModel.generatePacketId() ?: 0 }
                        }
                        if (updatedWp.icon == 0) {
                            finalWp = finalWp.copy { icon = 0x1F4CD }
                        }

                        uiViewModel.sendWaypoint(finalWp)
                        editingWaypoint = null
                    },
                    onDeleteClicked = { wpToDelete ->
                        if (wpToDelete.lockedTo == 0 && isConnected && wpToDelete.id != 0) {
                            val deleteMarkerWp = wpToDelete.copy { expire = 1 }
                            uiViewModel.sendWaypoint(deleteMarkerWp)
                        }
                        uiViewModel.deleteWaypoint(wpToDelete.id)
                        editingWaypoint = null
                    },
                    onDismissRequest = { editingWaypoint = null },
                )
            }

            MapControlsOverlay(
                modifier = Modifier.align(Alignment.CenterEnd).offset(x = -ScreenOffset),
                mapFilterMenuExpanded = mapFilterMenuExpanded,
                onMapFilterMenuDismissRequest = { mapFilterMenuExpanded = false },
                onToggleMapFilterMenu = { mapFilterMenuExpanded = true },
                mapViewModel = mapViewModel,
                mapTypeMenuExpanded = mapTypeMenuExpanded,
                onMapTypeMenuDismissRequest = { mapTypeMenuExpanded = false },
                onToggleMapTypeMenu = { mapTypeMenuExpanded = true },
                onManageLayersClicked = { showLayersBottomSheet = true },
                onManageCustomTileProvidersClicked = {
                    mapTypeMenuExpanded = false
                    showCustomTileManagerSheet = true
                },
                showFilterButton = focusedNodeNum == null,
                scrollBehavior = exitAlwaysScrollBehavior,
            )
        }
        if (showLayersBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showLayersBottomSheet = false }) {
                CustomMapLayersSheet(mapLayers, onToggleVisibility, onRemoveLayer, onAddLayerClicked)
            }
        }
        showClusterItemsDialog?.let {
            ClusterItemsListDialog(
                items = it,
                onDismiss = { showClusterItemsDialog = null },
                onItemClick = { item ->
                    navigateToNodeDetails(item.node.num)
                    showClusterItemsDialog = null
                },
            )
        }
        if (showCustomTileManagerSheet) {
            ModalBottomSheet(onDismissRequest = { showCustomTileManagerSheet = false }) {
                CustomTileProviderManagerSheet(mapViewModel = mapViewModel)
            }
        }
    }
}

internal fun convertIntToEmoji(unicodeCodePoint: Int): String = try {
    String(Character.toChars(unicodeCodePoint))
} catch (e: IllegalArgumentException) {
    Timber.w(e, "Invalid unicode code point: $unicodeCodePoint")
    "\uD83D\uDCCD"
}

internal fun unicodeEmojiToBitmap(icon: Int): BitmapDescriptor {
    val unicodeEmoji = convertIntToEmoji(icon)
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 64f
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
        }

    val baseline = -paint.ascent()
    val width = (paint.measureText(unicodeEmoji) + 0.5f).toInt()
    val height = (baseline + paint.descent() + 0.5f).toInt()
    val image = createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(unicodeEmoji, width / 2f, baseline, paint)

    return BitmapDescriptorFactory.fromBitmap(image)
}

@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: android.content.Context): String {
    var name = this.lastPathSegment ?: "layer_${System.currentTimeMillis()}"
    if (this.scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    name = cursor.getString(displayNameIndex)
                }
            }
        }
    }
    return name
}

data class NodeClusterItem(val node: Node, val nodePosition: LatLng, val nodeTitle: String, val nodeSnippet: String) :
    ClusterItem {
    override fun getPosition(): LatLng = nodePosition

    override fun getTitle(): String = nodeTitle

    override fun getSnippet(): String = nodeSnippet

    override fun getZIndex(): Float? = null

    fun getPrecisionMeters(): Double? {
        val precisionMap =
            mapOf(
                10 to 23345.484932,
                11 to 11672.7369,
                12 to 5836.36288,
                13 to 2918.175876,
                14 to 1459.0823719999053,
                15 to 729.53562,
                16 to 364.7622,
                17 to 182.375556,
                18 to 91.182212,
                19 to 45.58554,
            )
        return precisionMap[this.node.position.precisionBits]
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("LongMethod")
private fun PositionInfoWindowContent(
    position: Position,
    dateFormat: DateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM),
    displayUnits: DisplayUnits = DisplayUnits.METRIC,
) {
    @Composable
    fun PositionRow(label: String, value: String) {
        Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text(value, style = MaterialTheme.typography.labelMediumEmphasized)
        }
    }

    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            PositionRow(
                label = stringResource(R.string.latitude),
                value = "%.5f".format(position.latitudeI * com.geeksville.mesh.ui.metrics.DEG_D),
            )

            PositionRow(
                label = stringResource(R.string.longitude),
                value = "%.5f".format(position.longitudeI * com.geeksville.mesh.ui.metrics.DEG_D),
            )

            PositionRow(label = stringResource(R.string.sats), value = position.satsInView.toString())

            PositionRow(
                label = stringResource(R.string.alt),
                value = position.altitude.metersIn(displayUnits).toString(displayUnits),
            )

            PositionRow(label = stringResource(R.string.speed), value = speedFromPosition(position, displayUnits))

            PositionRow(
                label = stringResource(R.string.heading),
                value = "%.0f°".format(position.groundTrack * HEADING_DEG),
            )

            PositionRow(label = stringResource(R.string.timestamp), value = formatPositionTime(position, dateFormat))
        }
    }
}

@Composable
private fun speedFromPosition(position: Position, displayUnits: DisplayUnits): String {
    val speedInMps = position.groundSpeed
    val mpsText = "%d m/s".format(speedInMps)
    val speedText =
        if (speedInMps > 10) {
            when (displayUnits) {
                DisplayUnits.METRIC -> "%.1f Km/h".format(position.groundSpeed.mpsToKmph())
                DisplayUnits.IMPERIAL -> "%.1f mph".format(position.groundSpeed.mpsToMph())
                else -> mpsText // Fallback or handle UNRECOGNIZED
            }
        } else {
            mpsText
        }
    return speedText
}

private fun Position.toLatLng(): LatLng = LatLng(this.latitudeI * DEG_D, this.longitudeI * DEG_D)

private fun Node.toLatLng(): LatLng? = this.position.toLatLng()

private fun Waypoint.toLatLng(): LatLng = LatLng(this.latitudeI * DEG_D, this.longitudeI * DEG_D)
