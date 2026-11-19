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
package org.meshtastic.web.platform

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.ui.util.readWebFile
import org.meshtastic.core.ui.util.writeWebFile

/**
 * Reads and writes the `web-file:` URIs the web file launchers hand out: a pick from the file chooser, or a save that
 * lands through Chromium's save picker or as a download. The whole file passes through memory, since a browser gives
 * the page no stream to a download.
 */
class WebFileService : FileService {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean = try {
        val buffer = Buffer()
        block(buffer)
        writeWebFile(uri, buffer.readByteArray())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.e(e) { "Failed to write to URI: $uri" }
        false
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = try {
        val bytes = readWebFile(uri)
        if (bytes == null) {
            false
        } else {
            block(Buffer().write(bytes))
            true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Logger.e(e) { "Failed to read from URI: $uri" }
        false
    }
}
