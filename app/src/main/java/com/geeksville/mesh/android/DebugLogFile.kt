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