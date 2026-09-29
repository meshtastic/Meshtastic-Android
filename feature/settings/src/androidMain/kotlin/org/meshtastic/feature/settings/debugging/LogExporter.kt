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
package org.meshtastic.feature.settings.debugging

import co.touchlab.kermit.Logger
import java.util.concurrent.TimeUnit

/**
 * Dumps this app's own logcat, filtered to our process id via `--pid` (API 24+, minSdk is 26). Without READ_LOGS the OS
 * already limits us to our own entries, but `--pid` guarantees it even if that permission is ever granted (e.g. via adb
 * on an emulator) so a shared bug report can't leak other apps' logs. Best-effort: a capture failure returns a marker
 * rather than throwing. ProcessBuilder with a merged stderr avoids a pipe-buffer deadlock, and the bounded wait keeps a
 * stuck capture from tying up the IO thread.
 */
actual fun captureAppLogcat(): String = try {
    val pid = android.os.Process.myPid()
    val process =
        ProcessBuilder("logcat", "-d", "-v", "time", "--pid=$pid", "-t", "5000").redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    if (!process.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) process.destroyForcibly()
    output
} catch (e: java.io.IOException) {
    Logger.e(e) { "Failed to capture logcat" }
    "logcat capture failed: ${e.message}"
}

private const val LOGCAT_TIMEOUT_SECONDS = 5L
