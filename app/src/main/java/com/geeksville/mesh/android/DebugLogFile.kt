/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.android

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter

/**
 * Create a debug log on the SD card (if needed and allowed and app is configured for debugging (FIXME)
 *
 * write strings to that file
 */
class DebugLogFile(context: Context, name: String) {
    val stream = FileOutputStream(File(context.getExternalFilesDir(null), name), true)
    val file = PrintWriter(stream)

    fun close() {
        file.close()
    }

    fun log(s: String) {
        file.println(s) // FIXME, optionally include timestamps
        file.flush() // for debugging
    }
}


/**
 * Create a debug log on the SD card (if needed and allowed and app is configured for debugging (FIXME)
 *
 * write strings to that file
 */
class BinaryLogFile(context: Context, name: String) :
    FileOutputStream(File(context.getExternalFilesDir(null), name), true) {

}