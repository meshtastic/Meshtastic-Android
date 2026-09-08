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
package org.meshtastic.app.map.offline.pmtiles

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where downloaded offline regions live: one `.mbtiles` archive per region plus a JSON manifest of their metadata, all
 * under `filesDir/offline_regions/`. Every mutating call takes [mutex], since a download completing and the user
 * tapping delete can race.
 */
internal class OfflineRegionStore(private val baseDir: File) {

    private val manifestFile = File(baseDir, "manifest.json")
    private val mutex = Mutex()

    init {
        baseDir.mkdirs()
    }

    fun archiveFile(id: String): File = File(baseDir, "$id.mbtiles")

    /** Where a region's terrain tiles live — an [org.meshtastic.feature.map.terrain.TerrainTileStore] rooted here. */
    fun terrainDir(id: String): File = File(baseDir, "$id/terrain")

    fun list(): List<OfflineRegion> = readManifest()

    suspend fun add(region: OfflineRegion) {
        mutex.withLock { writeManifest(readManifest().filterNot { it.id == region.id } + region) }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            archiveFile(id).delete()
            terrainDir(id).deleteRecursively()
            writeManifest(readManifest().filterNot { it.id == id })
        }
    }

    /** Every byte a downloaded region occupies — its base vector archive plus any terrain attached to it. */
    fun totalBytes(): Long = readManifest().sumOf { it.byteSize + it.terrainByteSize }

    private fun readManifest(): List<OfflineRegion> {
        if (!manifestFile.exists()) return emptyList()
        return try {
            Json.decodeFromString<List<OfflineRegion>>(manifestFile.readText())
        } catch (e: kotlinx.serialization.SerializationException) {
            LOG.w(e) { "Offline-region manifest is corrupt; treating it as empty" }
            emptyList()
        }
    }

    // Write-then-rename: a process death mid-write leaves the untouched original (or a stray .tmp file), never a
    // half-written manifest.json that readManifest's corruption handling would silently mistake for "no regions".
    private fun writeManifest(regions: List<OfflineRegion>) {
        val tmpFile = File(baseDir, "manifest.json.tmp")
        tmpFile.writeText(Json.encodeToString(regions))
        Files.move(
            tmpFile.toPath(),
            manifestFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    companion object {
        private val LOG = Logger.withTag("OfflineRegionStore")
    }
}
