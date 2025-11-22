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

// Import modularized MapLibre components

import android.annotation.SuppressLint
import android.graphics.RectF
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.TransitionOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.meshtastic.core.database.model.Node
import org.meshtastic.feature.map.LayerType
import org.meshtastic.feature.map.MapLayerItem
import org.meshtastic.feature.map.MapViewModel
import org.meshtastic.feature.map.component.CustomMapLayersSheet
import org.meshtastic.feature.map.component.EditWaypointDialog
import org.meshtastic.feature.map.component.TileCacheManagementSheet
import org.meshtastic.feature.map.maplibre.BaseMapStyle
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_CIRCLE_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_LIST_FETCH_MAX
import org.meshtastic.feature.map.maplibre.MapLibreConstants.CLUSTER_RADIAL_MAX
import org.meshtastic.feature.map.maplibre.MapLibreConstants.DEG_D
import org.meshtastic.feature.map.maplibre.MapLibreConstants.HEATMAP_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.HEATMAP_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_CLUSTER_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_LAYER_NOCLUSTER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.NODES_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_LINE_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.TRACK_POINTS_SOURCE_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_LAYER_ID
import org.meshtastic.feature.map.maplibre.MapLibreConstants.WAYPOINTS_SOURCE_ID
import org.meshtastic.feature.map.maplibre.core.activateLocationComponentForStyle
import org.meshtastic.feature.map.maplibre.core.buildMeshtasticStyle
import org.meshtastic.feature.map.maplibre.core.ensureHeatmapSourceAndLayer
import org.meshtastic.feature.map.maplibre.core.ensureImportedLayerSourceAndLayers
import org.meshtastic.feature.map.maplibre.core.ensureSourcesAndLayers
import org.meshtastic.feature.map.maplibre.core.ensureTrackSourcesAndLayers
import org.meshtastic.feature.map.maplibre.core.logStyleState
import org.meshtastic.feature.map.maplibre.core.nodesToFeatureCollectionJsonWithSelection
import org.meshtastic.feature.map.maplibre.core.nodesToHeatmapFeatureCollection
import org.meshtastic.feature.map.maplibre.core.positionsToLineStringFeature
import org.meshtastic.feature.map.maplibre.core.positionsToPointFeatures
import org.meshtastic.feature.map.maplibre.core.reinitializeStyleAfterSwitch
import org.meshtastic.feature.map.maplibre.core.removeImportedLayerSourceAndLayers
import org.meshtastic.feature.map.maplibre.core.removeTrackSourcesAndLayers
import org.meshtastic.feature.map.maplibre.core.safeSetGeoJson
import org.meshtastic.feature.map.maplibre.core.setClusterVisibilityHysteresis
import org.meshtastic.feature.map.maplibre.core.setNodeLayersVisibility
import org.meshtastic.feature.map.maplibre.core.waypointsToFeatureCollectionFC
import org.meshtastic.feature.map.maplibre.utils.MapLibreTileCacheManager
import org.meshtastic.feature.map.maplibre.utils.applyFilters
import org.meshtastic.feature.map.maplibre.utils.copyFileToInternalStorage
import org.meshtastic.feature.map.maplibre.utils.deleteFileFromInternalStorage
import org.meshtastic.feature.map.maplibre.utils.distanceKmBetween
import org.meshtastic.feature.map.maplibre.utils.formatSecondsAgo
import org.meshtastic.feature.map.maplibre.utils.getFileName
import org.meshtastic.feature.map.maplibre.utils.hasAnyLocationPermission
import org.meshtastic.feature.map.maplibre.utils.loadLayerGeoJson
import org.meshtastic.feature.map.maplibre.utils.loadPersistedLayers
import org.meshtastic.feature.map.maplibre.utils.selectLabelsForViewport
import org.meshtastic.proto.ConfigProtos
import org.meshtastic.proto.MeshProtos.Waypoint
import org.meshtastic.proto.copy
import org.meshtastic.proto.waypoint
import timber.log.Timber

