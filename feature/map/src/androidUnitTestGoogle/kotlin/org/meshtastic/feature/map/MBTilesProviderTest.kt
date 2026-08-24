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
package org.meshtastic.feature.map

import android.database.sqlite.SQLiteDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MBTilesProviderTest {

    @get:Rule val tempFolder = TemporaryFolder()

    @Test
    fun `getTile translates y coordinate correctly to TMS`() {
        val dbFile = tempFolder.newFile("test.mbtiles")
        setupMockDatabase(dbFile)

        val provider = MBTilesProvider(dbFile)

        // Google Maps zoom 1, x=0, y=0
        // TMS y = (1 << 1) - 1 - 0 = 1
        provider.getTile(0, 0, 1)

        // We verify the query was correct by checking the database if we could,
        // but here we just ensure it doesn't crash and returns the expected No Tile if missing.
        // To truly test, we'd need to insert data.

        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        db.execSQL("INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (1, 0, 1, x'1234')")
        db.close()

        val tile = provider.getTile(0, 0, 1)
        assertEquals(256, tile?.width)
        assertEquals(256, tile?.height)
        // Robolectric SQLite might return different blob handling, but let's see.
    }

    private fun setupMockDatabase(file: File) {
        val db = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.CREATE_IF_NECESSARY)
        db.execSQL("CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER, tile_data BLOB)")
        db.close()
    }
}
