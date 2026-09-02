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
package org.meshtastic.feature.map.maplibre.terrain

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.feature.map.layers.mapLayerFileSystem
import org.meshtastic.feature.map.terrain.GeoBounds
import org.meshtastic.feature.map.terrain.TerrainDownloadFailure
import org.meshtastic.feature.map.terrain.TerrainDownloadState
import org.meshtastic.feature.map.terrain.TerrainRegionExtractor
import org.meshtastic.feature.map.terrain.TerrainSource
import org.meshtastic.feature.map.terrain.TerrainTileStore

/**
 * Owns the one downloaded offline-terrain region: its manifest, and the [TerrainTileStore] holding its tiles.
 *
 * Not a Koin `@Single`. [MapLibreMapViewProvider][org.meshtastic.feature.map.maplibre.MapLibreMapViewProvider]'s own
 * doc comment already states this module's rule — "free of any assumption about how the host app wires its graph" — and
 * it turns out to bind harder than usual here: a `@Module @ComponentScan` in this package would only take effect once
 * some host's Koin startup imports it, and every host that does that (`androidApp/src/main`'s `MainKoinModule`)
 * compiles into *both* Android flavors, so registering it there would pull a MapLibre-only type into the Google
 * flavor's dependency graph, exactly what this module's own build.gradle.kts forbids. [default] is how every caller
 * reaches the same instance instead — a plain lazily-initialized singleton, not a DI one.
 *
 * Single region, not a list: see [OfflineTerrainRegion]'s own doc comment for why. A new [download] therefore replaces
 * whatever was there before — deleting the old tiles and manifest *before* fetching the first new one, not after. This
 * is a known, deliberate tradeoff, not an oversight: there is nowhere to stage the new region side-by-side with the old
 * one without doubling disk usage for the download's duration, so a [download] that later fails or is cancelled leaves
 * nothing downloaded rather than restoring what was there — [region] reports `null`, the same as if nothing had ever
 * been fetched.
 */
class OfflineTerrainRepository(private val fileSystem: FileSystem, private val baseDir: Path) {

    private val store = TerrainTileStore(fileSystem, baseDir / TILES_DIR_NAME)

    /** For reading tile bytes directly — [org.meshtastic.feature.map.maplibre.layers.ContourLayer]'s own use. */
    val tileStore: TerrainTileStore
        get() = store

    private val manifestPath = baseDir / MANIFEST_FILE_NAME
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false

    // Owns download execution — deliberately not the caller's CoroutineScope. A UI-scoped download (the layers
    // sheet's own rememberCoroutineScope, say) is cancelled the instant that UI goes away — closing the sheet,
    // rotating the device — and TerrainRegionExtractor's own broad `catch (_: Exception)` also catches the
    // CancellationException that produces, running store.deleteAll() on a download the user never actually asked
    // to abandon. Owning the scope here means only this repository's own lifetime (the process) can cancel one.
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var downloadJob: Job? = null

    private val _region = MutableStateFlow<OfflineTerrainRegion?>(null)

    /** The current region, or `null` if nothing has been downloaded. Populated by [refresh]. */
    val region: StateFlow<OfflineTerrainRegion?> = _region.asStateFlow()

    private val _downloadState = MutableStateFlow<TerrainDownloadState?>(null)

    /** The most recent state [startDownload] has reported, surviving a UI that goes away and comes back. */
    val downloadState: StateFlow<TerrainDownloadState?> = _downloadState.asStateFlow()

    /**
     * Loads the manifest from disk if this is the first call. Idempotent, and cheap after the first: everything past
     * that point reads [region]'s already-loaded value. Callers that only ever read [region] from a composable should
     * call this once from a `LaunchedEffect`; see
     * [TerrainLayers][org.meshtastic.feature.map.maplibre.layers.TerrainLayers].
     */
    suspend fun refresh() {
        withContext(ioDispatcher) { mutex.withLock { loadIfNeeded(force = true) } }
    }

