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
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.app.MapFileImportBus
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapPrefs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.uuid.Uuid

/**
 * Flavor-neutral owner of the imported map-layer list, its internal-storage persistence, and the GeoJSON/KML import
 * plumbing. Both the Google (Google Maps data layer) and F-Droid (OSMdroid overlay) flavors observe [mapLayers] and
 * render it their own way; this class is the single home for everything that isn't the final overlay draw.
 *
 * A [Single] rather than per-flavor ViewModel state so the layer set survives ViewModel recreation and both flavors
 * share one implementation. Layers persist as files under `filesDir/map_layers`; hidden/network state lives in
 * [MapPrefs].
 */
@Single
@Suppress("TooManyFunctions")
class MapLayersManager(
    private val application: Application,
    private val dispatchers: CoroutineDispatchers,
    private val httpClient: HttpClient,
    private val mapPrefs: MapPrefs,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _mapLayers = MutableStateFlow<List<MapLayerItem>>(emptyList())
    val mapLayers: StateFlow<List<MapLayerItem>> = _mapLayers.asStateFlow()

    init {
        loadPersistedLayers()

        // Import a map file handed to us via an "Open in / Send to Meshtastic" intent (see MapFileImportBus).
        scope.launch {
            MapFileImportBus.pending.collect { uri ->
                uri ?: return@collect
                MapFileImportBus.pending.value = null
                addMapLayer(uri, uri.getFileName(application))
            }
        }
    }

    private fun loadPersistedLayers() {
        scope.launch(dispatchers.io) {
            try {
                val layersDir = File(application.filesDir, LAYERS_DIR)
                val hiddenLayerUrls = mapPrefs.hiddenLayerUrls.value
                val loadedItems =
                    if (layersDir.exists() && layersDir.isDirectory) {
                        layersDir.listFiles().orEmpty().mapNotNull { file ->
                            if (!file.isFile) return@mapNotNull null
                            resolveLayerType(file.extension)?.let { layerType ->
                                val uri = Uri.fromFile(file)
                                MapLayerItem(
                                    name = file.nameWithoutExtension,
                                    uri = uri,
                                    isVisible = !hiddenLayerUrls.contains(uri.toString()),
                                    layerType = layerType,
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }

                val networkItems =
                    mapPrefs.networkMapLayers.value.mapNotNull { networkString ->
                        val parts = networkString.split(NETWORK_LAYER_DELIMITER)
                        if (parts.size == NETWORK_LAYER_FIELDS) {
                            val uri = parts[2].toUri()
                            MapLayerItem(
                                id = parts[0],
                                name = parts[1],
                                uri = uri,
                                isVisible = !hiddenLayerUrls.contains(uri.toString()),
                                layerType = LayerType.KML,
                                isNetwork = true,
                            )
                        } else {
                            null
                        }
                    }

                _mapLayers.value = loadedItems + networkItems
                if (_mapLayers.value.isNotEmpty()) {
                    Logger.withTag(TAG).i("Loaded ${_mapLayers.value.size} persisted map layers.")
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.withTag(TAG).e(e) { "Error loading persisted map layers" }
                _mapLayers.value = emptyList()
            }
        }
    }

    fun addMapLayer(uri: Uri, fileName: String?) {
        scope.launch {
            val layerName = fileName?.substringBeforeLast('.') ?: "Layer ${mapLayers.value.size + 1}"
            val extension =
                fileName?.substringAfterLast('.', "")?.lowercase()
                    ?: application.contentResolver.getType(uri)?.split('/')?.last()

            val layerType = resolveLayerType(extension)
            if (layerType == null) {
                Logger.withTag(TAG).e("Unsupported map layer file type: $extension")
                return@launch
            }

            // Sanitize the on-disk name: fileName comes from an untrusted DISPLAY_NAME/lastPathSegment (share/open-with
            // from other apps), so strip anything that could let it escape map_layers/ (mirrors addGeoJsonLayer).
            val safeBase = layerName.replace(FILE_NAME_UNSAFE, "_")
            val finalFileName = if (fileName != null) "$safeBase.$extension" else "layer_${Uuid.random()}.$extension"
            val localFileUri = copyFileToInternalStorage(uri, finalFileName)
            if (localFileUri != null) {
                _mapLayers.update { it + MapLayerItem(name = layerName, uri = localFileUri, layerType = layerType) }
            } else {
                Logger.withTag(TAG).e("Failed to copy file to internal storage.")
            }
        }
    }

    /**
     * Import a GeoJSON string (e.g. handed back by the Site Planner headless bridge) as a visible local overlay,
     * reusing the same internal-storage-backed layer plumbing as file imports.
     */
    fun addGeoJsonLayer(name: String, geoJson: String) {
        scope.launch {
            val displayName = name.ifBlank { "Coverage" }
            val safeFileName = displayName.replace(FILE_NAME_UNSAFE, "_")
            val uri = writeStringToInternalStorage(geoJson, "${safeFileName}_${Uuid.random()}.geojson")
            if (uri != null) {
                _mapLayers.update { it + MapLayerItem(name = displayName, uri = uri, layerType = LayerType.GEOJSON) }
            } else {
                Logger.withTag(TAG).e("Failed to write GeoJSON layer to internal storage.")
            }
        }
    }

    /** Returns an error message if [name]/[url] are invalid, or null on success. Adds a persisted network layer. */
    @Suppress("ReturnCount") // guard clauses read clearer than nesting for this validation
    fun addNetworkMapLayer(name: String, url: String): String? {
        if (name.isBlank() || url.isBlank()) return "Invalid name or URL for network layer."
        val uri =
            try {
                url.toUri()
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                return "Invalid URL."
            }
        if (uri.scheme != "http" && uri.scheme != "https") return "URL must be http or https."

        val path = uri.path?.lowercase() ?: ""
        val layerType = if (path.endsWith(".geojson") || path.endsWith(".json")) LayerType.GEOJSON else LayerType.KML

        val newItem = MapLayerItem(name = name, uri = uri, layerType = layerType, isNetwork = true)
        _mapLayers.update { it + newItem }
        val encoded = listOf(newItem.id, newItem.name, newItem.uri).joinToString(NETWORK_LAYER_DELIMITER)
        mapPrefs.setNetworkMapLayers(mapPrefs.networkMapLayers.value + encoded)
        return null
    }

    fun toggleLayerVisibility(layerId: String) {
        val target = _mapLayers.value.find { it.id == layerId } ?: return
        val nowVisible = !target.isVisible
        _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isVisible = nowVisible) else it } }

        val uri = target.uri?.toString() ?: return
        val hidden = mapPrefs.hiddenLayerUrls.value
        mapPrefs.setHiddenLayerUrls(if (nowVisible) hidden - uri else hidden + uri)
    }

    fun removeMapLayer(layerId: String) {
        scope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            layerToRemove?.uri?.let { uri ->
                if (layerToRemove.isNetwork) {
                    mapPrefs.setNetworkMapLayers(
                        mapPrefs.networkMapLayers.value
                            .filterNot { it.startsWith("$layerId$NETWORK_LAYER_DELIMITER") }
                            .toSet(),
                    )
                } else {
                    deleteFileFromInternalStorage(uri)
                }
                mapPrefs.setHiddenLayerUrls(mapPrefs.hiddenLayerUrls.value - uri.toString())
            }
            _mapLayers.update { layers -> layers.filterNot { it.id == layerId } }
        }
    }

    /** Bounce a layer's [MapLayerItem.isRefreshing] so the render layer re-reads it (used for network refresh). */
    fun refreshMapLayer(layerId: String) {
        _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isRefreshing = true) else it } }
        _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isRefreshing = false) else it } }
    }

    fun refreshAllVisibleNetworkLayers() {
        _mapLayers.value.filter { it.isNetwork && it.isVisible }.forEach { refreshMapLayer(it.id) }
    }

    @Suppress("Recycle")
    suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? {
        val uriToLoad = layerItem.uri ?: return null
        return withContext(dispatchers.io) {
            try {
                if (layerItem.isNetwork && (uriToLoad.scheme == "http" || uriToLoad.scheme == "https")) {
                    val response = httpClient.get(uriToLoad.toString())
                    if (!response.status.isSuccess()) {
                        // Log only host, not the full URL (paths can carry user-identifying info).
                        Logger.withTag(TAG).e {
                            "HTTP ${response.status} fetching network layer from ${uriToLoad.host}"
                        }
                        return@withContext null
                    }
                    response.bodyAsChannel().toInputStream()
                } else {
                    application.contentResolver.openInputStream(uriToLoad)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Redact the URI: a content:///file:// path can include a user-chosen file name.
                Logger.withTag(TAG).e(e) { "Error opening InputStream for layer (scheme=${uriToLoad.scheme})" }
                null
            }
        }
    }

    private suspend fun copyFileToInternalStorage(uri: Uri, fileName: String): Uri? = withContext(dispatchers.io) {
        try {
            // openInputStream can return null (documented) — fail rather than return a Uri for a file never
            // written.
            val inputStream = application.contentResolver.openInputStream(uri) ?: return@withContext null
            val directory = File(application.filesDir, LAYERS_DIR).apply { if (!exists()) mkdirs() }
            val outputFile = File(directory, fileName)
            inputStream.use { input -> FileOutputStream(outputFile).use { output -> input.copyTo(output) } }
            Uri.fromFile(outputFile)
        } catch (e: IOException) {
            Logger.withTag(TAG).e(e) { "Error copying file to internal storage" }
            null
        }
    }

    private suspend fun writeStringToInternalStorage(content: String, fileName: String): Uri? =
        withContext(dispatchers.io) {
            try {
                val directory = File(application.filesDir, LAYERS_DIR).apply { if (!exists()) mkdirs() }
                val outputFile = File(directory, fileName)
                outputFile.writeText(content)
                Uri.fromFile(outputFile)
            } catch (e: IOException) {
                Logger.withTag(TAG).e(e) { "Error writing GeoJSON to internal storage" }
                null
            }
        }

    private suspend fun deleteFileFromInternalStorage(uri: Uri) {
        withContext(dispatchers.io) {
            try {
                val file = uri.toFile()
                if (file.exists()) file.delete()
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.withTag(TAG).e(e) { "Error deleting file from internal storage" }
            }
        }
    }

    private companion object {
        const val TAG = "MapLayersManager"
        const val LAYERS_DIR = "map_layers"
        const val NETWORK_LAYER_DELIMITER = "|:|"
        const val NETWORK_LAYER_FIELDS = 3 // id|:|name|:|uri

        // Characters not allowed in an on-disk layer file name; strips path separators so imports can't traverse.
        val FILE_NAME_UNSAFE = Regex("[^A-Za-z0-9._-]")
    }
}
