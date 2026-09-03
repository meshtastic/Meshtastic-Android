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
package org.meshtastic.app.map

import android.app.Application
import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.TileProvider
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.app.map.offline.pmtiles.OfflineDownloadState
import org.meshtastic.app.map.offline.pmtiles.OfflineRegion
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionExtractor
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionStore
import org.meshtastic.app.map.offline.pmtiles.OfflineRegionTileSet
import org.meshtastic.app.map.offline.terrain.TerrainDownloadPlanner
import org.meshtastic.app.map.prefs.map.GoogleCameraPosition
import org.meshtastic.app.map.prefs.map.GoogleMapSelectionPrefs
import org.meshtastic.app.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.app.map.tiles.MapTileHttpClient
import org.meshtastic.app.map.tiles.RasterBasemap
import org.meshtastic.app.map.tiles.RasterTileProvider
import org.meshtastic.app.map.tiles.toRasterBasemap
import org.meshtastic.core.common.util.LocaleUnitsProvider
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.MapTileProviderPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationPrefs
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.feature.map.layers.LayerOpacityStore
import org.meshtastic.feature.map.layers.MapLayerItem
import org.meshtastic.feature.map.layers.MapLayersManager
import org.meshtastic.feature.map.layers.PickedMapFile
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainDownloadState
import org.meshtastic.feature.map.terrain.TerrainRegionExtractor
import org.meshtastic.feature.map.terrain.TerrainTileStore
import org.meshtastic.feature.map.tiles.CustomTileProviderConfig
import org.meshtastic.feature.map.tiles.CustomTileProviderRepository
import org.meshtastic.feature.map.tiles.CustomTileProviderSaveResult
import org.meshtastic.feature.map.tiles.MapTileCatalogue
import org.meshtastic.feature.map.tiles.RasterOverlaySource
import org.meshtastic.feature.map.tiles.RasterTileSpec
import org.meshtastic.feature.map.tiles.isValidTileUrlTemplate
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.uuid.Uuid

/** Waypoint coordinates arrive as integer degrees scaled by 1e7. */
private const val WAYPOINT_COORD_SCALE = 1e7

/** Camera zoom applied when centering on a selected waypoint. */
private const val WAYPOINT_FOCUS_ZOOM = 15f

enum class CameraInitialization {
    Loading,
    FitNodes,
    Restored,
}

