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
package org.meshtastic.core.common.util

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * A specialized [FileOutputStream] that writes data to a file in the application's external files directory. Primarily
 * used for low-level protocol debugging and packet logging.
 *
 * @param context The context used to locate the external files directory.
 * @param name The name of the log file.
 */
class BinaryLogFile(context: Context, name: String) :
    FileOutputStream(File(context.getExternalFilesDir(null), name), true)
