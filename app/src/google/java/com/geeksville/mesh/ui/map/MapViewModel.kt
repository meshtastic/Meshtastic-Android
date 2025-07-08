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
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.model.Node
import com.google.android.gms.maps.GoogleMap
import com.google.maps.android.data.kml.KmlLayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

@Suppress("TooManyFunctions")
@HiltViewModel
class MapViewModel @Inject constructor(
    private val application: Application,
    private val preferences: SharedPreferences,
    nodeRepository: NodeRepository,
) : ViewModel() {

    private val onlyFavorites = MutableStateFlow(preferences.getBoolean("only-favorites", false))
    val nodes: StateFlow<List<Node>> =
        nodeRepository.getNodes().onEach { it.filter { !it.isIgnored } }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
    private val showWaypointsOnMap =
        MutableStateFlow(preferences.getBoolean("show-waypoints-on-map", true))
    private val showPrecisionCircleOnMap =
        MutableStateFlow(preferences.getBoolean("show-precision-circle-on-map", true))

    fun setOnlyFavorites(value: Boolean) {
        onlyFavorites.value = value
        preferences.edit { putBoolean("only-favorites", onlyFavorites.value) }
    }

    fun setShowWaypointsOnMap(value: Boolean) {
        showWaypointsOnMap.value = value
        preferences.edit { putBoolean("show-waypoints-on-map", value) }
    }

    fun setShowPrecisionCircleOnMap(value: Boolean) {
        showPrecisionCircleOnMap.value = value
        preferences.edit { putBoolean("show-precision-circle-on-map", value) }
    }

    data class MapFilterState(
        val onlyFavorites: Boolean,
        val showWaypoints: Boolean,
        val showPrecisionCircle: Boolean,
    )

    val mapFilterStateFlow: StateFlow<MapFilterState> = combine(
        onlyFavorites,
        showWaypointsOnMap,
        showPrecisionCircleOnMap,
    ) { favoritesOnly, showWaypoints, showPrecisionCircle ->
        MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapFilterState(false, true, true)
    )

    private val _mapLayers = MutableStateFlow<List<MapLayerItem>>(emptyList())
    val mapLayers: StateFlow<List<MapLayerItem>> = _mapLayers.asStateFlow()

    init {
        loadPersistedLayers()
    }

    private fun loadPersistedLayers() {
        viewModelScope.launch(Dispatchers.IO) { // Perform file operations on IO dispatcher
            try {
                val layersDir = File(application.filesDir, "map_layers")
                if (layersDir.exists() && layersDir.isDirectory) {
                    val persistedLayerFiles = layersDir.listFiles()

                    if (persistedLayerFiles != null) {
                        val loadedItems = persistedLayerFiles.mapNotNull { file ->
                            if (file.isFile) {
                                MapLayerItem(
                                    name = file.nameWithoutExtension,
                                    uri = Uri.fromFile(file),
                                    isVisible = true
                                )
                            } else {
                                null
                            }
                        }
                        _mapLayers.value = loadedItems
                        if (loadedItems.isNotEmpty()) {
                            Log.i(
                                "MapViewModel",
                                "Loaded ${loadedItems.size} persisted map layers."
                            )
                        }
                    }
                } else {
                    Log.i("MapViewModel", "Map layers directory does not exist. No layers loaded.")
                }
            } catch (e: Exception) {
                Log.e("MapViewModel", "Error loading persisted map layers", e)
                // Optionally, update UI to show an error or clear layers
                _mapLayers.value = emptyList()
            }
        }
    }

    // --- KML/KMZ Loading ---
    fun addMapLayer(uri: Uri, fileName: String?) {
        viewModelScope.launch {
            val layerName = fileName ?: "Layer ${mapLayers.value.size + 1}"
            // Copy the file to internal storage
            val localFileUri =
                copyFileToInternalStorage(uri, fileName ?: "layer_${UUID.randomUUID()}")

            if (localFileUri != null) {
                val newItem =
                    MapLayerItem(name = layerName, uri = localFileUri)
                _mapLayers.value = _mapLayers.value + newItem
            } else {
                // Handle error: Failed to copy file
                Log.e("MapViewModel", "Failed to copy KML/KMZ file to internal storage.")
            }
        }
    }

    private suspend fun copyFileToInternalStorage(uri: Uri, fileName: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val inputStream = application.contentResolver.openInputStream(uri)
                // It's good practice to store these in a dedicated subdirectory
                val directory = File(application.filesDir, "map_layers")
                if (!directory.exists()) {
                    directory.mkdirs()
                }
                val outputFile = File(directory, fileName)
                val outputStream = FileOutputStream(outputFile)

                inputStream?.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(outputFile)
            } catch (e: IOException) {
                Log.e("MapViewModel", "Error copying file to internal storage", e)
                null
            }
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        _mapLayers.value = _mapLayers.value.map {
            if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
        }
    }

    fun removeMapLayer(layerId: String) {
        viewModelScope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            layerToRemove?.kmlLayerData?.removeLayerFromMap() // Ensure we cast to KmlLayer if needed
            layerToRemove?.uri?.let { uri ->
                deleteFileFromInternalStorage(uri)
            }
            _mapLayers.value = _mapLayers.value.filterNot { it.id == layerId }
        }
    }

    private suspend fun deleteFileFromInternalStorage(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(uri.path ?: return@withContext) // Ensure path is not null
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
        val stream = withContext(Dispatchers.IO) {
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
            // Pass the whole layerItem to getInputStreamFromUri
            getInputStreamFromUri(layerItem)?.use { inputStream ->
                val kmlLayer = KmlLayer(
                    map,
                    inputStream,
                    application.applicationContext
                )
                _mapLayers.update { currentLayers ->
                    currentLayers.map {
                        if (it.id == layerItem.id) it.copy(kmlLayerData = kmlLayer) else it
                    }
                }
                kmlLayer
            }
        } catch (e: Exception) {
            Log.e(
                "MapViewModel",
                "Error loading KML for ${layerItem.uri}",
                e
            )
            null
        }
    }
}

data class MapLayerItem(
    val id: String = UUID.randomUUID().toString(), // Unique ID for the layer
    val name: String,
    val uri: Uri? = null, // Local URI of the file (if stored locally)
    var isVisible: Boolean = true,
    var kmlLayerData: KmlLayer? = null
)