@Suppress("TooManyFunctions", "LongParameterList")
@KoinViewModel
class MapViewModel(
    private val application: Application,
    private val dispatchers: CoroutineDispatchers,
    private val mapLayersManager: MapLayersManager,
    private val layerOpacityStore: LayerOpacityStore,
    mapPrefs: MapPrefs,
    private val googleMapsPrefs: GoogleMapsPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    radioController: RadioController,
    private val customTileProviderRepository: CustomTileProviderRepository,
    private val mapTileProviderPrefs: MapTileProviderPrefs,
    private val mapTileHttpClient: MapTileHttpClient,
    uiPrefs: UiPrefs,
    notificationPrefs: NotificationPrefs,
    savedStateHandle: SavedStateHandle,
    localeUnitsProvider: LocaleUnitsProvider,
) : BaseMapViewModel(
    mapPrefs,
    nodeRepository,
    packetRepository,
    radioController,
    radioConfigRepository,
    notificationPrefs,
    localeUnitsProvider,
) {

    private val _selectedWaypointId = MutableStateFlow(savedStateHandle.get<Int>("waypointId"))
    val selectedWaypointId: StateFlow<Int?> = _selectedWaypointId.asStateFlow()

    // Injected by the map provider because this SavedStateHandle is not the Navigation 3 entry's route state.
    private val sitePlannerRequestState = SitePlannerRequestState(nodeRepository.nodeDBbyNum)
    val sitePlannerRequest: StateFlow<Node?> =
        sitePlannerRequestState.request.stateInWhileSubscribed(initialValue = null)

    fun setSitePlannerNodeNum(nodeNum: Int?) {
        sitePlannerRequestState.setNodeNum(nodeNum)
    }

    fun consumeSitePlannerRequest(nodeNum: Int) {
        sitePlannerRequestState.consume(nodeNum)
    }

    fun setWaypointId(id: Int?) {
        if (_selectedWaypointId.value != id) {
            _selectedWaypointId.value = id
            if (id != null) {
                viewModelScope.launch {
                    val wpMap = waypoints.first { it.containsKey(id) }
                    wpMap[id]?.let { packet ->
                        val waypoint = packet.waypoint!!
                        val latLng =
                            LatLng(
                                (waypoint.latitude_i ?: 0) / WAYPOINT_COORD_SCALE,
                                (waypoint.longitude_i ?: 0) / WAYPOINT_COORD_SCALE,
                            )
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, WAYPOINT_FOCUS_ZOOM)
                    }
                }
            }
        }
    }

    val cameraPositionState = CameraPositionState(CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 7f))

    private val _cameraInitialization = MutableStateFlow(CameraInitialization.Loading)
    val cameraInitialization: StateFlow<CameraInitialization> = _cameraInitialization.asStateFlow()

    val theme: StateFlow<Int> = uiPrefs.theme

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: Flow<String> = _errorFlow.asFlow()

    val customTileProviderConfigs: StateFlow<List<CustomTileProviderConfig>> =
        customTileProviderRepository.getCustomTileProviders().stateInWhileSubscribed(initialValue = emptyList())

    private val _selectedRasterBasemapId = MutableStateFlow<String?>(null)

    /** The chosen raster basemap: a [MapTileCatalogue] source, or one of the user's own. Null means Google's own. */
    val selectedRasterBasemapId: StateFlow<String?> = _selectedRasterBasemapId.asStateFlow()

    val selectedRasterBasemap: StateFlow<RasterBasemap?> =
        combine(_selectedRasterBasemapId, customTileProviderConfigs) { selectedId, providers ->
            selectedId?.let { id ->
                MapTileCatalogue.basemaps.firstOrNull { it.id == id }?.let { RasterBasemap.Remote(id, it.spec) }
                    ?: providers.findSelectedCustomTileProvider(id)?.toRasterBasemap()
            }
        }
            .stateInWhileSubscribed(initialValue = null)

    /**
     * The overlays this map can composite over its basemap.
     *
     * DEM sources are dropped: their pixels encode elevation for a renderer that shades them into terrain, and drawing
     * the raw values over the map would just be noise. The MapLibre map, which does shade them, offers the full set.
     */
    val availableOverlays: List<RasterOverlaySource> = MapTileCatalogue.overlays.filter { it.demEncoding == null }

    private val _enabledOverlayIds = MutableStateFlow<Set<String>>(emptySet())
    val enabledOverlayIds: StateFlow<Set<String>> = _enabledOverlayIds.asStateFlow()

    /** Per-layer opacity, shared with the MapLibre map through [LayerOpacityStore]. */
    val layerOpacity: StateFlow<Map<String, Float>> = layerOpacityStore.opacity

    fun setLayerOpacity(key: String, opacity: Float) = layerOpacityStore.setOpacity(key, opacity)

    fun toggleOverlay(overlayId: String) {
        _enabledOverlayIds.update { enabled -> if (overlayId in enabled) enabled - overlayId else enabled + overlayId }
    }

    /**
     * A tile provider for an overlay. The basemap goes through [getTileProvider], which keeps the archive it opened.
     */
    fun createTileProvider(spec: RasterTileSpec): TileProvider = RasterTileProvider(spec, mapTileHttpClient.client)

    private val _selectedGoogleMapType = MutableStateFlow(MapType.NORMAL)
    val selectedGoogleMapType: StateFlow<MapType> = _selectedGoogleMapType.asStateFlow()

    fun addCustomTileProvider(name: String, urlTemplate: String, localUri: String? = null) {
        viewModelScope.launch {
            if (
                name.isBlank() ||
                (urlTemplate.isBlank() && localUri == null) ||
                (localUri == null && !isValidTileUrlTemplate(urlTemplate))
            ) {
                _errorFlow.emit("Invalid name, URL template, or local URI for custom tile provider.")
                return@launch
            }
            var finalLocalUri = localUri
            if (localUri != null) {
                try {
                    val uri = Uri.parse(localUri)
                    val extension = "mbtiles"
                    val finalFileName = "mbtiles_${Uuid.random()}.$extension"
                    val copiedUri = copyFileToInternalStorage(uri, finalFileName)
                    if (copiedUri != null) {
                        finalLocalUri = copiedUri.toString()
                    } else {
                        _errorFlow.emit("Failed to copy MBTiles file to internal storage.")
                        return@launch
                    }
                } catch (e: CancellationException) {
                    throw e // A cancelled copy (ViewModel cleared) is not a processing failure.
                } catch (e: Exception) {
                    Logger.withTag("MapViewModel").e(e) { "Error processing local URI" }
                    _errorFlow.emit("Error processing local URI for MBTiles.")
                    return@launch
                }
            }

            val newConfig = CustomTileProviderConfig(name = name, urlTemplate = urlTemplate, localUri = finalLocalUri)
            when (customTileProviderRepository.addCustomTileProvider(newConfig)) {
                CustomTileProviderSaveResult.SAVED -> Unit

                CustomTileProviderSaveResult.DUPLICATE_NAME -> {
                    finalLocalUri?.let { deleteFileToInternalStorage(Uri.parse(it)) }
                    _errorFlow.emit("Custom tile provider with name '$name' already exists.")
                }

                CustomTileProviderSaveResult.NOT_FOUND -> _errorFlow.emit("Failed to save custom tile provider.")
            }
        }
    }

    fun addCustomTileProvider(config: CustomTileProviderConfig) {
        viewModelScope.launch {
            val normalized = config.normalized()
            if (normalized.name.isBlank() || !normalized.hasValidGoogleTileSource()) {
                _errorFlow.emit("Invalid custom tile provider configuration.")
                return@launch
            }
            when (customTileProviderRepository.addCustomTileProvider(normalized)) {
                CustomTileProviderSaveResult.SAVED -> Unit

                CustomTileProviderSaveResult.DUPLICATE_NAME ->
                    _errorFlow.emit("Custom tile provider with that name already exists.")

                CustomTileProviderSaveResult.NOT_FOUND -> _errorFlow.emit("Failed to save custom tile provider.")
            }
        }
    }

    fun updateCustomTileProvider(configToUpdate: CustomTileProviderConfig) {
        viewModelScope.launch {
            val normalized = configToUpdate.normalized()
            if (
                normalized.name.isBlank() ||
                (normalized.urlTemplate.isBlank() && normalized.localUri == null) ||
                (normalized.localUri == null && !isValidTileUrlTemplate(normalized.urlTemplate))
            ) {
                _errorFlow.emit("Invalid name, URL template, or local URI for updating custom tile provider.")
                return@launch
            }
            when (customTileProviderRepository.updateCustomTileProvider(normalized)) {
                CustomTileProviderSaveResult.SAVED -> Unit

                CustomTileProviderSaveResult.DUPLICATE_NAME ->
                    _errorFlow.emit("Another custom tile provider with name '${normalized.name}' already exists.")

                CustomTileProviderSaveResult.NOT_FOUND -> _errorFlow.emit("Custom tile provider no longer exists.")
            }
        }
    }

    fun removeCustomTileProvider(configId: String) {
        viewModelScope.launch {
            val configToRemove = customTileProviderRepository.getCustomTileProviderById(configId)
            val wasSelected = _selectedRasterBasemapId.value == configId
            customTileProviderRepository.deleteCustomTileProvider(configId)

            if (configToRemove != null) {
                if (wasSelected) {
                    clearCurrentTileProvider()
                    _selectedRasterBasemapId.value = null
                    _selectedGoogleMapType.value = MapType.NORMAL
                    mapTileProviderPrefs.setSelectedCustomTileProviderId(null)
                    googleMapsPrefs.setSelectedCustomTileUrl(null)
                    googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
                }

                if (configToRemove.localUri != null) {
                    val uri = Uri.parse(configToRemove.localUri)
                    deleteFileToInternalStorage(uri)
                }
            }
        }
    }

    fun selectCustomTileProvider(config: CustomTileProviderConfig?) {
        if (config != null) {
            if (!config.isLocal && !isValidTileUrlTemplate(config.urlTemplate)) {
                Logger.withTag("MapViewModel").w("Attempted to select an invalid custom tile URL template")
                clearCurrentTileProvider()
                _selectedRasterBasemapId.value = null
                _selectedGoogleMapType.value = MapType.NORMAL
                viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
                googleMapsPrefs.setSelectedCustomTileUrl(null)
                googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
                return
            }
            applyRasterBasemapSelection(config.id)
        } else {
            clearCurrentTileProvider()
            _selectedRasterBasemapId.value = null
            _selectedGoogleMapType.value = MapType.NORMAL
            viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
            googleMapsPrefs.setSelectedCustomTileUrl(null)
            googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
        }
    }

    /** Selects one of the raster basemaps we ship. They draw exactly as a user's own source does. */
    fun selectCatalogueBasemap(basemapId: String) {
        applyRasterBasemapSelection(basemapId)
    }

    /**
     * Points the map at a raster source and turns Google's own basemap off.
     *
     * `MapType.NONE` matters: leaving it on would have Google fetch and draw a basemap underneath tiles that are
     * already opaque — paid quota spent on pixels nobody sees.
     */
    private fun applyRasterBasemapSelection(basemapId: String) {
        _selectedRasterBasemapId.value = basemapId
        _selectedGoogleMapType.value = MapType.NONE
        viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(basemapId) }
        googleMapsPrefs.setSelectedCustomTileUrl(null)
        googleMapsPrefs.setSelectedGoogleMapType(null)
    }

    fun setSelectedGoogleMapType(mapType: MapType) {
        clearCurrentTileProvider()
        _selectedGoogleMapType.value = mapType
        _selectedRasterBasemapId.value = null
        viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
        googleMapsPrefs.setSelectedGoogleMapType(mapType.name)
        googleMapsPrefs.setSelectedCustomTileUrl(null)
    }

    private var currentTileProvider: TileProvider? = null
    private var currentTileProviderBasemap: RasterBasemap? = null

    /**
     * The tile provider drawing [basemap], reused while the selection holds.
     *
     * Kept rather than rebuilt because a local archive owns an open database handle, and because rebuilding a remote
     * provider would drop the tiles it has already fetched.
     */
    fun getTileProvider(basemap: RasterBasemap?): TileProvider? {
        if (basemap == null) {
            clearCurrentTileProvider()
            return null
        }

        if (currentTileProvider != null && currentTileProviderBasemap == basemap) {
            return currentTileProvider
        }

        clearCurrentTileProvider()

        val newProvider =
            when (basemap) {
                is RasterBasemap.Local -> openLocalArchive(basemap.uri)
                is RasterBasemap.Remote -> RasterTileProvider(basemap.spec, mapTileHttpClient.client)
            }

        currentTileProvider = newProvider
        currentTileProviderBasemap = basemap.takeIf { newProvider != null }
        return newProvider
    }

    private fun openLocalArchive(localUri: String): MBTilesProvider? {
        val uri = Uri.parse(localUri)
        val file =
            try {
                uri.toFile()
            } catch (e: IllegalArgumentException) {
                Logger.withTag("MapViewModel").w(e) { "Falling back to the raw path for a local MBTiles archive" }
                File(uri.path ?: "")
            }
        return if (file.exists()) {
            MBTilesProvider(file)
        } else {
            Logger.withTag("MapViewModel").w("Selected local MBTiles file does not exist")
            null
        }
    }

    private fun isValidTileUrlTemplate(urlTemplate: String): Boolean =
        urlTemplate.isValidTileUrlTemplate(requireHttps = false)

    private fun clearCurrentTileProvider() {
        (currentTileProvider as? MBTilesProvider)?.close()
        currentTileProvider = null
        currentTileProviderBasemap = null
    }

    /** Imported overlay layers; owned by the flavor-neutral [MapLayersManager] and rendered by [MapLayerOverlay]. */
    val mapLayers: StateFlow<List<MapLayerItem>> = mapLayersManager.mapLayers

    // --- Offline vector regions (PMTiles-extracted) ---

    // Offline properties exposed as internal StateFlow for composable consumption
    internal val offlineRegionStore = OfflineRegionStore(File(application.filesDir, "offline_regions"))

    // A single shared instance, not one per download call: OfflineRegionExtractor's mutex only serializes concurrent
    // downloads against each other if they all go through the same instance.
    private val offlineRegionExtractor = OfflineRegionExtractor(offlineRegionStore)

    private val _offlineRegions = MutableStateFlow(offlineRegionStore.list())
    val offlineRegions: StateFlow<List<OfflineRegion>> = _offlineRegions.asStateFlow()

    private val _offlineDownloadState = MutableStateFlow<OfflineDownloadState?>(null)
    val offlineDownloadState: StateFlow<OfflineDownloadState?> = _offlineDownloadState.asStateFlow()

    /**
     * Shown on the map whenever a downloaded region covers the current viewport — manual, not tied to connectivity.
     * Wiring this to `mapNetworkAvailable` (from the sibling offline-fallback change) so it activates itself the
     * instant the network drops is the natural next step once both land; kept manual here so this change doesn't depend
     * on that one merging first.
     */
    private val _offlineOverlayEnabled = MutableStateFlow(false)
    val offlineOverlayEnabled: StateFlow<Boolean> = _offlineOverlayEnabled.asStateFlow()

    fun setOfflineOverlayEnabled(enabled: Boolean) {
        _offlineOverlayEnabled.value = enabled
    }

    fun estimateOfflineTileCount(bounds: LatLngBounds, zoomRange: IntRange): Long =
        OfflineRegionTileSet.estimateTileCount(bounds, zoomRange)

    fun downloadOfflineRegion(bounds: LatLngBounds, zoomRange: IntRange) {
        viewModelScope.launch {
            offlineRegionExtractor.download(bounds, zoomRange).collect { state ->
                _offlineDownloadState.value = state
                if (state is OfflineDownloadState.Complete) _offlineRegions.value = offlineRegionStore.list()
            }
        }
    }

    fun clearOfflineDownloadState() {
        _offlineDownloadState.value = null
    }

    fun deleteOfflineRegion(id: String) {
        viewModelScope.launch {
            if (_terrainDownloadRegionId.value == id) {
                // Joined, not just cancelled: the terrain job's Complete handler would otherwise re-add this region
                // to the manifest with no archive behind it, and its tile writes would race the directory delete.
                terrainDownloadJob?.cancelAndJoin()
                clearTerrainDownloadState()
            }
            offlineRegionStore.delete(id)
            _offlineRegions.value = offlineRegionStore.list()
        }
    }

    fun offlineRegionArchiveFile(id: String): File = offlineRegionStore.archiveFile(id)

    /** The downloaded region, if any, whose bounds fully cover [bounds] — the current camera viewport. */
    fun offlineRegionCovering(bounds: LatLngBounds): OfflineRegion? = _offlineRegions.value.firstOrNull {
        it.bounds.contains(bounds.northeast) && it.bounds.contains(bounds.southwest)
    }

    // --- Offline terrain (hillshade + contours), attached to an already-downloaded base region ---

    private val _terrainDownloadState = MutableStateFlow<TerrainDownloadState?>(null)
    val terrainDownloadState: StateFlow<TerrainDownloadState?> = _terrainDownloadState.asStateFlow()

    /** Which region [terrainDownloadState] belongs to — terrain, unlike the base layer, downloads per-row. */
    private val _terrainDownloadRegionId = MutableStateFlow<String?>(null)
    val terrainDownloadRegionId: StateFlow<String?> = _terrainDownloadRegionId.asStateFlow()

    /**
     * Manual toggles, same shape as [offlineOverlayEnabled] — independent of it, gated by [OfflineRegion.hasTerrain].
     */
    private val _terrainHillshadeEnabled = MutableStateFlow(false)
    val terrainHillshadeEnabled: StateFlow<Boolean> = _terrainHillshadeEnabled.asStateFlow()

    private val _terrainContoursEnabled = MutableStateFlow(false)
    val terrainContoursEnabled: StateFlow<Boolean> = _terrainContoursEnabled.asStateFlow()

    fun setTerrainHillshadeEnabled(enabled: Boolean) {
        _terrainHillshadeEnabled.value = enabled
    }

    fun setTerrainContoursEnabled(enabled: Boolean) {
        _terrainContoursEnabled.value = enabled
    }

    fun clearTerrainDownloadState() {
        _terrainDownloadState.value = null
        _terrainDownloadRegionId.value = null
    }

    /** A [TerrainTileStore] rooted at this region's own terrain subdirectory of [offlineRegionStore]'s base dir. */
    fun terrainStoreForRegion(id: String): TerrainTileStore =
        TerrainTileStore(FileSystem.SYSTEM, offlineRegionStore.terrainDir(id).absolutePath.toPath())

    private var terrainDownloadJob: Job? = null

    /**
     * Downloads terrain for an already-downloaded base region. No-ops (rather than starting a download that would only
     * fail) unless [canDownloadTerrainForRegion] — the UI already keeps its "Download Terrain" affordance disabled in
     * that case; this is the defensive re-check, same relationship [downloadOfflineRegion] has to its own button's
     * `enabled` condition. One terrain job at a time: a second would race the single [terrainDownloadState] and both
     * would pass the same one-shot storage check.
     */
    fun downloadTerrainForRegion(regionId: String) {
        val region = _offlineRegions.value.firstOrNull { it.id == regionId }
        if (region == null || terrainDownloadJob?.isActive == true || !canDownloadTerrainForRegion(regionId)) return

        _terrainDownloadRegionId.value = regionId
        _terrainDownloadState.value = null
        terrainDownloadJob =
            viewModelScope.launch {
                val store = terrainStoreForRegion(regionId)
                val bounds =
                    GeoBounds(
                        south = region.southLat,
                        west = region.westLon,
                        north = region.northLat,
                        east = region.eastLon,
                    )
                val maxZoom = TerrainDownloadPlanner.maxZoomFitting(bounds, TerrainRegionExtractor.MAX_TILES)
                // flowOn: the extractor does blocking per-tile HTTP on its collector's dispatcher.
                TerrainRegionExtractor(store).download(bounds, maxZoom).flowOn(dispatchers.io).collect { state ->
                    _terrainDownloadState.value = state
                    if (state is TerrainDownloadState.Complete) attachTerrain(region, state, store)
                }
            }
    }

    private suspend fun attachTerrain(
        region: OfflineRegion,
        state: TerrainDownloadState.Complete,
        store: TerrainTileStore,
    ) {
        // deleteOfflineRegion joins this job first, but the manifest is the authority on whether the region survived.
        if (offlineRegionStore.list().none { it.id == region.id }) {
            store.deleteAll()
            return
        }
        offlineRegionStore.add(
            region.copy(
                hasTerrain = true,
                terrainByteSize = state.byteSize,
                terrainHasRegionalDetail = state.hasRegionalDetail,
            ),
        )
        _offlineRegions.value = offlineRegionStore.list()
    }

    /**
     * Whether [regionId] is eligible for a terrain download: doesn't have one yet, and the shared storage budget
     * ([OfflineRegionExtractor.MAX_TOTAL_BYTES], covering every region's base archive plus any terrain attached to it —
     * [OfflineRegionStore.totalBytes]) isn't already exhausted.
     */
    private fun canDownloadTerrainForRegion(regionId: String): Boolean {
        val region = _offlineRegions.value.firstOrNull { it.id == regionId } ?: return false
        return !region.hasTerrain && offlineRegionStore.totalBytes() < OfflineRegionExtractor.MAX_TOTAL_BYTES
    }

    init {
        viewModelScope.launch {
            val savedCamera = googleMapsPrefs.cameraPosition.first()
            if (savedCamera == null) {
                _cameraInitialization.value = CameraInitialization.FitNodes
            } else {
                cameraPositionState.position = savedCamera.toCameraPosition()
                _cameraInitialization.value = CameraInitialization.Restored
            }
        }

        viewModelScope.launch {
            val providerLoad = customTileProviderRepository.awaitCustomTileProviders()
            loadPersistedMapType(
                providers = providerLoad.providers,
                providerLoadSuccessful = providerLoad.isSuccessful,
                selection = googleMapsPrefs.awaitMapSelection(),
                selectedProviderId = mapTileProviderPrefs.awaitSelectedCustomTileProviderId(),
            )
        }

        selectedWaypointId.value?.let { wpId ->
            viewModelScope.launch {
                val wpMap = waypoints.first { it.containsKey(wpId) }
                wpMap[wpId]?.let { packet ->
                    val waypoint = packet.waypoint!!
                    val latLng =
                        LatLng(
                            (waypoint.latitude_i ?: 0) / WAYPOINT_COORD_SCALE,
                            (waypoint.longitude_i ?: 0) / WAYPOINT_COORD_SCALE,
                        )
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, WAYPOINT_FOCUS_ZOOM)
                }
            }
        }
    }

    fun saveCameraPosition(cameraPosition: CameraPosition) {
        if (_cameraInitialization.value != CameraInitialization.Restored) return
        googleMapsPrefs.setCameraPosition(cameraPosition.toMapCameraPosition())
    }

    fun onInitialNodeBoundsApplied() {
        _cameraInitialization.value = CameraInitialization.Restored
        saveCameraPosition(cameraPositionState.position)
    }

    private suspend fun loadPersistedMapType(
        providers: List<CustomTileProviderConfig>,
        providerLoadSuccessful: Boolean,
        selection: GoogleMapSelectionPrefs,
        selectedProviderId: String?,
    ) {
        // A catalogue source is not in the user's list, so it has to be recognised before that lookup decides the
        // stored id points at a source that no longer exists.
        if (MapTileCatalogue.basemaps.any { it.id == selectedProviderId }) {
            _selectedRasterBasemapId.value = selectedProviderId
            _selectedGoogleMapType.value = MapType.NONE
            return
        }

        val resolvedSelection =
            providers.resolvePersistedCustomTileSelection(
                selectedProviderId = selectedProviderId,
                legacySource = selection.customTileUrl,
                providerLoadSuccessful = providerLoadSuccessful,
            )
        val selectedProvider = resolvedSelection.provider

        if (selectedProvider != null) {
            _selectedRasterBasemapId.value = selectedProvider.id
            _selectedGoogleMapType.value = MapType.NONE
            if (selectedProviderId != selectedProvider.id) {
                mapTileProviderPrefs.setSelectedCustomTileProviderId(selectedProvider.id)
            }
            if (selection.customTileUrl != null) googleMapsPrefs.setSelectedCustomTileUrl(null)
        } else {
            _selectedRasterBasemapId.value = null
            if (resolvedSelection.canDiscardMissingSelection) {
                if (selectedProviderId != null) mapTileProviderPrefs.setSelectedCustomTileProviderId(null)
                if (selection.customTileUrl != null) googleMapsPrefs.setSelectedCustomTileUrl(null)
            }
            if (!providerLoadSuccessful && (selectedProviderId != null || selection.customTileUrl != null)) {
                _selectedGoogleMapType.value = MapType.NORMAL
                return
            }
            try {
                _selectedGoogleMapType.value = MapType.valueOf(selection.mapType)
            } catch (_: IllegalArgumentException) {
                Logger.w { "Ignoring an invalid saved Google Map type" }
                _selectedGoogleMapType.value = MapType.NORMAL
                googleMapsPrefs.setSelectedGoogleMapType(null)
            }
        }
    }

    fun addMapLayer(picked: PickedMapFile) = mapLayersManager.addMapLayer(picked)

    fun addNetworkMapLayer(name: String, url: String) {
        mapLayersManager.addNetworkMapLayer(name, url)?.let { error ->
            viewModelScope.launch { _errorFlow.emit(error) }
        }
    }

    private suspend fun copyFileToInternalStorage(uri: Uri, fileName: String): Uri? = withContext(dispatchers.io) {
        try {
            val inputStream = application.contentResolver.openInputStream(uri)
            val directory = File(application.filesDir, "map_layers")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            val outputFile = File(directory, fileName)
            val outputStream = FileOutputStream(outputFile)

            inputStream?.use { input -> outputStream.use { output -> input.copyTo(output) } }
            Uri.fromFile(outputFile)
        } catch (e: IOException) {
            Logger.withTag("MapViewModel").e(e) { "Error copying file to internal storage" }
            null
        }
    }

    fun addGeoJsonLayer(name: String, geoJson: String) = mapLayersManager.addGeoJsonLayer(name, geoJson)

    fun toggleLayerVisibility(layerId: String) = mapLayersManager.toggleLayerVisibility(layerId)

    fun removeMapLayer(layerId: String) = mapLayersManager.removeMapLayer(layerId)

    fun refreshMapLayer(layerId: String) = mapLayersManager.refreshMapLayer(layerId)

    fun refreshAllVisibleNetworkLayers() = mapLayersManager.refreshAllVisibleNetworkLayers()

    private suspend fun deleteFileToInternalStorage(uri: Uri) {
        withContext(dispatchers.io) {
            try {
                val file = uri.toFile()
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Logger.withTag("MapViewModel").e(e) { "Error deleting file from internal storage" }
            }
        }
    }

    suspend fun readLayerBytes(layerItem: MapLayerItem): ByteArray? = mapLayersManager.readLayerBytes(layerItem)

    override fun onCleared() {
        super.onCleared()
        (currentTileProvider as? MBTilesProvider)?.close()
    }

    override fun getUser(userId: String?) = nodeRepository.getUser(userId ?: NodeAddress.ID_BROADCAST)
}

