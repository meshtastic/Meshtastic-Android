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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.app.map.model.CustomTileProviderConfig
import org.meshtastic.app.map.model.isValidTileUrlTemplate
import org.meshtastic.app.map.prefs.map.GoogleCameraPosition
import org.meshtastic.app.map.prefs.map.GoogleMapSelectionPrefs
import org.meshtastic.app.map.prefs.map.GoogleMapsPrefs
import org.meshtastic.app.map.repository.CustomTileProviderRepository
import org.meshtastic.app.map.repository.CustomTileProviderSaveResult
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
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.MalformedURLException
import java.net.URL
import kotlin.uuid.Uuid

private const val TILE_SIZE = 256

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
    mapPrefs: MapPrefs,
    private val googleMapsPrefs: GoogleMapsPrefs,
    nodeRepository: NodeRepository,
    packetRepository: PacketRepository,
    radioConfigRepository: RadioConfigRepository,
    radioController: RadioController,
    private val customTileProviderRepository: CustomTileProviderRepository,
    private val mapTileProviderPrefs: MapTileProviderPrefs,
    uiPrefs: UiPrefs,
    notificationPrefs: NotificationPrefs,
    savedStateHandle: SavedStateHandle,
) : BaseMapViewModel(
    mapPrefs,
    nodeRepository,
    packetRepository,
    radioController,
    radioConfigRepository,
    notificationPrefs,
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
                        val latLng = LatLng((waypoint.latitude_i ?: 0) / 1e7, (waypoint.longitude_i ?: 0) / 1e7)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
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

    private val _selectedCustomTileProviderId = MutableStateFlow<String?>(null)
    val selectedCustomTileProviderId: StateFlow<String?> = _selectedCustomTileProviderId.asStateFlow()

    val selectedCustomTileProvider: StateFlow<CustomTileProviderConfig?> =
        combine(_selectedCustomTileProviderId, customTileProviderConfigs) { selectedId, providers ->
            providers.findSelectedCustomTileProvider(selectedId)
        }
            .stateInWhileSubscribed(initialValue = null)

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
            val wasSelected = _selectedCustomTileProviderId.value == configId
            customTileProviderRepository.deleteCustomTileProvider(configId)

            if (configToRemove != null) {
                if (wasSelected) {
                    clearCurrentTileProvider()
                    _selectedCustomTileProviderId.value = null
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
                _selectedCustomTileProviderId.value = null
                _selectedGoogleMapType.value = MapType.NORMAL
                viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
                googleMapsPrefs.setSelectedCustomTileUrl(null)
                googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
                return
            }
            _selectedCustomTileProviderId.value = config.id
            _selectedGoogleMapType.value = MapType.NONE
            viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(config.id) }
            googleMapsPrefs.setSelectedCustomTileUrl(null)
            googleMapsPrefs.setSelectedGoogleMapType(null)
        } else {
            clearCurrentTileProvider()
            _selectedCustomTileProviderId.value = null
            _selectedGoogleMapType.value = MapType.NORMAL
            viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
            googleMapsPrefs.setSelectedCustomTileUrl(null)
            googleMapsPrefs.setSelectedGoogleMapType(MapType.NORMAL.name)
        }
    }

    fun setSelectedGoogleMapType(mapType: MapType) {
        clearCurrentTileProvider()
        _selectedGoogleMapType.value = mapType
        _selectedCustomTileProviderId.value = null
        viewModelScope.launch { mapTileProviderPrefs.setSelectedCustomTileProviderId(null) }
        googleMapsPrefs.setSelectedGoogleMapType(mapType.name)
        googleMapsPrefs.setSelectedCustomTileUrl(null)
    }

    private var currentTileProvider: TileProvider? = null
    private var currentTileProviderConfig: CustomTileProviderConfig? = null

    fun getTileProvider(config: CustomTileProviderConfig?): TileProvider? {
        if (config == null) {
            clearCurrentTileProvider()
            return null
        }

        if (currentTileProvider != null && currentTileProviderConfig == config) {
            return currentTileProvider
        }

        clearCurrentTileProvider()

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
                    Logger.withTag("MapViewModel").w("Selected local MBTiles file does not exist")
                    null
                }
            } else {
                val urlString = config.urlTemplate
                if (!isValidTileUrlTemplate(urlString)) {
                    Logger.withTag("MapViewModel").w("Selected custom tile URL template is invalid")
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
                            } catch (_: MalformedURLException) {
                                Logger.withTag("MapViewModel").w("Custom tile provider produced a malformed URL")
                                null
                            }
                        }
                    }
                }
            }

        currentTileProvider = newProvider
        currentTileProviderConfig = config.takeIf { newProvider != null }
        return newProvider
    }

    private fun isValidTileUrlTemplate(urlTemplate: String): Boolean =
        urlTemplate.isValidTileUrlTemplate(requireHttps = false)

    private fun clearCurrentTileProvider() {
        (currentTileProvider as? MBTilesProvider)?.close()
        currentTileProvider = null
        currentTileProviderConfig = null
    }

    /** Imported overlay layers; owned by the flavor-neutral [MapLayersManager] and rendered by [MapLayerOverlay]. */
    val mapLayers: StateFlow<List<MapLayerItem>> = mapLayersManager.mapLayers

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
                    val latLng = LatLng((waypoint.latitude_i ?: 0) / 1e7, (waypoint.longitude_i ?: 0) / 1e7)
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 15f)
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
        val resolvedSelection =
            providers.resolvePersistedCustomTileSelection(
                selectedProviderId = selectedProviderId,
                legacySource = selection.customTileUrl,
                providerLoadSuccessful = providerLoadSuccessful,
            )
        val selectedProvider = resolvedSelection.provider

        if (selectedProvider != null) {
            _selectedCustomTileProviderId.value = selectedProvider.id
            _selectedGoogleMapType.value = MapType.NONE
            if (selectedProviderId != selectedProvider.id) {
                mapTileProviderPrefs.setSelectedCustomTileProviderId(selectedProvider.id)
            }
            if (selection.customTileUrl != null) googleMapsPrefs.setSelectedCustomTileUrl(null)
        } else {
            _selectedCustomTileProviderId.value = null
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

    fun addMapLayer(uri: Uri, fileName: String?) = mapLayersManager.addMapLayer(uri, fileName)

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

    suspend fun getInputStreamFromUri(layerItem: MapLayerItem): InputStream? =
        mapLayersManager.getInputStreamFromUri(layerItem)

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
