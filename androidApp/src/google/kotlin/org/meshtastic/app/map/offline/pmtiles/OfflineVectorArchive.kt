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

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * A downloaded offline region, stored on disk as a standard `.mbtiles` archive (`tiles`/`metadata` tables, TMS row
 * numbering) holding MVT tiles — the same container format [org.meshtastic.app.map.MBTilesProvider] already reads for
 * raster imports, just with `format=pbf` instead of a raster format. Tiles are stored decompressed:
 * [OfflineRegionExtractor] gunzips each one before [writeTile], trading more disk per tile for a simpler read path with
 * no per-read inflate. Any generic MBTiles tool can still open a region this writes; nothing here is a bespoke format.
 */
internal class OfflineVectorArchive private constructor(private val database: SQLiteDatabase) : AutoCloseable {

    fun writeTile(zoom: Int, x: Int, y: Int, gzippedTile: ByteArray) {
        val values =
            ContentValues().apply {
                put(COLUMN_ZOOM, zoom)
                put(COLUMN_COLUMN, x)
                put(COLUMN_ROW, tmsRow(zoom, y))
                put(COLUMN_DATA, gzippedTile)
            }
        database.insertWithOnConflict(TABLE_TILES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** The gzip-compressed tile bytes at (zoom, x, y) — google/osm XYZ, matching [ch.poole.geo.pmtiles.Reader]. */
    fun readTile(zoom: Int, x: Int, y: Int): ByteArray? = database
        .rawQuery(
            "SELECT $COLUMN_DATA FROM $TABLE_TILES WHERE $COLUMN_ZOOM = ? AND $COLUMN_COLUMN = ? AND $COLUMN_ROW = ?",
            arrayOf(zoom.toString(), x.toString(), tmsRow(zoom, y).toString()),
        )
        .use { cursor -> if (cursor.moveToFirst()) cursor.getBlob(0) else null }

    fun tileCount(): Long = database.rawQuery("SELECT COUNT(*) FROM $TABLE_TILES", null).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    override fun close() {
        database.close()
    }

    private fun tmsRow(zoom: Int, xyzY: Int): Int = (1 shl zoom) - 1 - xyzY

    companion object {
        private const val TABLE_TILES = "tiles"
        private const val COLUMN_ZOOM = "zoom_level"
        private const val COLUMN_COLUMN = "tile_column"
        private const val COLUMN_ROW = "tile_row"
        private const val COLUMN_DATA = "tile_data"

        /** Creates a fresh archive at [file], overwriting anything already there. */
        fun create(file: File, attribution: String): OfflineVectorArchive {
            file.delete()
            val database = SQLiteDatabase.openOrCreateDatabase(file, null)
            database.execSQL(
                "CREATE TABLE $TABLE_TILES (" +
                    "$COLUMN_ZOOM INTEGER, $COLUMN_COLUMN INTEGER, $COLUMN_ROW INTEGER, $COLUMN_DATA BLOB, " +
                    "PRIMARY KEY ($COLUMN_ZOOM, $COLUMN_COLUMN, $COLUMN_ROW))",
            )
            database.execSQL("CREATE TABLE metadata (name TEXT, value TEXT)")
            val metadata =
                mapOf(
                    "name" to "Meshtastic offline region",
                    "format" to "pbf",
                    "attribution" to attribution,
                    "type" to "baselayer",
                )
            metadata.forEach { (name, value) ->
                database.insert(
                    "metadata",
                    null,
                    ContentValues().apply {
                        put("name", name)
                        put("value", value)
                    },
                )
            }
            return OfflineVectorArchive(database)
        }

        /** Opens an already-downloaded archive read-only. */
        fun open(file: File): OfflineVectorArchive? = if (file.exists()) {
            OfflineVectorArchive(SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY))
        } else {
            null
        }
    }
}
