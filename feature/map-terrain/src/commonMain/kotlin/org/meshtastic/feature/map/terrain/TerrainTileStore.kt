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
package org.meshtastic.feature.map.terrain

import okio.FileSystem
import okio.Path

/**
 * Raw Terrarium WebP tiles as a plain file hierarchy under [baseDir]: `<baseDir>/<source>/<zoom>/<x>/<y>.webp`.
 *
 * Plain files, not a SQLite archive (unlike the base offline layer's Google-only archive): this store also has to work
 * on Desktop, and Okio's [FileSystem] is genuinely multiplatform where Android's SQLite APIs aren't. One instance is
 * scoped to one region's own terrain directory — callers own deciding where that directory lives.
 */
class TerrainTileStore(private val fileSystem: FileSystem, private val baseDir: Path) {

    fun writeTile(source: TerrainSource, tile: TileIndex, webpBytes: ByteArray) {
        val path = tilePath(source, tile)
        path.parent?.let { fileSystem.createDirectories(it) }
        fileSystem.write(path) { write(webpBytes) }
    }

    fun readTile(source: TerrainSource, tile: TileIndex): ByteArray? {
        val path = tilePath(source, tile)
        if (!fileSystem.exists(path)) return null
        return fileSystem.read(path) { readByteArray() }
    }

    fun hasTile(source: TerrainSource, tile: TileIndex): Boolean = fileSystem.exists(tilePath(source, tile))

    /** Total bytes on disk under [baseDir] — both [TerrainSource.GLOBAL] and [TerrainSource.REGIONAL], if present. */
    fun sizeBytes(): Long {
        if (!fileSystem.exists(baseDir)) return 0L
        return fileSystem
            .listRecursively(baseDir)
            .filter { fileSystem.metadata(it).isRegularFile }
            .sumOf { fileSystem.metadata(it).size ?: 0L }
    }

    /** Deletes every tile this store owns — the whole [baseDir], both sources. */
    fun deleteAll() {
        if (fileSystem.exists(baseDir)) fileSystem.deleteRecursively(baseDir)
    }

    private fun tilePath(source: TerrainSource, tile: TileIndex): Path =
        baseDir / source.dirName / tile.zoom.toString() / tile.x.toString() / "${tile.y}.webp"
}

/** Mapterhorn's two-tier archive split: a global low-res layer always present, a regional high-res layer sometimes. */
enum class TerrainSource(val dirName: String) {
    GLOBAL("global"),
    REGIONAL("regional"),
}