    /**
     * Replaces whatever region was downloaded before with a fresh one covering [bounds] up to [maxZoom].
     *
     * Runs on [ioDispatcher] — [TerrainRegionExtractor] does blocking range-request I/O per tile (see
     * `TerrainTileFetcher`'s own doc comment), and this flow's collector must never be left running on the caller's own
     * dispatcher for that reason. The manifest is written only after the extractor reports
     * [TerrainDownloadState.Complete], so a process death mid-download leaves tiles with no manifest — [loadIfNeeded]
     * treats that combination as orphaned and clears it, rather than resurrecting a manifest-less region.
     */
    fun download(bounds: GeoBounds, maxZoom: Int): Flow<TerrainDownloadState> = flow {
        mutex.withLock {
            loadIfNeeded()
            store.deleteAll()
            deleteManifest()
            _region.value = null

            TerrainRegionExtractor(store).download(bounds, maxZoom).collect { state ->
                val outcome =
                    when (state) {
                        is TerrainDownloadState.Complete -> persistCompletedDownload(bounds, maxZoom, state)

                        is TerrainDownloadState.Failed -> {
                            // Nothing partial is kept: TerrainRegionExtractor already ran store.deleteAll() itself on
                            // both failure reasons (TILE_LIMIT_EXCEEDED never wrote a tile to begin with).
                            Logger.withTag(LOG_TAG).w { "Offline terrain download failed: ${state.reason}" }
                            state
                        }

                        is TerrainDownloadState.InProgress -> state
                    }
                emit(outcome)
            }
        }
    }
        .flowOn(ioDispatcher)

    /**
     * Starts a [download] on this repository's own [scope] and mirrors its emissions into [downloadState], so a caller
     * observes progress instead of collecting a `Flow` tied to its own lifecycle — see [scope]'s own comment for why
     * that distinction matters here. A no-op while a download is already running, closing the double-tap window a
     * UI-side "is it downloading" check can't close on its own (the first [TerrainDownloadState.InProgress] only
     * arrives after several tiles have already been fetched).
     */
    fun startDownload(bounds: GeoBounds, maxZoom: Int) {
        if (downloadJob?.isActive == true) return
        _downloadState.value = null
        downloadJob = scope.launch { download(bounds, maxZoom).collect { state -> _downloadState.value = state } }
    }

    /** Deletes the current region's tiles and manifest, leaving nothing downloaded. A no-op if there is none. */
    suspend fun delete() {
        withContext(ioDispatcher) {
            mutex.withLock {
                store.deleteAll()
                deleteManifest()
                _region.value = null
                loaded = true
            }
        }
    }

    /**
     * The `file://` URL template [rememberRasterDemSource][org.maplibre.compose.sources.rememberRasterDemSource]
     * (hillshade) can use to reach [source]'s downloaded tiles directly, matching [TerrainTileStore]'s own
     * `<baseDir>/<source>/<zoom>/<x>/<y>.webp` layout.
     *
     * `baseDir` is always an absolute path (see [terrainStorageDirectory]'s platform actuals), so this yields a
     * three-slash `file:///...` URL — see `OfflineTerrainRepositoryTest`'s template test for why that is asserted
     * explicitly rather than left to be "fixed" to four slashes later.
     *
     * The directory portion is percent-encoded before interpolation — a Desktop/JVM user-data path can legitimately
     * contain characters reserved in a URL (a space in a Windows/macOS username is the common case, `#`/`%` less so but
     * just as real) — while the trailing `{z}/{x}/{y}` placeholders are appended afterwards, literally, so
     * [rememberRasterDemSource]'s own substitution still sees them unescaped.
     */
    fun tileUrlTemplate(source: TerrainSource): String {
        val encodedDir = (baseDir / TILES_DIR_NAME / source.dirName).toString().percentEncodeUrlReserved()
        return "file://$encodedDir/{z}/{x}/{y}.webp"
    }

    private fun loadIfNeeded(force: Boolean = false) {
        if (loaded && !force) return
        cleanUpOrphanedTiles()
        _region.value = readManifest()
        loaded = true
    }

