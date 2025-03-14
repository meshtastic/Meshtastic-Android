/*
 * Copyright (c) 2025 Meshtastic LLC
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
import android.os.Build
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.util.toOneLineString
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

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
 * Write received and sent packets to a file
 */
class PacketLogFile(val context: Context, val type: PacketLogType) {
    private val name = when (type) {
        PacketLogType.SENT -> "sent_packets.log"
        PacketLogType.RECEIVED -> "received_packets.log"
    }
    private val file = PrintWriter(
        FileOutputStream(File(context.getExternalFilesDir(null), name), true)
    )

    init {
        file.use {
            it.println("${getTimeStamp()}: Log Start")
        }
    }

    fun close() {
        file.use {
            it.println("${getTimeStamp()}: Log End")
        }
    }

    fun log(p: ByteArray) {
        val logString = if (type == PacketLogType.SENT) {
            val toRadio = MeshProtos.ToRadio.parser().parseFrom(p)
            toRadio.toOneLineString()
        } else {
            MeshProtos.FromRadio.parser().parseFrom(p).toOneLineString()
        }
        file.use {
            it.println("${getTimeStamp()}: $logString")
        }
    }

    private fun getTimeStamp(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } else {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            dateFormat.format(Date())
        }
    }

    enum class PacketLogType {
        SENT,
        RECEIVED
    }
}
