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
package org.meshtastic.feature.map.layers

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.MapPrefs

/**
 * Owner of the imported map-layer list, its on-disk persistence, and the import plumbing.
 *
 * Common code because every host imports layers: both Android flavours and the desktop app observe [mapLayers] and
 * render it their own way. This class is the single home for everything that is not the final overlay draw.
 *
 * Layers persist as files under [mapLayersDirectory]; hidden and network state lives in [MapPrefs]. Nothing here
 * touches a platform URI type — an import arrives as a [PickedMapFile] whose bytes the caller already knows how to
 * read, which is what lets one implementation serve a ContentResolver and a plain file path alike.
 */
@Single
@Suppress("TooManyFunctions")
class MapLayersManager
// layersDir and fileSystem are parameters so a test can point the store at a temporary directory and an in-memory file
// system. Koin resolves neither — the three-argument constructor below is the injected one.
internal constructor(
    private val dispatchers: CoroutineDispatchers,
    private val httpClient: HttpClient,
    private val mapPrefs: MapPrefs,
    private val layersDir: Path,
    private val fileSystem: FileSystem,
) {
    constructor(
        dispatchers: CoroutineDispatchers,
        httpClient: HttpClient,
        mapPrefs: MapPrefs,
    ) : this(dispatchers, httpClient, mapPrefs, mapLayersDirectory(), FileSystem.SYSTEM)

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    private val _mapLayers = MutableStateFlow<List<MapLayerItem>>(emptyList())
    val mapLayers: StateFlow<List<MapLayerItem>> = _mapLayers.asStateFlow()

    init {
        loadPersistedLayers()
    }

    private fun loadPersistedLayers() {
        scope.launch(dispatchers.io) {
            try {
                // await* (not .value) so a cold-start load doesn't see the StateFlow's initial empty default.
                val hiddenLayerUrls = mapPrefs.awaitHiddenLayerUrls()
                val loadedItems =
                    if (fileSystem.exists(layersDir)) {
                        fileSystem.list(layersDir).mapNotNull { path ->
                            val metadata = fileSystem.metadataOrNull(path)
                            if (metadata?.isRegularFile != true) return@mapNotNull null
                            resolveLayerType(path.name.substringAfterLast('.', "").ifBlank { null })?.let { type ->
                                val uri = path.toFileUri()
                                MapLayerItem(
                                    name = displayNameFromFileName(path.name.substringBeforeLast('.')),
                                    uri = uri,
                                    isVisible = !hiddenLayerUrls.contains(uri),
                                    layerType = type,
                                    createdAt = metadata.lastModifiedAtMillis?.takeIf { it > 0 },
                                )
                            }
                        }
                    } else {
                        emptyList()
                    }

                _mapLayers.value = loadedItems + persistedNetworkLayers(hiddenLayerUrls)
                if (_mapLayers.value.isNotEmpty()) {
                    Logger.withTag(TAG).i("Loaded ${_mapLayers.value.size} persisted map layers.")
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.withTag(TAG).e(e) { "Error loading persisted map layers" }
                _mapLayers.value = emptyList()
            }
        }
    }

    private suspend fun persistedNetworkLayers(hiddenLayerUrls: Set<String>): List<MapLayerItem> =
        mapPrefs.awaitNetworkMapLayers().mapNotNull { networkString ->
            val parts = networkString.split(NETWORK_LAYER_DELIMITER)
            if (parts.size == NETWORK_LAYER_FIELDS) {
                MapLayerItem(
                    id = parts[0],
                    name = parts[1],
                    uri = parts[2],
                    isVisible = !hiddenLayerUrls.contains(parts[2]),
                    layerType = LayerType.KML,
                    isNetwork = true,
                )
            } else {
                null
            }
        }

    /** Import a file the user picked. Unsupported types and unreadable sources are logged and skipped. */
    fun addMapLayer(picked: PickedMapFile) {
        scope.launch {
            val layerName = picked.displayName.substringBeforeLast('.').ifBlank { "Layer ${mapLayers.value.size + 1}" }
            val extension = picked.extensionOrMime?.substringAfterLast('.')?.lowercase()
            val layerType = resolveLayerType(extension)
            // resolveLayerType only matches non-null input, so a non-null type guarantees a non-null extension.
            if (layerType == null || extension == null) {
                Logger.withTag(TAG).e("Unsupported map layer file type: $extension")
                return@launch
            }

            val bytes = picked.read()
            if (bytes == null) {
                Logger.withTag(TAG).e("Could not read the picked map layer.")
                return@launch
            }
            val storedUri = write(bytes, layerFileName(layerName, extension))
            if (storedUri != null) {
                _mapLayers.update {
                    it + MapLayerItem(name = layerName, uri = storedUri, layerType = layerType, createdAt = nowMillis)
                }
            }
        }
    }

    /**
     * Import a GeoJSON string (e.g. handed back by the Site Planner headless bridge) as a visible local overlay,
     * reusing the same storage-backed layer plumbing as file imports.
     */
    fun addGeoJsonLayer(name: String, geoJson: String) {
        scope.launch {
            val displayName = name.ifBlank { "Coverage" }
            val uri = write(geoJson.encodeToByteArray(), layerFileName(displayName, COVERAGE_EXTENSION))
            if (uri != null) {
                _mapLayers.update {
                    it +
                        MapLayerItem(
                            name = displayName,
                            uri = uri,
                            layerType = LayerType.COVERAGE,
                            createdAt = nowMillis,
                        )
                }
            }
        }
    }

    /** Returns an error message if [name]/[url] are invalid, or null on success. Adds a persisted network layer. */
    @Suppress("ReturnCount") // guard clauses read clearer than nesting for this validation
    fun addNetworkMapLayer(name: String, url: String): String? {
        if (name.isBlank() || url.isBlank()) return "Invalid name or URL for network layer."
        val parsed =
            try {
                Url(url)
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") e: Exception) {
                return "Invalid URL."
            }
        if (parsed.protocol.name != "http" && parsed.protocol.name != "https") return "URL must be http or https."

        val path = parsed.encodedPath.lowercase()
        val layerType = if (path.endsWith(".geojson") || path.endsWith(".json")) LayerType.GEOJSON else LayerType.KML

        val newItem = MapLayerItem(name = name, uri = url, layerType = layerType, isNetwork = true)
        _mapLayers.update { it + newItem }
        val encoded = listOf(newItem.id, newItem.name, url).joinToString(NETWORK_LAYER_DELIMITER)
        mapPrefs.updateNetworkMapLayers { it + encoded }
        return null
    }

    fun toggleLayerVisibility(layerId: String) {
        val target = _mapLayers.value.find { it.id == layerId } ?: return
        val nowVisible = !target.isVisible
        _mapLayers.update { layers -> layers.map { if (it.id == layerId) it.copy(isVisible = nowVisible) else it } }

        val uri = target.uri ?: return
        mapPrefs.updateHiddenLayerUrls { if (nowVisible) it - uri else it + uri }
    }

    fun removeMapLayer(layerId: String) {
        scope.launch {
            val layerToRemove = _mapLayers.value.find { it.id == layerId }
            layerToRemove?.uri?.let { uri ->
                if (layerToRemove.isNetwork) {
                    mapPrefs.updateNetworkMapLayers { entries ->
                        entries.filterNot { it.startsWith("$layerId$NETWORK_LAYER_DELIMITER") }.toSet()
                    }
                } else {
                    delete(uri)
                }
                mapPrefs.updateHiddenLayerUrls { it - uri }
            }
            _mapLayers.update { layers -> layers.filterNot { it.id == layerId } }
        }
    }

    /** Bump a layer's [MapLayerItem.refreshToken] so renderers re-read it (used for network-layer refresh). */
    fun refreshMapLayer(layerId: String) {
        _mapLayers.update { layers ->
            layers.map { if (it.id == layerId) it.copy(refreshToken = it.refreshToken + 1) else it }
        }
    }

    fun refreshAllVisibleNetworkLayers() {
        _mapLayers.value.filter { it.isNetwork && it.isVisible }.forEach { refreshMapLayer(it.id) }
    }

    /**
     * The layer's bytes: fetched for a network layer, read from storage otherwise.
     *
     * Bytes rather than a stream because a stream has no common type here, and because both readers a layer feeds — the
     * KML converter and the zip reader — want the whole document anyway.
     */
    suspend fun readLayerBytes(layerItem: MapLayerItem): ByteArray? {
        val uri = layerItem.uri ?: return null
        return withContext(dispatchers.io) {
            try {
                if (layerItem.isNetwork && (uri.startsWith("http://") || uri.startsWith("https://"))) {
                    val response = httpClient.get(uri)
                    if (!response.status.isSuccess()) {
                        // Log only the host, not the full URL (paths can carry user-identifying info).
                        Logger.withTag(TAG).e { "HTTP ${response.status} fetching network layer from ${Url(uri).host}" }
                        return@withContext null
                    }
                    response.readRawBytes()
                } else {
                    fileSystem.read(uri.toLocalPath()) { readByteArray() }
                }
            } catch (e: CancellationException) {
                // The caller is a composition-scoped effect, so a layer refresh or leaving the map cancels this fetch
                // mid-flight. That is not a load failure and must not be reported as one.
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Redact the URI: it can include a user-chosen file name.
                Logger.withTag(TAG).e(e) { "Error reading map layer (network=${layerItem.isNetwork})" }
                null
            }
        }
    }

    private suspend fun write(bytes: ByteArray, fileName: String): String? = withContext(dispatchers.io) {
        try {
            fileSystem.createDirectories(layersDir)
            val target = layersDir / fileName
            fileSystem.write(target) { write(bytes) }
            target.toFileUri()
        } catch (e: IOException) {
            Logger.withTag(TAG).e(e) { "Error writing map layer to storage" }
            null
        }
    }

    private suspend fun delete(uri: String) {
        withContext(dispatchers.io) {
            try {
                fileSystem.delete(uri.toLocalPath(), mustExist = false)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Logger.withTag(TAG).e(e) { "Error deleting map layer from storage" }
            }
        }
    }

    private companion object {
        const val TAG = "MapLayersManager"
        const val NETWORK_LAYER_DELIMITER = "|:|"
        const val NETWORK_LAYER_FIELDS = 3 // id|:|name|:|uri
    }
}

/** The directory name under each platform's own app storage. */
internal const val LAYERS_DIR = "map_layers"
