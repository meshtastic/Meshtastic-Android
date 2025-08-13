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

package com.geeksville.mesh.ui.map

import android.app.Application
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.repository.map.CustomTileProviderRepository
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.TileProvider
import com.google.android.gms.maps.model.UrlTileProvider
import com.google.maps.android.compose.MapType
import com.google.maps.android.data.kml.KmlLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

@Suppress("TooManyFunctions")
@HiltViewModel
class MapViewModel
@Inject
constructor(
    private val application: Application,
    preferences: SharedPreferences,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    private val customTileProviderRepository: CustomTileProviderRepository,
) : BaseMapViewModel(preferences, nodeRepository, packetRepository) {

    private val _errorFlow = MutableSharedFlow<String>()
    val errorFlow: SharedFlow<String> = _errorFlow.asSharedFlow()

    val customTileProviderConfigs: StateFlow<List<CustomTileProviderConfig>> =
        customTileProviderRepository
            .getCustomTileProviders()
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = emptyList())

    private val _selectedCustomTileProviderUrl = MutableStateFlow<String?>(null)
    val selectedCustomTileProviderUrl: StateFlow<String?> = _selectedCustomTileProviderUrl.asStateFlow()

    private val _selectedGoogleMapType = MutableStateFlow<MapType>(MapType.NORMAL)
    val selectedGoogleMapType: StateFlow<MapType> = _selectedGoogleMapType.asStateFlow()

    private val _cameraPosition = MutableStateFlow<MapCameraPosition?>(null)

    val cameraPosition: StateFlow<MapCameraPosition?> = _cameraPosition.asStateFlow()

    fun onCameraPositionChanged(cameraPosition: CameraPosition) {
        _cameraPosition.value =
            MapCameraPosition(
                targetLat = cameraPosition.target.latitude,
                targetLng = cameraPosition.target.longitude,
                zoom = cameraPosition.zoom,
                tilt = cameraPosition.tilt,
                bearing = cameraPosition.bearing,
            )
    }

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
            }
        }
    }

    fun selectCustomTileProvider(config: CustomTileProviderConfig?) {
        if (config != null) {
            if (!isValidTileUrlTemplate(config.urlTemplate)) {
                Log.w("MapViewModel", "Attempted to select invalid URL template: ${config.urlTemplate}")
                _selectedCustomTileProviderUrl.value = null
                return
            }
            _selectedCustomTileProviderUrl.value = config.urlTemplate
        } else {
            _selectedCustomTileProviderUrl.value = null
        }
    }

    fun setSelectedGoogleMapType(mapType: MapType) {
        _selectedGoogleMapType.value = mapType
        if (_selectedCustomTileProviderUrl.value != null) {
            _selectedCustomTileProviderUrl.value = null
        }
    }

    fun createUrlTileProvider(urlString: String): TileProvider? {
        if (!isValidTileUrlTemplate(urlString)) {
            Log.e("MapViewModel", "Tile URL does not contain valid {x}, {y}, and {z} placeholders: $urlString")
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
                    Log.e("MapViewModel", "Malformed URL: $formattedUrl", e)
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
        loadPersistedLayers()
    }

    private fun loadPersistedLayers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val layersDir = File(application.filesDir, "map_layers")
                if (layersDir.exists() && layersDir.isDirectory) {
                    val persistedLayerFiles = layersDir.listFiles()

                    if (persistedLayerFiles != null) {
                        val loadedItems =
                            persistedLayerFiles.mapNotNull { file ->
                                if (file.isFile) {
                                    MapLayerItem(
                                        name = file.nameWithoutExtension,
                                        uri = Uri.fromFile(file),
                                        isVisible = true,
                                    )
                                } else {
                                    null
                                }
                            }
                        _mapLayers.value = loadedItems
                        if (loadedItems.isNotEmpty()) {
                            Log.i("MapViewModel", "Loaded ${loadedItems.size} persisted map layers.")
                        }
                    }
                } else {
                    Log.i("MapViewModel", "Map layers directory does not exist. No layers loaded.")
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error loading persisted map layers", e)
                _mapLayers.value = emptyList()
            }
        }
    }

    fun addMapLayer(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            val layerName = fileName ?: "Layer ${mapLayers.value.size + 1}"
            val localFileUri = copyFileToInternalStorage(uri, fileName ?: "layer_${UUID.randomUUID()}")

            if (localFileUri != null) {
                val newItem = MapLayerItem(name = layerName, uri = localFileUri)
                _mapLayers.value = _mapLayers.value + newItem
            } else {
                Log.e("MapViewModel", "Failed to copy KML/KMZ file to internal storage.")
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
            Log.e("MapViewModel", "Error copying file to internal storage", e)
            null
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        _mapLayers.value = _mapLayers.value.map { if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it }
    }

    fun removeMapLayer(layerId: String) {
        viewModelScope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            layerToRemove?.kmlLayerData?.removeLayerFromMap()
            layerToRemove?.uri?.let { uri -> deleteFileFromInternalStorage(uri) }
            _mapLayers.value = _mapLayers.value.filterNot { it.id == layerId }
        }
    }

    private suspend fun deleteFileFromInternalStorage(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(uri.path ?: return@withContext)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error deleting file from internal storage", e)
            }
        }
    }

    @Suppress("Recycle")
    suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? {
        val uriToLoad = layerItem.uri ?: return null
        val stream =
            withContext(Dispatchers.IO) {
                try {
                    application.contentResolver.openInputStream(uriToLoad)
                } catch (_: Exception) {
                    debug("MapViewModel: Error opening InputStream from URI: $uriToLoad")
                    null
                }
            }
        return stream
    }

    suspend fun loadKmlLayerIfNeeded(map: GoogleMap, layerItem: MapLayerItem): KmlLayer? {
        if (layerItem.kmlLayerData != null) {
            return layerItem.kmlLayerData
        }

        return try {
            getInputStreamFromUri(layerItem)?.use { inputStream ->
                val kmlLayer = KmlLayer(map, inputStream, application.applicationContext)
                _mapLayers.update { currentLayers ->
                    currentLayers.map { if (it.id == layerItem.id) it.copy(kmlLayerData = kmlLayer) else it }
                }
                kmlLayer
            }
        } catch (e: Exception) {
            Log.e("MapViewModel", "Error loading KML for ${layerItem.uri}", e)
            null
        }
    }
}

data class MapLayerItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val uri: Uri? = null,
    var isVisible: Boolean = true,
    var kmlLayerData: KmlLayer? = null,
)

@Serializable
data class CustomTileProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val urlTemplate: String,
)
