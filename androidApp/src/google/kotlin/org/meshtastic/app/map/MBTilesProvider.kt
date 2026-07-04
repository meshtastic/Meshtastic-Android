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

import android.database.sqlite.SQLiteDatabase
import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider
import java.io.File

class MBTilesProvider(private val file: File) :
    TileProvider,
    AutoCloseable {
    private var database: SQLiteDatabase? = null

    init {
        openDatabase()
    }

    private fun openDatabase() {
        if (database == null && file.exists()) {
            database = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }
    }

    override fun getTile(x: Int, y: Int, zoom: Int): Tile? {
        val db = database ?: return null

        var tile: Tile? = null
        // Convert Google Maps y coordinate to standard TMS y coordinate
        val tmsY = (1 shl zoom) - 1 - y

        val cursor =
            db.rawQuery(
                "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
                arrayOf(zoom.toString(), x.toString(), tmsY.toString()),
            )

        if (cursor.moveToFirst()) {
            val tileData = cursor.getBlob(0)
            tile = Tile(256, 256, tileData)
        }
        cursor.close()

        return tile ?: TileProvider.NO_TILE
    }

    override fun close() {
        database?.close()
        database = null
    }
}
