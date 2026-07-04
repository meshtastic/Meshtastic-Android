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
import com.google.android.gms.maps.model.TileProvider
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.MapType
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.MapPrefs
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.map.BaseMapViewModel
import org.meshtastic.proto.Config
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import kotlin.uuid.Uuid

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
@KoinViewModel
class MapViewModel(
    private val application: Application,
    private val dispatchers: CoroutineDispatchers,
    private val httpClient: HttpClient,
    mapPrefs: MapPrefs,
    private val googleMapsPrefs: GoogleMapsPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    radioController: RadioController,
    private val customTileProviderRepository: CustomTileProviderRepository,
    uiPrefs: UiPrefs,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(mapPrefs, nodeRepository, packetRepository, radioController) {

    private val _selectedWaypointId = MutableStateFlow(savedStateHandle.get<Int>("waypointId"))
    val selectedWaypointId: StateFlow<Int?> = _selectedWaypointId.asStateFlow()

    fun setWaypointId(id: Int?) {
        if (_selectedWaypointId.value != id) {
            _selectedWaypointId.value = id
            if (id != null) {
                viewModelScope.launch {
                    val wpMap = waypoints.first { it.containsKey(id) }
                    wpMap[id]?.let { packet ->
                        val waypoint = packet.waypoint!!
                        val latLng = LatLng((waypoint.latitude_i ?: 0) / 1e7, (waypoint.longitude_i ?: 0) / 1e7)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                    }
                }
            }
        }
    }

    private val targetLatLng =
        googleMapsPrefs.cameraTargetLat.value
            .takeIf { it != 0.0 }
            ?.let { lat -> googleMapsPrefs.cameraTargetLng.value.takeIf { it != 0.0 }?.let { lng -> LatLng(lat, lng) } }
            ?: ourNodeInfo.value?.position?.toLatLng()
            ?: LatLng(0.0, 0.0)

    val cameraPositionState =
        CameraPositionState(
            position =
            CameraPosition(
                targetLatLng,
                googleMapsPrefs.cameraZoom.value,
                googleMapsPrefs.cameraTilt.value,
                googleMapsPrefs.cameraBearing.value,
            ),
        )

    val theme: StateFlow<Int> = uiPrefs.theme

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: Flow<String> = _errorFlow.asFlow()

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
            if (customTileProviderConfigs.value.any { it.name.equals(name, ignoreCase = true) }) {
                _errorFlow.emit("Custom tile provider with name '$name' already exists.")
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
                } catch (e: Exception) {
                    Logger.withTag("MapViewModel").e(e) { "Error processing local URI" }
                    _errorFlow.emit("Error processing local URI for MBTiles.")
                    return@launch
                }
            }

            val newConfig = CustomTileProviderConfig(name = name, urlTemplate = urlTemplate, localUri = finalLocalUri)
            customTileProviderRepository.addCustomTileProvider(newConfig)
        }
    }

    fun updateCustomTileProvider(configToUpdate: CustomTileProviderConfig) {
        viewModelScope.launch {
            if (
                configToUpdate.name.isBlank() ||
                (configToUpdate.urlTemplate.isBlank() && configToUpdate.localUri == null) ||
                (configToUpdate.localUri == null && !isValidTileUrlTemplate(configToUpdate.urlTemplate))
            ) {
                _errorFlow.emit("Invalid name, URL template, or local URI for updating custom tile provider.")
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

            if (configToRemove != null) {
                if (
                    _selectedCustomTileProviderUrl.value == configToRemove.urlTemplate ||
                    _selectedCustomTileProviderUrl.value == configToRemove.localUri
                ) {
                    _selectedCustomTileProviderUrl.value = null
                    // Also clear from prefs
                    googleMapsPrefs.setSelectedCustomTileUrl(null)
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
                Logger.withTag("MapViewModel").w("Attempted to select invalid URL template: ${config.urlTemplate}")
                _selectedCustomTileProviderUrl.value = null
                googleMapsPrefs.setSelectedCustomTileUrl(null)
                return
            }
            // Use localUri if present, otherwise urlTemplate
            val selectedUrl = config.localUri ?: config.urlTemplate
            _selectedCustomTileProviderUrl.value = selectedUrl
            _selectedGoogleMapType.value = MapType.NONE
            googleMapsPrefs.setSelectedCustomTileUrl(selectedUrl)
            googleMapsPrefs.setSelectedGoogleMapType(null)
        } else {
            _selectedCustomTileProviderUrl.value = null
            _selectedGoogleMapType.value = MapType.NORMAL
            googleMapsPrefs.setSelectedCustomTileUrl(null)
            googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
        }
    }

    fun setSelectedGoogleMapType(mapType: MapType) {
        _selectedGoogleMapType.value = mapType
        _selectedCustomTileProviderUrl.value = null // Clear custom selection
        googleMapsPrefs.setSelectedGoogleMapType(mapType.name)
        googleMapsPrefs.setSelectedCustomTileUrl(null)
    }

    private var currentTileProvider: TileProvider? = null

    fun getTileProvider(config: CustomTileProviderConfig?): TileProvider? {
        if (config == null) {
            (currentTileProvider as? MBTilesProvider)?.close()
            currentTileProvider = null
            return null
        }

        val selectedUrl = config.localUri ?: config.urlTemplate
        if (currentTileProvider != null && _selectedCustomTileProviderUrl.value == selectedUrl) {
            return currentTileProvider
        }

        // Close previous if it was a local provider
        (currentTileProvider as? MBTilesProvider)?.close()

        val newProvider =
            if (config.isLocal) {
                val uri = Uri.parse(config.localUri)
                val file =
                    try {
                        uri.toFile()
                    } catch (e: Exception) {
                        File(uri.path ?: "")
                    }
                if (file.exists()) {
                    MBTilesProvider(file)
                } else {
                    Logger.withTag("MapViewModel").e("Local MBTiles file does not exist: ${config.localUri}")
                    null
                }
            } else {
                val urlString = config.urlTemplate
                if (!isValidTileUrlTemplate(urlString)) {
                    Logger.withTag("MapViewModel")
                        .e("Tile URL does not contain valid {x}, {y}, and {z} placeholders: $urlString")
                    null
                } else {
                    object : UrlTileProvider(TILE_SIZE, TILE_SIZE) {
                        override fun getTileUrl(x: Int, y: Int, zoom: Int): URL? {
                            val subdomains = listOf("a", "b", "c")
                            val subdomain = subdomains[(x + y) % subdomains.size]
                            val formattedUrl =
                                urlString
                                    .replace("{s}", subdomain, ignoreCase = true)
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
            }

        currentTileProvider = newProvider
        return newProvider
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
                    val waypoint = packet.waypoint!!
                    val latLng = LatLng((waypoint.latitude_i ?: 0) / 1e7, (waypoint.longitude_i ?: 0) / 1e7)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
                }
            }
        }
    }

    fun saveCameraPosition(cameraPosition: CameraPosition) {
        viewModelScope.launch {
            googleMapsPrefs.setCameraTargetLat(cameraPosition.target.latitude)
            googleMapsPrefs.setCameraTargetLng(cameraPosition.target.longitude)
            googleMapsPrefs.setCameraZoom(cameraPosition.zoom)
            googleMapsPrefs.setCameraTilt(cameraPosition.tilt)
            googleMapsPrefs.setCameraBearing(cameraPosition.bearing)
        }
    }

    private fun loadPersistedMapType() {
        val savedCustomUrl = googleMapsPrefs.selectedCustomTileUrl.value
        if (savedCustomUrl != null) {
            // Check if this custom provider still exists
            if (
                customTileProviderConfigs.value.any { it.urlTemplate == savedCustomUrl } &&
                isValidTileUrlTemplate(savedCustomUrl)
            ) {
                _selectedCustomTileProviderUrl.value = savedCustomUrl
                _selectedGoogleMapType.value =
                    MapType.NONE // MapType.NONE to hide google basemap when using custom provider
            } else {
                // The saved custom URL is no longer valid or doesn't exist, remove preference
                googleMapsPrefs.setSelectedCustomTileUrl(null)
                // Fallback to default Google Map type
                _selectedGoogleMapType.value = MapType.NORMAL
            }
        } else {
            val savedGoogleMapTypeName = googleMapsPrefs.selectedGoogleMapType.value
            try {
                _selectedGoogleMapType.value = MapType.valueOf(savedGoogleMapTypeName ?: MapType.NORMAL.name)
            } catch (e: IllegalArgumentException) {
                Logger.e(e) { "Invalid saved Google Map type: $savedGoogleMapTypeName" }
                _selectedGoogleMapType.value = MapType.NORMAL // Fallback in case of invalid stored name
                googleMapsPrefs.setSelectedGoogleMapType(null)
            }
        }
    }

    private fun loadPersistedLayers() {
        viewModelScope.launch(dispatchers.io) {
            try {
                val layersDir = File(application.filesDir, "map_layers")
                if (layersDir.exists() && layersDir.isDirectory) {
                    val persistedLayerFiles = layersDir.listFiles()

                    if (persistedLayerFiles != null) {
                        val hiddenLayerUrls = googleMapsPrefs.hiddenLayerUrls.value
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

                        val networkItems =
                            googleMapsPrefs.networkMapLayers.value.mapNotNull { networkString ->
                                try {
                                    val parts = networkString.split("|:|")
                                    if (parts.size == 3) {
                                        val id = parts[0]
                                        val name = parts[1]
                                        val uri = Uri.parse(parts[2])
                                        MapLayerItem(
                                            id = id,
                                            name = name,
                                            uri = uri,
                                            isVisible = !hiddenLayerUrls.contains(uri.toString()),
                                            layerType = LayerType.KML,
                                            isNetwork = true,
                                        )
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    null
                                }
                            }

                        _mapLayers.value = loadedItems + networkItems
                        if (_mapLayers.value.isNotEmpty()) {
                            Logger.withTag("MapViewModel").i("Loaded ${_mapLayers.value.size} persisted map layers.")
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
                    "layer_${Uuid.random()}.$extension"
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

    fun addNetworkMapLayer(name: String, url: String) {
        viewModelScope.launch {
            if (name.isBlank() || url.isBlank()) {
                _errorFlow.emit("Invalid name or URL for network layer.")
                return@launch
            }
            try {
                val uri = Uri.parse(url)
                if (uri.scheme != "http" && uri.scheme != "https") {
                    _errorFlow.emit("URL must be http or https.")
                    return@launch
                }

                val path = uri.path?.lowercase() ?: ""
                val layerType =
                    when {
                        path.endsWith(".geojson") || path.endsWith(".json") -> LayerType.GEOJSON
                        else -> LayerType.KML // Default to KML
                    }

                val newItem = MapLayerItem(name = name, uri = uri, layerType = layerType, isNetwork = true)
                _mapLayers.value = _mapLayers.value + newItem

                val networkLayerString = "${newItem.id}|:|${newItem.name}|:|${newItem.uri}"
                googleMapsPrefs.setNetworkMapLayers(googleMapsPrefs.networkMapLayers.value + networkLayerString)
            } catch (e: Exception) {
                _errorFlow.emit("Invalid URL.")
            }
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
                googleMapsPrefs.setHiddenLayerUrls(googleMapsPrefs.hiddenLayerUrls.value - it.uri.toString())
            } else {
                googleMapsPrefs.setHiddenLayerUrls(googleMapsPrefs.hiddenLayerUrls.value + it.uri.toString())
            }
        }
    }

    fun removeMapLayer(layerId: String) {
        viewModelScope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            layerToRemove?.uri?.let { uri ->
                if (layerToRemove.isNetwork) {
                    googleMapsPrefs.setNetworkMapLayers(
                        googleMapsPrefs.networkMapLayers.value.filterNot { it.startsWith("$layerId|:|") }.toSet(),
                    )
                } else {
                    deleteFileToInternalStorage(uri)
                }
                googleMapsPrefs.setHiddenLayerUrls(googleMapsPrefs.hiddenLayerUrls.value - uri.toString())
            }
            _mapLayers.value = _mapLayers.value.filterNot { it.id == layerId }
        }
    }

    fun refreshMapLayer(layerId: String) {
        viewModelScope.launch {
            _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isRefreshing = true) else it } }
            // By resetting the layer data in the UI (implied by just refreshing),
            // we trigger a reload in the Composable.
            _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isRefreshing = false) else it } }
        }
    }

    fun refreshAllVisibleNetworkLayers() {
        _mapLayers.value.filter { it.isNetwork && it.isVisible }.forEach { refreshMapLayer(it.id) }
    }

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

    @Suppress("Recycle")
    suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? {
        val uriToLoad = layerItem.uri ?: return null
        return withContext(dispatchers.io) {
            try {
                if (layerItem.isNetwork && (uriToLoad.scheme == "http" || uriToLoad.scheme == "https")) {
                    val response = httpClient.get(uriToLoad.toString())
                    if (!response.status.isSuccess()) {
                        Logger.withTag("MapViewModel").e { "HTTP ${response.status} fetching layer: $uriToLoad" }
                        return@withContext null
                    }
                    response.bodyAsChannel().toInputStream()
                } else {
                    application.contentResolver.openInputStream(uriToLoad)
                }
            } catch (e: Exception) {
                Logger.withTag("MapViewModel").e(e) { "Error opening InputStream from URI: $uriToLoad" }
                null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        (currentTileProvider as? MBTilesProvider)?.close()
    }

    override fun getUser(userId: String?) =
        nodeRepository.getUser(userId ?: org.meshtastic.core.model.DataPacket.ID_BROADCAST)
}

enum class LayerType {
    KML,
    GEOJSON,
}

data class MapLayerItem(
    val id: String = Uuid.random().toString(),
    val name: String,
    val uri: Uri? = null,
    val isVisible: Boolean = true,
    val layerType: LayerType,
    val isNetwork: Boolean = false,
    val isRefreshing: Boolean = false,
)
