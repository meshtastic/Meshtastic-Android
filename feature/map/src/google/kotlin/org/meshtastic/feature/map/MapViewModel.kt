/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.map

import android.app.Application
import android.net.Uri
import androidx.core.net.toFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileProvider
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapType
import com.google.maps.android.data.geojson.GeoJsonLayer
import com.google.maps.android.data.kml.KmlLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import org.meshtastic.core.data.model.CustomTileProviderConfig
import org.meshtastic.core.data.repository.CustomTileProviderRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.datastore.UiPreferencesDataSource
import org.meshtastic.core.navigation.MapRoutes
import org.meshtastic.core.prefs.map.GoogleMapsPrefs
import org.meshtastic.core.prefs.map.MapPrefs
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.proto.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import java.util.UUID
import javax.inject.Inject

private const val TILE_SIZE = 256

@Serializable
data class MapCameraPosition(
    val targetLat: Double,
    val targetLng: Double,
    val zoom: Float,
    val tilt: Float,
    val bearing: Float,
)

@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class MapViewModel
@Inject
constructor(
    private val application: Application,
    mapPrefs: MapPrefs,
    private val googleMapsPrefs: GoogleMapsPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    serviceRepository: ServiceRepository,
    private val customTileProviderRepository: CustomTileProviderRepository,
    uiPreferencesDataSource: UiPreferencesDataSource,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, serviceRepository) {

    private val _selectedWaypointId = MutableStateFlow(savedStateHandle.toRoute<MapRoutes.Map>().waypointId)
    val selectedWaypointId: StateFlow<Int?> = _selectedWaypointId.asStateFlow()

    private val targetLatLng =
        googleMapsPrefs.cameraTargetLat
            .takeIf { it != 0.0 }
            ?.let { lat -> googleMapsPrefs.cameraTargetLng.takeIf { it != 0.0 }?.let { lng -> LatLng(lat, lng) } }
            ?: ourNodeInfo.value?.position?.toLatLng()
            ?: LatLng(0.0, 0.0)

    val cameraPositionState =
        CameraPositionState(
            position =
            CameraPosition(
                targetLatLng,
                googleMapsPrefs.cameraZoom,
                googleMapsPrefs.cameraTilt,
                googleMapsPrefs.cameraBearing,
            ),
        )

    val theme: StateFlow<Int> = uiPreferencesDataSource.theme

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    val customTileProviderConfigs: StateFlow<List<CustomTileProviderConfig>> =
        customTileProviderRepository.getCustomTileProviders().stateInWhileSubscribed(initialValue = emptyList())

    private val _selectedCustomTileProviderUrl = MutableStateFlow<String?>(null)
    val selectedCustomTileProviderUrl: StateFlow<String?> = _selectedCustomTileProviderUrl.asStateFlow()

    private val _selectedGoogleMapType = MutableStateFlow(MapType.NORMAL)
    val selectedGoogleMapType: StateFlow<MapType> = _selectedGoogleMapType.asStateFlow()

    val displayUnits =
        radioConfigRepository.deviceProfileFlow
            .mapNotNull { it.config?.display?.units }
            .stateInWhileSubscribed(initialValue = Config.DisplayConfig.DisplayUnits.METRIC)

    fun addCustomTileProvider(name: String, urlTemplate: String) {
        viewModelScope.launch {
            if (name.isBlank() || urlTemplate.isBlank() || !isValidTileUrlTemplate(urlTemplate)) {
                _errorFlow.emit("Invalid name or URL template for custom tile provider.")
                return@launch
            }
            if (customTileProviderConfigs.value.any { it.name.equals(name, ignoreCase = true) }) {
                _errorFlow.emit("Custom tile provider with name '$name' already exists.")
                return@launch
            }

            val newConfig = CustomTileProviderConfig(name = name, urlTemplate = urlTemplate)
            customTileProviderRepository.addCustomTileProvider(newConfig)
        }
    }

    fun updateCustomTileProvider(configToUpdate: CustomTileProviderConfig) {
        viewModelScope.launch {
            if (
                configToUpdate.name.isBlank() ||
                configToUpdate.urlTemplate.isBlank() ||
                !isValidTileUrlTemplate(configToUpdate.urlTemplate)
            ) {
                _errorFlow.emit("Invalid name or URL template for updating custom tile provider.")
                return@launch
            }
            val existingConfigs = customTileProviderConfigs.value
            if (
                existingConfigs.any {
                    it.id != configToUpdate.id && it.name.equals(configToUpdate.name, ignoreCase = true)
                }
            ) {
                _errorFlow.emit("Another custom tile provider with name '${configToUpdate.name}' already exists.")
                return@launch
            }

            customTileProviderRepository.updateCustomTileProvider(configToUpdate)

            val originalConfig = customTileProviderRepository.getCustomTileProviderById(configToUpdate.id)
            if (
                _selectedCustomTileProviderUrl.value != null &&
                originalConfig?.urlTemplate == _selectedCustomTileProviderUrl.value
            ) {
                // No change needed if URL didn't change, or handle if it did
            } else if (originalConfig != null && _selectedCustomTileProviderUrl.value != originalConfig.urlTemplate) {
                val currentlySelectedConfig =
                    customTileProviderConfigs.value.find { it.urlTemplate == _selectedCustomTileProviderUrl.value }
                if (currentlySelectedConfig?.id == configToUpdate.id) {
                    _selectedCustomTileProviderUrl.value = configToUpdate.urlTemplate
                }
            }
        }
    }

    fun removeCustomTileProvider(configId: String) {
        viewModelScope.launch {
            val configToRemove = customTileProviderRepository.getCustomTileProviderById(configId)
            customTileProviderRepository.deleteCustomTileProvider(configId)

            if (configToRemove != null && _selectedCustomTileProviderUrl.value == configToRemove.urlTemplate) {
                _selectedCustomTileProviderUrl.value = null
                // Also clear from prefs
                googleMapsPrefs.selectedCustomTileUrl = null
            }
        }
    }

    fun selectCustomTileProvider(config: CustomTileProviderConfig?) {
        if (config != null) {
            if (!isValidTileUrlTemplate(config.urlTemplate)) {
                Logger.withTag("MapViewModel").w("Attempted to select invalid URL template: ${config.urlTemplate}")
                _selectedCustomTileProviderUrl.value = null
                googleMapsPrefs.selectedCustomTileUrl = null
                return
            }
            _selectedCustomTileProviderUrl.value = config.urlTemplate
            _selectedGoogleMapType.value = MapType.NORMAL // Reset to a default or keep last? For now, reset.
            googleMapsPrefs.selectedCustomTileUrl = config.urlTemplate
            googleMapsPrefs.selectedGoogleMapType = null
        } else {
            _selectedCustomTileProviderUrl.value = null
            googleMapsPrefs.selectedCustomTileUrl = null
        }
    }

    fun setSelectedGoogleMapType(mapType: MapType) {
        _selectedGoogleMapType.value = mapType
        _selectedCustomTileProviderUrl.value = null // Clear custom selection
        googleMapsPrefs.selectedGoogleMapType = mapType.name
        googleMapsPrefs.selectedCustomTileUrl = null
    }

    fun createUrlTileProvider(urlString: String): TileProvider? {
        if (!isValidTileUrlTemplate(urlString)) {
            Logger.withTag("MapViewModel")
                .e("Tile URL does not contain valid {x}, {y}, and {z} placeholders: $urlString")
            return null
        }
        return object : UrlTileProvider(TILE_SIZE, TILE_SIZE) {
            override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                val formattedUrl =
                    urlString
                        .replace("{z}", zoom.toString(), ignoreCase = true)
                        .replace("{x}", x.toString(), ignoreCase = true)
                        .replace("{y}", y.toString(), ignoreCase = true)
                return try {
                    URL(formattedUrl)
                } catch (e: MalformedURLException) {
                    Logger.withTag("MapViewModel").e(e) { "Malformed URL: $formattedUrl" }
                    null
                }
            }
        }
    }

    private fun isValidTileUrlTemplate(urlTemplate: String): Boolean = urlTemplate.contains("{z}", ignoreCase = true) &&
        urlTemplate.contains("{x}", ignoreCase = true) &&
        urlTemplate.contains("{y}", ignoreCase = true)

    private val _mapLayers = MutableStateFlow<List<MapLayerItem>>(emptyList())
    val mapLayers: StateFlow<List<MapLayerItem>> = _mapLayers.asStateFlow()

    init {
        viewModelScope.launch {
            customTileProviderRepository.getCustomTileProviders().first()
            loadPersistedMapType()
        }
        loadPersistedLayers()

        selectedWaypointId.value?.let { wpId ->
            viewModelScope.launch {
                val wpMap = waypoints.first { it.containsKey(wpId) }
                wpMap[wpId]?.let { packet ->
                    val waypoint = packet.data.waypoint!!
                    val latLng = LatLng((waypoint.latitude_i ?: 0) / 1e7, (waypoint.longitude_i ?: 0) / 1e7)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                }
            }
        }
    }

    fun saveCameraPosition(cameraPosition: CameraPosition) {
        viewModelScope.launch {
            googleMapsPrefs.cameraTargetLat = cameraPosition.target.latitude
            googleMapsPrefs.cameraTargetLng = cameraPosition.target.longitude
            googleMapsPrefs.cameraZoom = cameraPosition.zoom
            googleMapsPrefs.cameraTilt = cameraPosition.tilt
            googleMapsPrefs.cameraBearing = cameraPosition.bearing
        }
    }

    private fun loadPersistedMapType() {
        val savedCustomUrl = googleMapsPrefs.selectedCustomTileUrl
        if (savedCustomUrl != null) {
            // Check if this custom provider still exists
            if (
                customTileProviderConfigs.value.any { it.urlTemplate == savedCustomUrl } &&
                isValidTileUrlTemplate(savedCustomUrl)
            ) {
                _selectedCustomTileProviderUrl.value = savedCustomUrl
                _selectedGoogleMapType.value = MapType.NORMAL // Default, as custom is active
            } else {
                // The saved custom URL is no longer valid or doesn't exist, remove preference
                googleMapsPrefs.selectedCustomTileUrl = null
                // Fallback to default Google Map type
                _selectedGoogleMapType.value = MapType.NORMAL
            }
        } else {
            val savedGoogleMapTypeName = googleMapsPrefs.selectedGoogleMapType
            try {
                _selectedGoogleMapType.value = MapType.valueOf(savedGoogleMapTypeName ?: MapType.NORMAL.name)
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "Invalid saved Google Map type: $savedGoogleMapTypeName" }
                _selectedGoogleMapType.value = MapType.NORMAL // Fallback in case of invalid stored name
                googleMapsPrefs.selectedGoogleMapType = null
            }
        }
    }

    private fun loadPersistedLayers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val layersDir = File(application.filesDir, "map_layers")
                if (layersDir.exists() && layersDir.isDirectory) {
                    val persistedLayerFiles = layersDir.listFiles()

                    if (persistedLayerFiles != null) {
                        val hiddenLayerUrls = googleMapsPrefs.hiddenLayerUrls
                        val loadedItems =
                            persistedLayerFiles.mapNotNull { file ->
                                if (file.isFile) {
                                    val layerType =
                                        when (file.extension.lowercase()) {
                                            "kml",
                                            "kmz",
                                            -> LayerType.KML
                                            "geojson",
                                            "json",
                                            -> LayerType.GEOJSON
                                            else -> null
                                        }

                                    layerType?.let {
                                        val uri = Uri.fromFile(file)
                                        MapLayerItem(
                                            name = file.nameWithoutExtension,
                                            uri = uri,
                                            isVisible = !hiddenLayerUrls.contains(uri.toString()),
                                            layerType = it,
                                        )
                                    }
                                } else {
                                    null
                                }
                            }
                        _mapLayers.value = loadedItems
                        if (loadedItems.isNotEmpty()) {
                            Logger.withTag("MapViewModel").i("Loaded ${loadedItems.size} persisted map layers.")
                        }
                    }
                } else {
                    Logger.withTag("MapViewModel").i("Map layers directory does not exist. No layers loaded.")
                }
            } catch (e: Exception) {
                Logger.withTag("MapViewModel").e(e) { "Error loading persisted map layers" }
                _mapLayers.value = emptyList()
            }
        }
    }

    fun addMapLayer(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            val layerName = fileName?.substringBeforeLast('.') ?: "Layer ${mapLayers.value.size + 1}"

            val extension =
                fileName?.substringAfterLast('.', "")?.lowercase()
                    ?: application.contentResolver.getType(uri)?.split('/')?.last()

            val kmlExtensions = listOf("kml", "kmz", "vnd.google-earth.kml+xml", "vnd.google-earth.kmz")
            val geoJsonExtensions = listOf("geojson", "json")

            val layerType =
                when (extension) {
                    in kmlExtensions -> LayerType.KML
                    in geoJsonExtensions -> LayerType.GEOJSON
                    else -> null
                }

            if (layerType == null) {
                Logger.withTag("MapViewModel").e("Unsupported map layer file type: $extension")
                return@launch
            }

            val finalFileName =
                if (fileName != null) {
                    "$layerName.$extension"
                } else {
                    "layer_${UUID.randomUUID()}.$extension"
                }

            val localFileUri = copyFileToInternalStorage(uri, finalFileName)

            if (localFileUri != null) {
                val newItem = MapLayerItem(name = layerName, uri = localFileUri, layerType = layerType)
                _mapLayers.value = _mapLayers.value + newItem
            } else {
                Logger.withTag("MapViewModel").e("Failed to copy file to internal storage.")
            }
        }
    }

    private suspend fun copyFileToInternalStorage(uri: Uri, fileName: String): Uri? = withContext(Dispatchers.IO) {
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

    fun toggleLayerVisibility(layerId: String) {
        var toggledLayer: MapLayerItem? = null
        val updatedLayers =
            _mapLayers.value.map {
                if (it.id == layerId) {
                    toggledLayer = it.copy(isVisible = !it.isVisible)
                    toggledLayer
                } else {
                    it
                }
            }
        _mapLayers.value = updatedLayers

        toggledLayer?.let {
            if (it.isVisible) {
                googleMapsPrefs.hiddenLayerUrls -= it.uri.toString()
            } else {
                googleMapsPrefs.hiddenLayerUrls += it.uri.toString()
            }
        }
    }

    fun removeMapLayer(layerId: String) {
        viewModelScope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            when (layerToRemove?.layerType) {
                LayerType.KML -> layerToRemove.kmlLayerData?.removeLayerFromMap()
                LayerType.GEOJSON -> layerToRemove.geoJsonLayerData?.removeLayerFromMap()
                null -> {}
            }
            layerToRemove?.uri?.let { uri ->
                deleteFileToInternalStorage(uri)
                googleMapsPrefs.hiddenLayerUrls -= uri.toString()
            }
            _mapLayers.value = _mapLayers.value.filterNot { it.id == layerId }
        }
    }

    private suspend fun deleteFileToInternalStorage(uri: Uri) {
        withContext(Dispatchers.IO) {
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

    @Suppress("Recycle")
    private suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? {
        val uriToLoad = layerItem.uri ?: return null
        return withContext(Dispatchers.IO) {
            try {
                application.contentResolver.openInputStream(uriToLoad)
            } catch (_: Exception) {
                Logger.d { "MapViewModel: Error opening InputStream from URI: $uriToLoad" }
                null
            }
        }
    }

    suspend fun loadMapLayerIfNeeded(map: GoogleMap, layerItem: MapLayerItem) {
        if (layerItem.kmlLayerData != null || layerItem.geoJsonLayerData != null) return
        try {
            when (layerItem.layerType) {
                LayerType.KML -> loadKmlLayerIfNeeded(layerItem, map)

                LayerType.GEOJSON -> loadGeoJsonLayerIfNeeded(layerItem, map)
            }
        } catch (e: Exception) {
            Logger.withTag("MapViewModel").e(e) { "Error loading map layer for ${layerItem.uri}" }
        }
    }

    private suspend fun loadKmlLayerIfNeeded(layerItem: MapLayerItem, map: GoogleMap) {
        val kmlLayer =
            getInputStreamFromUri(layerItem)?.use {
                KmlLayer(map, it, application.applicationContext).apply {
                    if (!layerItem.isVisible) removeLayerFromMap()
                }
            }
        _mapLayers.update { currentLayers ->
            currentLayers.map {
                if (it.id == layerItem.id) {
                    it.copy(kmlLayerData = kmlLayer)
                } else {
                    it
                }
            }
        }
    }

    private suspend fun loadGeoJsonLayerIfNeeded(layerItem: MapLayerItem, map: GoogleMap) {
        val geoJsonLayer =
            getInputStreamFromUri(layerItem)?.use { inputStream ->
                val jsonObject = JSONObject(inputStream.bufferedReader().use { it.readText() })
                GeoJsonLayer(map, jsonObject).apply { if (!layerItem.isVisible) removeLayerFromMap() }
            }
        _mapLayers.update { currentLayers ->
            currentLayers.map {
                if (it.id == layerItem.id) {
                    it.copy(geoJsonLayerData = geoJsonLayer)
                } else {
                    it
                }
            }
        }
    }

    fun clearLoadedLayerData() {
        _mapLayers.update { currentLayers ->
            currentLayers.map { it.copy(kmlLayerData = null, geoJsonLayerData = null) }
        }
    }
}

enum class LayerType {
    KML,
    GEOJSON,
}

data class MapLayerItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri? = null,
    var isVisible: Boolean = true,
    var kmlLayerData: KmlLayer? = null,
    var geoJsonLayerData: GeoJsonLayer? = null,
    val layerType: LayerType,
)
