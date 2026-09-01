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

    fun list(): List<OfflineRegion> = readManifest()

    suspend fun add(region: OfflineRegion) {
        mutex.withLock { writeManifest(readManifest().filterNot { it.id == region.id } + region) }
    }

    suspend fun delete(id: String) {
        mutex.withLock {
            archiveFile(id).delete()
            writeManifest(readManifest().filterNot { it.id == id })
        }
    }

    fun totalBytes(): Long = readManifest().sumOf { it.byteSize }

    private fun readManifest(): List<OfflineRegion> {
        if (!manifestFile.exists()) return emptyList()
        return try {
            Json.decodeFromString<List<OfflineRegion>>(manifestFile.readText())
        } catch (e: kotlinx.serialization.SerializationException) {
            LOG.w(e) { "Offline-region manifest is corrupt; treating it as empty" }
            emptyList()
        }
    }

    private fun writeManifest(regions: List<OfflineRegion>) {
        manifestFile.writeText(Json.encodeToString(regions))
    }

    companion object {
        private val LOG = Logger.withTag("OfflineRegionStore")
    }
}