internal fun List<CustomTileProviderConfig>.findSelectedCustomTileProvider(
    selectedProviderId: String?,
): CustomTileProviderConfig? = singleOrNull { it.id == selectedProviderId }

internal fun List<CustomTileProviderConfig>.findLegacyCustomTileProvider(
    legacySource: String?,
): CustomTileProviderConfig? = legacySource?.let { source -> firstOrNull { (it.localUri ?: it.urlTemplate) == source } }

internal data class PersistedCustomTileSelection(
    val provider: CustomTileProviderConfig?,
    val canDiscardMissingSelection: Boolean,
)

internal fun List<CustomTileProviderConfig>.resolvePersistedCustomTileSelection(
    selectedProviderId: String?,
    legacySource: String?,
    providerLoadSuccessful: Boolean,
): PersistedCustomTileSelection {
    val provider =
        listOfNotNull(findSelectedCustomTileProvider(selectedProviderId), findLegacyCustomTileProvider(legacySource))
            .firstOrNull { it.hasValidGoogleTileSource() }
    return PersistedCustomTileSelection(
        provider = provider,
        canDiscardMissingSelection = provider == null && providerLoadSuccessful,
    )
}

internal fun CustomTileProviderConfig.hasValidGoogleTileSource(): Boolean =
    isLocal || urlTemplate.isValidTileUrlTemplate(requireHttps = false)

private fun GoogleCameraPosition.toCameraPosition() = CameraPosition(LatLng(targetLat, targetLng), zoom, tilt, bearing)

private fun CameraPosition.toMapCameraPosition() = GoogleCameraPosition(
    targetLat = target.latitude,
    targetLng = target.longitude,
    zoom = zoom,
    tilt = tilt,
    bearing = bearing,
)