    /**
     * A tile directory with no manifest can only be the result of a process death mid-[download] (the manifest is
     * always the last thing written, on [TerrainDownloadState.Complete]) — there is no region to describe it, so its
     * tiles are unreachable and just consuming disk. Cleared on the next load rather than left to accumulate.
     */
    private fun cleanUpOrphanedTiles() {
        if (fileSystem.exists(manifestPath)) return
        if (store.sizeBytes() > 0L) store.deleteAll()
    }

    /**
     * A manifest that fails to parse is unusable, but leaving it (and its tile directory) on disk would permanently
     * retain that disk usage with no region shown and no delete action available to reclaim it — the next call would
     * just hit the same corrupt file and return `null` again. Both are deleted here so the next [download] starts clean
     * instead of layering a fresh manifest over stale, orphaned tiles.
     *
     * A single [IllegalArgumentException] catch, not a separate one for `SerializationException`: kotlinx.serialization
     * throws that as a subtype of it, so one catch already covers both a malformed-JSON and a schema-mismatch manifest.
     */
    private fun readManifest(): OfflineTerrainRegion? {
        if (!fileSystem.exists(manifestPath)) return null
        return try {
            val text = fileSystem.read(manifestPath) { readUtf8() }
            json.decodeFromString<OfflineTerrainRegion>(text)
        } catch (error: IllegalArgumentException) {
            Logger.withTag(LOG_TAG).w(error) { "Discarding an unreadable offline-terrain manifest and its tiles" }
            deleteManifest()
            store.deleteAll()
            null
        }
    }

    /**
     * Builds the [OfflineTerrainRegion] for a [TerrainDownloadState.Complete] and persists it via [writeManifest] — the
     * one write in [download] with no error handling of its own, so a full disk or a permission failure here would
     * otherwise propagate as an uncaught exception instead of surfacing through [downloadState] like every other
     * failure mode. On write failure, the tiles [TerrainRegionExtractor] just fetched are discarded too: a manifest-
     * less tile directory is exactly what [cleanUpOrphanedTiles] treats as orphaned, so leaving them would just be
     * deferring the same cleanup to the next [loadIfNeeded] instead of reporting the failure now.
     */
    private fun persistCompletedDownload(
        bounds: GeoBounds,
        maxZoom: Int,
        state: TerrainDownloadState.Complete,
    ): TerrainDownloadState {
        val downloaded =
            OfflineTerrainRegion(
                south = bounds.south,
                west = bounds.west,
                north = bounds.north,
                east = bounds.east,
                maxZoom = maxZoom,
                hasRegionalDetail = state.hasRegionalDetail,
                tileCount = state.tileCount,
                byteSize = state.byteSize,
            )
        return try {
            writeManifest(downloaded)
            _region.value = downloaded
            state
        } catch (error: IOException) {
            Logger.withTag(LOG_TAG).w(error) { "Failed to persist the offline-terrain manifest" }
            store.deleteAll()
            deleteManifest()
            _region.value = null
            TerrainDownloadState.Failed(TerrainDownloadFailure.IO_ERROR)
        }
    }

    private fun writeManifest(region: OfflineTerrainRegion) {
        manifestPath.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(manifestPath) { writeUtf8(json.encodeToString(region)) }
    }

    private fun deleteManifest() {
        if (fileSystem.exists(manifestPath)) fileSystem.delete(manifestPath)
    }

    companion object {
        private const val TILES_DIR_NAME = "tiles"
        private const val MANIFEST_FILE_NAME = "manifest.json"
        private const val LOG_TAG = "OfflineTerrainRepository"

        /**
         * The one instance every host and composable in this module shares — see the class doc comment for why this is
         * a plain lazy singleton rather than a Koin one. Backed by [terrainStorageDirectory] and [mapLayerFileSystem],
         * the same storage-location split [org.meshtastic.feature.map.layers.MapLayersManager] uses.
         */
        val default: OfflineTerrainRepository by lazy {
            OfflineTerrainRepository(mapLayerFileSystem(), terrainStorageDirectory())
        }
    }
}