@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MapLibrePOC(
    mapViewModel: MapViewModel = hiltViewModel(),
    onNavigateToNodeDetails: (Int) -> Unit = {},
    focusedNodeNum: Int? = null,
    nodeTracks: List<org.meshtastic.proto.MeshProtos.Position>? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedNodeNum by remember { mutableStateOf<Int?>(null) }

    // Log track parameters on entry
    Timber.tag("MapLibrePOC")
        .d(
            "MapLibrePOC called - focusedNodeNum=%s, nodeTracks count=%d",
            focusedNodeNum ?: "null",
            nodeTracks?.size ?: 0,
        )
    val ourNode by mapViewModel.ourNodeInfo.collectAsStateWithLifecycle()
    val mapFilterState by mapViewModel.mapFilterStateFlow.collectAsStateWithLifecycle()
    var isLocationTrackingEnabled by remember { mutableStateOf(false) }
    var followBearing by remember { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    data class ExpandedCluster(val centerPx: android.graphics.PointF, val members: List<Node>)
    var expandedCluster by remember { mutableStateOf<ExpandedCluster?>(null) }
    var clusterListMembers by remember { mutableStateOf<List<Node>?>(null) }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var didInitialCenter by remember { mutableStateOf(false) }
    // Track whether we're currently showing tracks (for callback checks)
    val showingTracksRef = remember { mutableStateOf(false) }
    showingTracksRef.value = nodeTracks != null && focusedNodeNum != null
    var showLegend by remember { mutableStateOf(false) }
    var enabledRoles by remember { mutableStateOf<Set<ConfigProtos.Config.DeviceConfig.Role>>(emptySet()) }
    var clusteringEnabled by remember { mutableStateOf(true) }
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var mapTypeMenuExpanded by remember { mutableStateOf(false) }
    var showCustomTileDialog by remember { mutableStateOf(false) }
    var customTileUrl by remember { mutableStateOf("") }
    var customTileUrlInput by remember { mutableStateOf("") }
    var usingCustomTiles by remember { mutableStateOf(false) }
    // Base map style rotation
    val baseStyles = remember { enumValues<BaseMapStyle>().toList() }
    var baseStyleIndex by remember { mutableStateOf(0) }
    val baseStyle = baseStyles[baseStyleIndex % baseStyles.size]
    // Remember last applied cluster visibility to reduce flashing
    var clustersShown by remember { mutableStateOf(false) }
    var lastClusterEvalMs by remember { mutableStateOf(0L) }

    // Heatmap mode
    var heatmapEnabled by remember { mutableStateOf(false) }

    // Map layer management
    var mapLayers by remember { mutableStateOf<List<MapLayerItem>>(emptyList()) }
    var showLayersBottomSheet by remember { mutableStateOf(false) }
    var layerGeoJsonCache by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    // Tile cache management - initialize after MapLibre is initialized
    var tileCacheManager by remember { mutableStateOf<MapLibreTileCacheManager?>(null) }
    var showCacheBottomSheet by remember { mutableStateOf(false) }

    val nodes by mapViewModel.nodes.collectAsStateWithLifecycle()
    val waypoints by mapViewModel.waypoints.collectAsStateWithLifecycle()
    val isConnected by mapViewModel.isConnected.collectAsStateWithLifecycle()
    val displayableWaypoints = waypoints.values.mapNotNull { it.data.waypoint }
    val coroutineScope = rememberCoroutineScope()

    // Check location permission
    hasLocationPermission = hasAnyLocationPermission(context)

    // Load persisted map layers on startup
    LaunchedEffect(Unit) { mapLayers = loadPersistedLayers(context) }

    // Initialize tile cache manager after MapLibre is initialized
    LaunchedEffect(Unit) {
        try {
            // Ensure MapLibre is initialized first
            MapLibre.getInstance(context)
            if (tileCacheManager == null) {
                tileCacheManager = MapLibreTileCacheManager(context)
                Timber.tag("MapLibrePOC").d("Tile cache manager initialized")
            }
        } catch (e: Exception) {
            Timber.tag("MapLibrePOC").e(e, "Failed to initialize tile cache manager")
        }
    }

    // Helper functions for layer management
    fun toggleLayerVisibility(layerId: String) {
        mapLayers =
            mapLayers.map {
                if (it.id == layerId) {
                    it.copy(isVisible = !it.isVisible)
                } else {
                    it
                }
            }
    }

    fun removeLayer(layerId: String) {
        coroutineScope.launch {
            val layerToRemove = mapLayers.find { it.id == layerId }
            layerToRemove?.uri?.let { uri -> deleteFileFromInternalStorage(uri) }
            mapLayers = mapLayers.filterNot { it.id == layerId }
            layerGeoJsonCache = layerGeoJsonCache - layerId
        }
    }

    // File picker launcher for adding map layers
    val filePickerLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val fileName = uri.getFileName(context)
                    Timber.tag("MapLibrePOC").d("File picker result: uri=%s, fileName=%s", uri, fileName)
                    coroutineScope.launch {
                        val layerName = fileName?.substringBeforeLast('.') ?: "Layer ${mapLayers.size + 1}"
                        val extension =
                            fileName?.substringAfterLast('.', "")?.lowercase()
                                ?: context.contentResolver.getType(uri)?.split('/')?.last()
                        Timber.tag("MapLibrePOC").d("Layer upload: name=%s, extension=%s", layerName, extension)
                        val kmlExtensions = listOf("kml", "kmz", "vnd.google-earth.kml+xml", "vnd.google-earth.kmz")
                        val geoJsonExtensions = listOf("geojson", "json")
                        val layerType =
                            when (extension) {
                                in kmlExtensions -> LayerType.KML
                                in geoJsonExtensions -> LayerType.GEOJSON
                                else -> null
                            }
                        if (layerType != null) {
                            Timber.tag("MapLibrePOC").d("Detected layer type: %s", layerType)
                            val finalFileName = fileName ?: "layer_${java.util.UUID.randomUUID()}.$extension"
                            val localFileUri = copyFileToInternalStorage(context, uri, finalFileName)
                            if (localFileUri != null) {
                                Timber.tag("MapLibrePOC").d("File copied to internal storage: %s", localFileUri)
                                val newItem = MapLayerItem(name = layerName, uri = localFileUri, layerType = layerType)
                                mapLayers = mapLayers + newItem
                                Timber.tag("MapLibrePOC").d("Layer added to list. Total layers: %d", mapLayers.size)
                            } else {
                                Timber.tag("MapLibrePOC").e("Failed to copy file to internal storage")
                            }
                        } else {
                            Timber.tag("MapLibrePOC").w("Unsupported file type: extension=%s", extension)
                        }
                    }
                }
            } else {
                Timber.tag("MapLibrePOC").d("File picker cancelled or failed: resultCode=%d", result.resultCode)
            }
        }

    fun openFilePicker() {
        val intent =
            android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(
                    android.content.Intent.EXTRA_MIME_TYPES,
                    arrayOf(
                        "application/vnd.google-earth.kml+xml",
                        "application/vnd.google-earth.kmz",
                        "application/geo+json",
                        "application/json",
                    ),
                )
            }
        filePickerLauncher.launch(intent)
    }

    // Load and render imported map layers
    LaunchedEffect(mapLayers, mapRef) {
        mapRef?.let { map ->
            map.style?.let { style ->
                Timber.tag("MapLibrePOC")
                    .d("Layer rendering LaunchedEffect triggered. Layers count: %d", mapLayers.size)
                coroutineScope.launch {
                    // Load GeoJSON for layers that don't have it cached
                    mapLayers.forEach { layer ->
                        if (!layerGeoJsonCache.containsKey(layer.id)) {
                            Timber.tag("MapLibrePOC")
                                .d(
                                    "Loading GeoJSON for layer: id=%s, name=%s, type=%s",
                                    layer.id,
                                    layer.name,
                                    layer.layerType,
                                )
                            val geoJson = loadLayerGeoJson(context, layer)
                            if (geoJson != null) {
                                val featureCount =
                                    try {
                                        val jsonObj = org.json.JSONObject(geoJson)
                                        jsonObj.optJSONArray("features")?.length() ?: 0
                                    } catch (e: Exception) {
                                        0
                                    }
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "GeoJSON loaded for layer %s: %d features, %d bytes",
                                        layer.name,
                                        featureCount,
                                        geoJson.length,
                                    )
                                layerGeoJsonCache = layerGeoJsonCache + (layer.id to geoJson)
                            } else {
                                Timber.tag("MapLibrePOC").e("Failed to load GeoJSON for layer: %s", layer.name)
                            }
                        } else {
                            Timber.tag("MapLibrePOC").d("Using cached GeoJSON for layer: %s", layer.name)
                        }
                    }

                    // Ensure all layers are rendered
                    mapLayers.forEach { layer ->
                        val geoJson = layerGeoJsonCache[layer.id]
                        Timber.tag("MapLibrePOC")
                            .d(
                                "Rendering layer: id=%s, name=%s, visible=%s, hasGeoJson=%s",
                                layer.id,
                                layer.name,
                                layer.isVisible,
                                geoJson != null,
                            )
                        ensureImportedLayerSourceAndLayers(style, layer.id, geoJson, layer.isVisible)
                    }

                    // Remove layers that are no longer in the list
                    val currentLayerIds = mapLayers.map { it.id }.toSet()
                    val cachedLayerIds = layerGeoJsonCache.keys.toSet()
                    cachedLayerIds
                        .filter { it !in currentLayerIds }
                        .forEach { removedLayerId ->
                            removeImportedLayerSourceAndLayers(style, removedLayerId)
                            layerGeoJsonCache = layerGeoJsonCache - removedLayerId
                        }
                }
            }
        }
    }

    // Apply location tracking settings when state changes
    LaunchedEffect(isLocationTrackingEnabled, followBearing, hasLocationPermission) {
        mapRef?.let { map ->
            map.style?.let { style ->
                try {
                    if (hasLocationPermission) {
                        val locationComponent = map.locationComponent

                        // Enable/disable location component based on tracking state
                        if (isLocationTrackingEnabled) {
                            // Enable and show location component
                            if (!locationComponent.isLocationComponentEnabled) {
                                locationComponent.activateLocationComponent(
                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                        context,
                                        style,
                                    )
                                        .useDefaultLocationEngine(true)
                                        .build(),
                                )
                                locationComponent.isLocationComponentEnabled = true
                            }

                            // Set render mode
                            locationComponent.renderMode =
                                if (followBearing) {
                                    RenderMode.COMPASS
                                } else {
                                    RenderMode.NORMAL
                                }

                            // Set camera mode
                            locationComponent.cameraMode =
                                if (followBearing) {
                                    org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                } else {
                                    org.maplibre.android.location.modes.CameraMode.TRACKING
                                }
                        } else {
                            // Disable location component to hide the blue dot
                            if (locationComponent.isLocationComponentEnabled) {
                                locationComponent.isLocationComponentEnabled = false
                            }
                        }

                        Timber.tag("MapLibrePOC")
                            .d(
                                "Location component updated: enabled=%s, follow=%s",
                                isLocationTrackingEnabled,
                                followBearing,
                            )
                    }
                } catch (e: Exception) {
                    Timber.tag("MapLibrePOC").w(e, "Failed to update location component")
                }
            }
        }
    }

    // Heatmap mode management
    LaunchedEffect(
        heatmapEnabled,
        nodes,
        mapFilterState,
        enabledRoles,
        ourNode,
        isLocationTrackingEnabled,
        clusteringEnabled,
        nodeTracks,
        focusedNodeNum,
    ) {
        // Don't manage heatmap/clustering when showing tracks
        if (nodeTracks != null && focusedNodeNum != null) return@LaunchedEffect

        mapRef?.let { map ->
            map.style?.let { style ->
                if (heatmapEnabled) {
                    // Filter nodes same way as regular view
                    val filteredNodes =
                        applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled)

                    // Update heatmap source with filtered node positions
                    val heatmapFC = nodesToHeatmapFeatureCollection(filteredNodes)
                    (style.getSource(HEATMAP_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(heatmapFC)

                    // Hide node/cluster/waypoint layers
                    setNodeLayersVisibility(style, false)

                    // Show heatmap layer
                    style.getLayer(HEATMAP_LAYER_ID)?.setProperties(visibility("visible"))

                    Timber.tag("MapLibrePOC").d("Heatmap enabled: %d nodes", filteredNodes.size)
                } else {
                    // Hide heatmap layer
                    style.getLayer(HEATMAP_LAYER_ID)?.setProperties(visibility("none"))

                    // Restore proper clustering visibility based on current state
                    val filteredNodes =
                        applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled)
                    clustersShown =
                        setClusterVisibilityHysteresis(
                            map,
                            style,
                            filteredNodes,
                            clusteringEnabled,
                            clustersShown,
                            mapFilterState.showPrecisionCircle,
                        )

                    Timber.tag("MapLibrePOC")
                        .d("Heatmap disabled, clustering=%b, clustersShown=%b", clusteringEnabled, clustersShown)
                }
            }
        }
    }

    // Handle node tracks rendering when nodeTracks or focusedNodeNum changes
    LaunchedEffect(nodeTracks, focusedNodeNum, mapFilterState.lastHeardTrackFilter, styleReady) {
        if (!styleReady) {
            Timber.tag("MapLibrePOC").d("LaunchedEffect: Waiting for style to be ready")
            return@LaunchedEffect
        }

        val map =
            mapRef
                ?: run {
                    Timber.tag("MapLibrePOC").w("LaunchedEffect: mapRef is null")
                    return@LaunchedEffect
                }
        val style =
            map.style
                ?: run {
                    Timber.tag("MapLibrePOC").w("LaunchedEffect: Style not ready yet")
                    return@LaunchedEffect
                }

        map.let { map ->
            style.let { style ->
                if (nodeTracks != null && focusedNodeNum != null) {
                    Timber.tag("MapLibrePOC")
                        .d(
                            "LaunchedEffect: Rendering tracks for node %d, total positions: %d",
                            focusedNodeNum,
                            nodeTracks.size,
                        )

                    // Ensure track sources and layers exist
                    ensureTrackSourcesAndLayers(style)

                    // Get the focused node to use its color
                    val focusedNode = nodes.firstOrNull { it.num == focusedNodeNum }

                    // Apply time filter
                    val currentTimeSeconds = System.currentTimeMillis() / 1000
                    val filterSeconds = mapFilterState.lastHeardTrackFilter.seconds
                    val filteredTracks =
                        nodeTracks.filter {
                            mapFilterState.lastHeardTrackFilter == org.meshtastic.feature.map.LastHeardFilter.Any ||
                                it.time > currentTimeSeconds - filterSeconds
                        }

                    Timber.tag("MapLibrePOC")
                        .d(
                            "LaunchedEffect: Tracks filtered: %d positions remain (from %d total)",
                            filteredTracks.size,
                            nodeTracks.size,
                        )

                    // Update track line
                    if (filteredTracks.size >= 2) {
                        positionsToLineStringFeature(filteredTracks)?.let { lineFeature ->
                            (style.getSource(TRACK_LINE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(lineFeature)
                            Timber.tag("MapLibrePOC").d("LaunchedEffect: Track line updated")
                        }
                    }

                    // Update track points
                    if (filteredTracks.isNotEmpty()) {
                        val pointsFC = positionsToPointFeatures(filteredTracks)
                        (style.getSource(TRACK_POINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(pointsFC)
                        Timber.tag("MapLibrePOC").d("LaunchedEffect: Track points updated")

                        // Center camera on the tracks
                        if (filteredTracks.size == 1) {
                            // Single position - just center on it with a fixed zoom
                            val position = filteredTracks.first()
                            val latLng =
                                org.maplibre.android.geometry.LatLng(
                                    position.latitudeI * DEG_D,
                                    position.longitudeI * DEG_D,
                                )
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12.0))
                            Timber.tag("MapLibrePOC").d("LaunchedEffect: Camera centered on single track position")
                        } else {
                            // Multiple positions - fit bounds
                            val trackBounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                            filteredTracks.forEach { position ->
                                trackBounds.include(
                                    org.maplibre.android.geometry.LatLng(
                                        position.latitudeI * DEG_D,
                                        position.longitudeI * DEG_D,
                                    ),
                                )
                            }
                            val padding = 100 // pixels
                            map.animateCamera(CameraUpdateFactory.newLatLngBounds(trackBounds.build(), padding))
                            Timber.tag("MapLibrePOC")
                                .d("LaunchedEffect: Camera centered on %d track positions", filteredTracks.size)
                        }
                    }
                } else {
                    Timber.tag("MapLibrePOC").d("LaunchedEffect: No tracks to display - removing track layers")
                    removeTrackSourcesAndLayers(style)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView<MapView>(
            modifier = Modifier.fillMaxSize(),
            factory = {
                MapLibre.getInstance(context)
                Timber.tag("MapLibrePOC").d("Creating MapView + initializing MapLibre")
                MapView(context).apply {
                    mapViewRef = this
                    getMapAsync { map ->
                        mapRef = map
                        Timber.tag("MapLibrePOC").d("getMapAsync() map acquired, setting style...")
                        // Set initial base raster style using MapLibre test-app pattern
                        map.setStyle(buildMeshtasticStyle(baseStyle)) { style ->
                            Timber.tag("MapLibrePOC").d("Style loaded (base=%s)", baseStyle.label)
                            style.setTransition(TransitionOptions(0, 0))
                            styleReady = true
                            logStyleState("after-style-load(pre-ensure)", style)
                            ensureSourcesAndLayers(style)
                            ensureHeatmapSourceAndLayer(style)

                            // Setup track sources and layers if rendering node tracks
                            Timber.tag("MapLibrePOC")
                                .d(
                                    "Track check: nodeTracks=%s (%d positions), focusedNodeNum=%s",
                                    if (nodeTracks != null) "NOT NULL" else "NULL",
                                    nodeTracks?.size ?: 0,
                                    focusedNodeNum ?: "NULL",
                                )

                            if (nodeTracks != null && focusedNodeNum != null) {
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Loading tracks for node %d, total positions: %d",
                                        focusedNodeNum,
                                        nodeTracks.size,
                                    )

                                // Get the focused node to use its color
                                val focusedNode = nodes.firstOrNull { it.num == focusedNodeNum }
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Focused node found: %s (searching in %d nodes)",
                                        if (focusedNode != null) "YES" else "NO",
                                        nodes.size,
                                    )

                                val trackColor =
                                    focusedNode?.let { String.format("#%06X", 0xFFFFFF and it.colors.second) }
                                        ?: "#FF5722" // Default orange color

                                Timber.tag("MapLibrePOC").d("Track color: %s", trackColor)

                                ensureTrackSourcesAndLayers(style, trackColor)
                                Timber.tag("MapLibrePOC").d("Track sources and layers ensured")

                                // Filter tracks by time using lastHeardTrackFilter
                                val currentTimeSeconds = System.currentTimeMillis() / 1000
                                val filterSeconds = mapFilterState.lastHeardTrackFilter.seconds
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Filtering tracks - filter: %s (seconds: %d), current time: %d",
                                        mapFilterState.lastHeardTrackFilter,
                                        filterSeconds,
                                        currentTimeSeconds,
                                    )

                                val filteredTracks =
                                    nodeTracks
                                        .filter {
                                            val keep =
                                                mapFilterState.lastHeardTrackFilter ==
                                                    org.meshtastic.feature.map.LastHeardFilter.Any ||
                                                    it.time > currentTimeSeconds - filterSeconds
                                            if (!keep) {
                                                Timber.tag("MapLibrePOC")
                                                    .v(
                                                        "Filtering out position at time %d (age: %d seconds)",
                                                        it.time,
                                                        currentTimeSeconds - it.time,
                                                    )
                                            }
                                            keep
                                        }
                                        .sortedBy { it.time }

                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Tracks filtered: %d positions remain (from %d total)",
                                        filteredTracks.size,
                                        nodeTracks.size,
                                    )

                                // Update track line
                                if (filteredTracks.size >= 2) {
                                    Timber.tag("MapLibrePOC")
                                        .d("Creating line feature from %d points", filteredTracks.size)
                                    positionsToLineStringFeature(filteredTracks)?.let { lineFeature ->
                                        Timber.tag("MapLibrePOC").d("Setting line feature on source")
                                        (style.getSource(TRACK_LINE_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                            lineFeature,
                                        )
                                        Timber.tag("MapLibrePOC").d("Track line set successfully")
                                    }
                                        ?: run {
                                            Timber.tag("MapLibrePOC")
                                                .w(
                                                    "Failed to create line feature - positionsToLineStringFeature returned null",
                                                )
                                        }
                                } else {
                                    Timber.tag("MapLibrePOC")
                                        .w("Not enough points for track line (need >=2, have %d)", filteredTracks.size)
                                }

                                // Update track points
                                if (filteredTracks.isNotEmpty()) {
                                    Timber.tag("MapLibrePOC")
                                        .d("Creating point features from %d points", filteredTracks.size)
                                    val pointsFC = positionsToPointFeatures(filteredTracks)
                                    (style.getSource(TRACK_POINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(pointsFC)
                                    Timber.tag("MapLibrePOC").d("Track points set successfully")
                                } else {
                                    Timber.tag("MapLibrePOC").w("No filtered tracks to display as points")
                                }

                                Timber.tag("MapLibrePOC")
                                    .i(
                                        "✓ Track rendering complete: %d positions displayed for node %d",
                                        filteredTracks.size,
                                        focusedNodeNum,
                                    )

                                // Center camera on the tracks
                                if (filteredTracks.isNotEmpty()) {
                                    val trackBounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                                    filteredTracks.forEach { position ->
                                        trackBounds.include(
                                            org.maplibre.android.geometry.LatLng(
                                                position.latitudeI * DEG_D,
                                                position.longitudeI * DEG_D,
                                            ),
                                        )
                                    }
                                    val padding = 100 // pixels
                                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(trackBounds.build(), padding))
                                    Timber.tag("MapLibrePOC")
                                        .d("Camera centered on %d track positions", filteredTracks.size)
                                }
                            } else {
                                Timber.tag("MapLibrePOC").d("No tracks to display - removing track layers")
                                // Remove track layers if no tracks to display
                                removeTrackSourcesAndLayers(style)
                            }

                            // Push current data immediately after style load
                            try {
                                // Only set node data if we're not showing tracks
                                if (nodeTracks == null || focusedNodeNum == null) {
                                    val density = context.resources.displayMetrics.density
                                    val bounds = map.projection.visibleRegion.latLngBounds
                                    val labelSet = run {
                                        val visible =
                                            applyFilters(
                                                nodes,
                                                mapFilterState,
                                                enabledRoles,
                                                ourNode?.num,
                                                isLocationTrackingEnabled,
                                            )
                                                .filter { n ->
                                                    val p = n.validPosition ?: return@filter false
                                                    bounds.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                                }
                                        val sorted =
                                            visible.sortedWith(
                                                compareByDescending<Node> { it.isFavorite }
                                                    .thenByDescending { it.lastHeard },
                                            )
                                        val cell = (80f * density).toInt().coerceAtLeast(48)
                                        val occupied = HashSet<Long>()
                                        val chosen = LinkedHashSet<Int>()
                                        for (n in sorted) {
                                            val p = n.validPosition ?: continue
                                            val pt =
                                                map.projection.toScreenLocation(
                                                    LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                                )
                                            val cx = (pt.x / cell).toInt()
                                            val cy = (pt.y / cell).toInt()
                                            val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
                                            if (occupied.add(key)) chosen.add(n.num)
                                        }
                                        chosen
                                    }
                                    (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                                        waypointsToFeatureCollectionFC(waypoints.values),
                                    )
                                    // Set clustered source only (like MapLibre example)
                                    val filteredNodes =
                                        applyFilters(
                                            nodes,
                                            mapFilterState,
                                            enabledRoles,
                                            ourNode?.num,
                                            isLocationTrackingEnabled,
                                        )
                                    val json = nodesToFeatureCollectionJsonWithSelection(filteredNodes, labelSet)
                                    Timber.tag("MapLibrePOC")
                                        .d("Setting nodes sources: %d nodes, jsonBytes=%d", nodes.size, json.length)
                                    safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, json)
                                    safeSetGeoJson(style, NODES_SOURCE_ID, json) // Also populate non-clustered source
                                    Timber.tag("MapLibrePOC")
                                        .d(
                                            "Initial data set after style load. nodes=%d waypoints=%d",
                                            nodes.size,
                                            waypoints.size,
                                        )
                                    logStyleState("after-style-load(post-sources)", style)
                                } else {
                                    Timber.tag("MapLibrePOC").d("Skipping node data setup - showing tracks instead")
                                }
                            } catch (t: Throwable) {
                                Timber.tag("MapLibrePOC").e(t, "Failed to set initial data after style load")
                            }
                            // Keep base vector layers; OSM raster will sit below node layers for labels/roads
                            // Enable location component (if permissions granted)
                            activateLocationComponentForStyle(context, map, style)
                            Timber.tag("MapLibrePOC").d("Location component initialization attempted")
                            // Initial center on user's device location if available, else our node
                            if (!didInitialCenter) {
                                try {
                                    val loc = map.locationComponent.lastKnownLocation
                                    if (loc != null) {
                                        val target = LatLng(loc.latitude, loc.longitude)
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 10.0))
                                        didInitialCenter = true
                                    } else {
                                        ourNode?.validPosition?.let { p ->
                                            map.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D),
                                                    10.0,
                                                ),
                                            )
                                            didInitialCenter = true
                                        }
                                            ?: run {
                                                // Fallback: center to bounds of current nodes if available
                                                val filtered =
                                                    applyFilters(
                                                        nodes,
                                                        mapFilterState,
                                                        enabledRoles,
                                                        ourNode?.num,
                                                        isLocationTrackingEnabled,
                                                    )
                                                val boundsBuilder = org.maplibre.android.geometry.LatLngBounds.Builder()
                                                var any = false
                                                filtered.forEach { n ->
                                                    n.validPosition?.let { vp ->
                                                        boundsBuilder.include(
                                                            LatLng(vp.latitudeI * DEG_D, vp.longitudeI * DEG_D),
                                                        )
                                                        any = true
                                                    }
                                                }
                                                if (any) {
                                                    val b = boundsBuilder.build()
                                                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(b, 64))
                                                } else {
                                                    map.moveCamera(
                                                        CameraUpdateFactory.newLatLngZoom(LatLng(0.0, 0.0), 2.5),
                                                    )
                                                }
                                                didInitialCenter = true
                                            }
                                    }
                                } catch (_: Throwable) {}
                            }
                            map.addOnMapClickListener { latLng ->
                                // Any tap on the map clears overlays unless replaced below
                                expandedCluster = null
                                clusterListMembers = null
                                val screenPoint = map.projection.toScreenLocation(latLng)
                                // Use a small hitbox to improve taps on small circles
                                val r = (24 * context.resources.displayMetrics.density)
                                val rect =
                                    android.graphics.RectF(
                                        (screenPoint.x - r).toFloat(),
                                        (screenPoint.y - r).toFloat(),
                                        (screenPoint.x + r).toFloat(),
                                        (screenPoint.y + r).toFloat(),
                                    )
                                val features =
                                    map.queryRenderedFeatures(
                                        rect,
                                        CLUSTER_CIRCLE_LAYER_ID,
                                        NODES_LAYER_ID,
                                        NODES_LAYER_NOCLUSTER_ID,
                                        WAYPOINTS_LAYER_ID,
                                    )
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "Map click at (%.5f, %.5f) -> %d features",
                                        latLng.latitude,
                                        latLng.longitude,
                                        features.size,
                                    )
                                val f = features.firstOrNull()
                                // If cluster tapped, expand using true cluster leaves from the source
                                if (f != null && f.hasProperty("point_count")) {
                                    val pointCount = f.getNumberProperty("point_count")?.toInt() ?: 0
                                    val limit = kotlin.math.min(CLUSTER_LIST_FETCH_MAX, pointCount.toLong())
                                    val src = (map.style?.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)
                                    if (src != null) {
                                        val fc = src.getClusterLeaves(f, limit, 0L)
                                        val nums =
                                            fc.features()?.mapNotNull { feat ->
                                                try {
                                                    feat.getNumberProperty("num")?.toInt()
                                                } catch (_: Throwable) {
                                                    null
                                                }
                                            } ?: emptyList()
                                        val members = nodes.filter { nums.contains(it.num) }
                                        if (members.isNotEmpty()) {
                                            // Center camera on cluster (without zoom) to keep cluster intact
                                            val geom = f.geometry()
                                            if (geom is Point) {
                                                val clusterLat = geom.latitude()
                                                val clusterLon = geom.longitude()
                                                val clusterLatLng = LatLng(clusterLat, clusterLon)
                                                map.animateCamera(
                                                    CameraUpdateFactory.newLatLng(clusterLatLng),
                                                    300,
                                                    object : MapLibreMap.CancelableCallback {
                                                        override fun onFinish() {
                                                            // Calculate screen position AFTER camera animation
                                                            // completes
                                                            val clusterCenter =
                                                                map.projection.toScreenLocation(
                                                                    LatLng(clusterLat, clusterLon),
                                                                )

                                                            // Set overlay state after camera animation completes
                                                            if (pointCount > CLUSTER_RADIAL_MAX) {
                                                                // Show list for large clusters
                                                                clusterListMembers = members
                                                            } else {
                                                                // Show radial overlay for small clusters
                                                                expandedCluster =
                                                                    ExpandedCluster(
                                                                        clusterCenter,
                                                                        members.take(CLUSTER_RADIAL_MAX),
                                                                    )
                                                            }
                                                        }

                                                        override fun onCancel() {
                                                            // Animation was cancelled, don't show overlay
                                                        }
                                                    },
                                                )
                                                Timber.tag("MapLibrePOC")
                                                    .d(
                                                        "Centering on cluster at (%.5f, %.5f) with %d members",
                                                        clusterLat,
                                                        clusterLon,
                                                        members.size,
                                                    )
                                            } else {
                                                // No geometry, show overlay immediately using current screen position
                                                val clusterCenter =
                                                    (f.geometry() as? Point)?.let { p ->
                                                        map.projection.toScreenLocation(
                                                            LatLng(p.latitude(), p.longitude()),
                                                        )
                                                    } ?: screenPoint
                                                if (pointCount > CLUSTER_RADIAL_MAX) {
                                                    clusterListMembers = members
                                                } else {
                                                    expandedCluster =
                                                        ExpandedCluster(clusterCenter, members.take(CLUSTER_RADIAL_MAX))
                                                }
                                            }
                                        }
                                        return@addOnMapClickListener true
                                    } else {
                                        map.animateCamera(CameraUpdateFactory.zoomIn())
                                        return@addOnMapClickListener true
                                    }
                                }
                                // Handle node/waypoint selection
                                f?.let {
                                    val kind = it.getStringProperty("kind")
                                    when (kind) {
                                        "node" -> {
                                            val num = it.getNumberProperty("num")?.toInt() ?: -1
                                            selectedNodeNum = num

                                            // Center camera on selected node
                                            val geom = it.geometry()
                                            if (geom is Point) {
                                                val nodeLat = geom.latitude()
                                                val nodeLon = geom.longitude()
                                                val nodeLatLng = LatLng(nodeLat, nodeLon)
                                                map.animateCamera(CameraUpdateFactory.newLatLng(nodeLatLng), 300)
                                                Timber.tag("MapLibrePOC")
                                                    .d("Centering on node %d at (%.5f, %.5f)", num, nodeLat, nodeLon)
                                            }
                                        }
                                        "waypoint" -> {
                                            val id = it.getNumberProperty("id")?.toInt() ?: -1
                                            // Open edit dialog for waypoint
                                            waypoints.values
                                                .find { pkt -> pkt.data.waypoint?.id == id }
                                                ?.let { pkt -> editingWaypoint = pkt.data.waypoint }

                                            // Center camera on waypoint
                                            val geom = it.geometry()
                                            if (geom is Point) {
                                                val wpLat = geom.latitude()
                                                val wpLon = geom.longitude()
                                                val wpLatLng = LatLng(wpLat, wpLon)
                                                map.animateCamera(CameraUpdateFactory.newLatLng(wpLatLng), 300)
                                                Timber.tag("MapLibrePOC")
                                                    .d("Centering on waypoint %d at (%.5f, %.5f)", id, wpLat, wpLon)
                                            }
                                        }
                                        else -> {}
                                    }
                                }
                                true
                            }
                            // Long-press to create waypoint
                            map.addOnMapLongClickListener { latLng ->
                                if (isConnected) {
                                    val newWaypoint = waypoint {
                                        latitudeI = (latLng.latitude / DEG_D).toInt()
                                        longitudeI = (latLng.longitude / DEG_D).toInt()
                                    }
                                    editingWaypoint = newWaypoint
                                    Timber.tag("MapLibrePOC")
                                        .d("Long press created waypoint at ${latLng.latitude}, ${latLng.longitude}")
                                }
                                true
                            }
                            // Update clustering visibility on camera idle (zoom changes)
                            map.addOnCameraIdleListener {
                                val st = map.style ?: return@addOnCameraIdleListener
                                // Skip node updates when heatmap is enabled
                                if (heatmapEnabled) return@addOnCameraIdleListener
                                // Skip node updates when showing tracks
                                if (showingTracksRef.value) {
                                    Timber.tag("MapLibrePOC").d("onCameraIdle: Skipping node updates - showing tracks")
                                    return@addOnCameraIdleListener
                                }
                                // Debounce to avoid rapid toggling during kinetic flings/tiles loading
                                val now = SystemClock.uptimeMillis()
                                if (now - lastClusterEvalMs < 300) return@addOnCameraIdleListener
                                lastClusterEvalMs = now
                                val filtered =
                                    applyFilters(
                                        nodes,
                                        mapFilterState,
                                        enabledRoles,
                                        ourNode?.num,
                                        isLocationTrackingEnabled,
                                    )
                                Timber.tag("MapLibrePOC")
                                    .d("onCameraIdle: filtered nodes=%d (of %d)", filtered.size, nodes.size)
                                clustersShown =
                                    setClusterVisibilityHysteresis(
                                        map,
                                        st,
                                        filtered,
                                        clusteringEnabled,
                                        clustersShown,
                                        mapFilterState.showPrecisionCircle,
                                    )
                                // Compute which nodes get labels in viewport and update source
                                val density = context.resources.displayMetrics.density
                                val labelSet = selectLabelsForViewport(map, filtered, density)
                                val jsonIdle = nodesToFeatureCollectionJsonWithSelection(filtered, labelSet)
                                Timber.tag("MapLibrePOC")
                                    .d(
                                        "onCameraIdle: updating sources. labelSet=%d (nums=%s) jsonBytes=%d",
                                        labelSet.size,
                                        labelSet.take(5).joinToString(","),
                                        jsonIdle.length,
                                    )
                                // Update both clustered and non-clustered sources
                                safeSetGeoJson(st, NODES_CLUSTER_SOURCE_ID, jsonIdle)
                                safeSetGeoJson(st, NODES_SOURCE_ID, jsonIdle)
                                logStyleState("onCameraIdle(post-update)", st)
                                try {
                                    val w = mapViewRef?.width ?: 0
                                    val h = mapViewRef?.height ?: 0
                                    val bbox = android.graphics.RectF(0f, 0f, w.toFloat(), h.toFloat())
                                    val rendered =
                                        map.queryRenderedFeatures(bbox, NODES_LAYER_ID, CLUSTER_CIRCLE_LAYER_ID)
                                    Timber.tag("MapLibrePOC")
                                        .d("onCameraIdle: rendered features in viewport=%d", rendered.size)
                                } catch (_: Throwable) {}
                            }
                            // Hide expanded cluster overlay whenever camera moves to avoid stale screen positions
                            map.addOnCameraMoveListener {
                                if (expandedCluster != null || clusterListMembers != null) {
                                    expandedCluster = null
                                    clusterListMembers = null
                                }
                            }
                        }
                    }
                }
            },
            update = { mapView: MapView ->
                mapView.getMapAsync { map ->
                    val style = map.style
                    if (style == null) {
                        Timber.tag("MapLibrePOC").w("Style not yet available in update()")
                        return@getMapAsync
                    }
                    // Apply bearing render mode toggle
                    try {
                        map.locationComponent.renderMode = if (followBearing) RenderMode.COMPASS else RenderMode.NORMAL
                    } catch (_: Throwable) {
                        /* ignore */
                    }

                    // Handle location tracking state changes
                    if (isLocationTrackingEnabled && hasAnyLocationPermission(context)) {
                        try {
                            val locationComponent = map.locationComponent
                            if (!locationComponent.isLocationComponentEnabled) {
                                locationComponent.activateLocationComponent(
                                    org.maplibre.android.location.LocationComponentActivationOptions.builder(
                                        context,
                                        map.style!!,
                                    )
                                        .useDefaultLocationEngine(true)
                                        .build(),
                                )
                                locationComponent.isLocationComponentEnabled = true
                            }
                            locationComponent.renderMode = if (followBearing) RenderMode.COMPASS else RenderMode.NORMAL
                            locationComponent.cameraMode =
                                if (isLocationTrackingEnabled) {
                                    if (followBearing) {
                                        org.maplibre.android.location.modes.CameraMode.TRACKING_COMPASS
                                    } else {
                                        org.maplibre.android.location.modes.CameraMode.TRACKING
                                    }
                                } else {
                                    org.maplibre.android.location.modes.CameraMode.NONE
                                }
                            Timber.tag("MapLibrePOC")
                                .d(
                                    "Location tracking: enabled=%s, follow=%s, mode=%s",
                                    isLocationTrackingEnabled,
                                    followBearing,
                                    locationComponent.cameraMode,
                                )
                        } catch (e: Exception) {
                            Timber.tag("MapLibrePOC").w(e, "Failed to update location component")
                        }
                    }
                    // Skip node updates when heatmap is enabled
                    if (!heatmapEnabled) {
                        Timber.tag("MapLibrePOC")
                            .d("Updating sources. nodes=%d, waypoints=%d", nodes.size, waypoints.size)
                        val density = context.resources.displayMetrics.density
                        val bounds2 = map.projection.visibleRegion.latLngBounds
                        val labelSet = run {
                            val visible =
                                nodes.filter { n ->
                                    val p = n.validPosition ?: return@filter false
                                    bounds2.contains(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                }
                            val sorted =
                                visible.sortedWith(
                                    compareByDescending<Node> { it.isFavorite }.thenByDescending { it.lastHeard },
                                )
                            val cell = (80f * density).toInt().coerceAtLeast(48)
                            val occupied = HashSet<Long>()
                            val chosen = LinkedHashSet<Int>()
                            for (n in sorted) {
                                val p = n.validPosition ?: continue
                                val pt =
                                    map.projection.toScreenLocation(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D))
                                val cx = (pt.x / cell).toInt()
                                val cy = (pt.y / cell).toInt()
                                val key = (cx.toLong() shl 32) or (cy.toLong() and 0xffffffff)
                                if (occupied.add(key)) chosen.add(n.num)
                            }
                            chosen
                        }
                        (style.getSource(WAYPOINTS_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                            waypointsToFeatureCollectionFC(waypoints.values),
                        )
                        val filteredNow =
                            applyFilters(nodes, mapFilterState, enabledRoles, ourNode?.num, isLocationTrackingEnabled)
                        val jsonNow = nodesToFeatureCollectionJsonWithSelection(filteredNow, labelSet)
                        safeSetGeoJson(style, NODES_CLUSTER_SOURCE_ID, jsonNow)
                        safeSetGeoJson(style, NODES_SOURCE_ID, jsonNow) // Also populate non-clustered source
                        // Apply visibility now
                        clustersShown =
                            setClusterVisibilityHysteresis(
                                map,
                                style,
                                applyFilters(
                                    nodes,
                                    mapFilterState,
                                    enabledRoles,
                                    ourNode?.num,
                                    isLocationTrackingEnabled,
                                ),
                                clusteringEnabled,
                                clustersShown,
                                mapFilterState.showPrecisionCircle,
                            )
                    }
                    logStyleState("update(block)", style)
                }
            },
        )

        // Role legend (based on roles present in current nodes)
        if (showLegend) {
            RoleLegend(nodes = nodes, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
        }

        // Map controls: horizontal toolbar at the top (matches Google Maps style)
        MapToolbar(
            hasLocationPermission = hasLocationPermission,
            isLocationTrackingEnabled = isLocationTrackingEnabled,
            followBearing = followBearing,
            onLocationTrackingChanged = { enabled, follow ->
                isLocationTrackingEnabled = enabled
                followBearing = follow
            },
            mapFilterState = mapFilterState,
            onToggleOnlyFavorites = {
                mapViewModel.toggleOnlyFavorites()
                // Refresh both sources when filters change
                mapRef?.style?.let { st ->
                    val filtered =
                        applyFilters(
                            nodes,
                            mapFilterState.copy(onlyFavorites = !mapFilterState.onlyFavorites),
                            enabledRoles,
                        )
                    (st.getSource(NODES_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                        nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                    )
                    (st.getSource(NODES_CLUSTER_SOURCE_ID) as? GeoJsonSource)?.setGeoJson(
                        nodesToFeatureCollectionJsonWithSelection(filtered, emptySet()),
                    )
                }
            },
            onToggleShowPrecisionCircle = { mapViewModel.toggleShowPrecisionCircleOnMap() },
            nodes = nodes,
            enabledRoles = enabledRoles,
            onRoleToggled = { role ->
                enabledRoles =
                    if (enabledRoles.isEmpty()) {
                        setOf(role)
                    } else if (enabledRoles.contains(role)) {
                        enabledRoles - role
                    } else {
                        enabledRoles + role
                    }
                mapRef?.style?.let { st ->
                    mapRef?.let { map ->
                        clustersShown =
                            setClusterVisibilityHysteresis(
                                map,
                                st,
                                applyFilters(
                                    nodes,
                                    mapFilterState,
                                    enabledRoles,
                                    ourNode?.num,
                                    isLocationTrackingEnabled,
                                ),
                                clusteringEnabled,
                                clustersShown,
                                mapFilterState.showPrecisionCircle,
                            )
                    }
                }
            },
            clusteringEnabled = clusteringEnabled,
            onClusteringToggled = { enabled ->
                clusteringEnabled = enabled
                mapRef?.style?.let { st ->
                    mapRef?.let { map ->
                        clustersShown =
                            setClusterVisibilityHysteresis(
                                map,
                                st,
                                applyFilters(
                                    nodes,
                                    mapFilterState,
                                    enabledRoles,
                                    ourNode?.num,
                                    isLocationTrackingEnabled,
                                ),
                                clusteringEnabled,
                                clustersShown,
                                mapFilterState.showPrecisionCircle,
                            )
                    }
                }
            },
            baseStyles = baseStyles,
            baseStyleIndex = baseStyleIndex,
            usingCustomTiles = usingCustomTiles,
            onStyleSelected = { index ->
                baseStyleIndex = index
                usingCustomTiles = false
                val next = baseStyles[baseStyleIndex % baseStyles.size]
                mapRef?.let { map ->
                    Timber.tag("MapLibrePOC").d("Switching base style to: %s", next.label)
                    map.setStyle(buildMeshtasticStyle(next)) { st ->
                        Timber.tag("MapLibrePOC").d("Base map switched to: %s", next.label)
                        val density = context.resources.displayMetrics.density
                        clustersShown =
                            reinitializeStyleAfterSwitch(
                                context,
                                map,
                                st,
                                waypoints,
                                nodes,
                                mapFilterState,
                                enabledRoles,
                                ourNode?.num,
                                isLocationTrackingEnabled,
                                clusteringEnabled,
                                clustersShown,
                                density,
                            )
                    }
                }
            },
            customTileUrl = customTileUrl,
            onCustomTileClicked = {
                customTileUrlInput = customTileUrl
                showCustomTileDialog = true
            },
            onShowLayersClicked = { showLayersBottomSheet = true },
            onShowCacheClicked = { showCacheBottomSheet = true },
            onShowLegendToggled = { showLegend = !showLegend },
            heatmapEnabled = heatmapEnabled,
            onHeatmapToggled = { heatmapEnabled = !heatmapEnabled },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
        )

        // Zoom controls (bottom right)
        ZoomControls(
            mapRef = mapRef,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp),
        )

        // Custom tile URL dialog
        if (showCustomTileDialog) {
            CustomTileDialog(
                customTileUrlInput = customTileUrlInput,
                onCustomTileUrlInputChanged = { customTileUrlInput = it },
                onApply = {
                    customTileUrl = customTileUrlInput.trim()
                    if (customTileUrl.isNotEmpty()) {
                        usingCustomTiles = true
                        // Apply custom tiles (use first base style as template but we'll override the raster source)
                        mapRef?.let { map ->
                            Timber.tag("MapLibrePOC").d("Switching to custom tiles: %s", customTileUrl)
                            map.setStyle(buildMeshtasticStyle(baseStyles[0], customTileUrl)) { st ->
                                Timber.tag("MapLibrePOC").d("Custom tiles applied")
                                val density = context.resources.displayMetrics.density
                                clustersShown =
                                    reinitializeStyleAfterSwitch(
                                        context,
                                        map,
                                        st,
                                        waypoints,
                                        nodes,
                                        mapFilterState,
                                        enabledRoles,
                                        ourNode?.num,
                                        isLocationTrackingEnabled,
                                        clusteringEnabled,
                                        clustersShown,
                                        density,
                                    )
                            }
                        }
                    }
                    showCustomTileDialog = false
                },
                onDismiss = { showCustomTileDialog = false },
            )
        }

        // Expanded cluster radial overlay
        expandedCluster?.let { ec ->
            val d = context.resources.displayMetrics.density
            ClusterRadialOverlay(
                centerPx = ec.centerPx,
                members = ec.members,
                density = d,
                onNodeClicked = { node ->
                    selectedNodeNum = node.num
                    expandedCluster = null
                    node.validPosition?.let { p ->
                        mapRef?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D), 15.0),
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }

        // Layer management bottom sheet
        if (showLayersBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLayersBottomSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                CustomMapLayersSheet(
                    mapLayers = mapLayers,
                    onToggleVisibility = ::toggleLayerVisibility,
                    onRemoveLayer = ::removeLayer,
                    onAddLayerClicked = {
                        showLayersBottomSheet = false
                        openFilePicker()
                    },
                )
            }
        }

        // Tile cache management bottom sheet
        if (showCacheBottomSheet && tileCacheManager != null) {
            ModalBottomSheet(
                onDismissRequest = { showCacheBottomSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            ) {
                TileCacheManagementSheet(
                    cacheManager = tileCacheManager!!,
                    onDismiss = { showCacheBottomSheet = false },
                )
            }
        }

        // Bottom sheet with node details and actions
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val selectedNode = selectedNodeNum?.let { num -> nodes.firstOrNull { it.num == num } }

        // Cluster list bottom sheet (for large clusters)
        clusterListMembers?.let { members ->
            ClusterListBottomSheet(
                members = members,
                onNodeClicked = { node ->
                    selectedNodeNum = node.num
                    clusterListMembers = null
                    node.validPosition?.let { p ->
                        mapRef?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(p.latitudeI * DEG_D, p.longitudeI * DEG_D), 15.0),
                        )
                    }
                },
                onDismiss = { clusterListMembers = null },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            )
        }

        // Node details bottom sheet
        if (selectedNode != null) {
            val lastHeardAgo = formatSecondsAgo(selectedNode.lastHeard)
            val coords = selectedNode.gpsString()
            val km = ourNode?.let { me -> distanceKmBetween(me, selectedNode) }
            val distanceKm = km?.let { "%.1f".format(it) }

            NodeDetailsBottomSheet(
                node = selectedNode,
                lastHeardAgo = lastHeardAgo,
                coords = coords,
                distanceKm = distanceKm,
                onViewFullNode = {
                    onNavigateToNodeDetails(selectedNode.num)
                    selectedNodeNum = null
                },
                onDismiss = { selectedNodeNum = null },
                sheetState = sheetState,
            )
        }
        // Waypoint editing dialog
        editingWaypoint?.let { waypointToEdit ->
            EditWaypointDialog(
                waypoint = waypointToEdit,
                onSendClicked = { updatedWp ->
                    var finalWp = updatedWp
                    if (updatedWp.id == 0) {
                        finalWp = finalWp.copy { id = mapViewModel.generatePacketId() ?: 0 }
                    }
                    if (updatedWp.icon == 0) {
                        finalWp = finalWp.copy { icon = 0x1F4CD }
                    }
                    mapViewModel.sendWaypoint(finalWp)
                    editingWaypoint = null
                },
                onDeleteClicked = { wpToDelete ->
                    if (wpToDelete.lockedTo == 0 && isConnected && wpToDelete.id != 0) {
                        val deleteMarkerWp = wpToDelete.copy { expire = 1 }
                        mapViewModel.sendWaypoint(deleteMarkerWp)
                    }
                    mapViewModel.deleteWaypoint(wpToDelete.id)
                    editingWaypoint = null
                },
                onDismissRequest = { editingWaypoint = null },
            )
        }
    }

    // Forward lifecycle events to MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Note: AndroidView handles View lifecycle, but MapView benefits from explicit forwarding
            when (event) {
                Lifecycle.Event.ON_START -> {}
                Lifecycle.Event.ON_RESUME -> {}
                Lifecycle.Event.ON_PAUSE -> {}
                Lifecycle.Event.ON_STOP -> {}
                Lifecycle.Event.ON_DESTROY -> {}
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
