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
package org.meshtastic.feature.firmware

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * Streams [response]'s body into [targetFile], reporting fractional progress via [onProgress] as bytes arrive.
 *
 * Shared by the Android and desktop JVM [FirmwareFileHandler] implementations so the two byte-copy loops (and their
 * cancellation/completeness checks) cannot drift apart — see [extractZipEntriesBounded] for the same rationale on the
 * zip-extraction side. Streams incrementally rather than buffering the whole body: ktor's built-in `onDownload`
 * progress hook pairs naturally with `HttpResponse.body(): ByteArray`, but that holds an entire firmware image — tens
 * of MB — in memory (twice over, briefly), which isn't worth it just to drop a manual loop on a download this size.
 *
 * @throws IOException if the transfer is shorter than the response's declared `Content-Length`.
 * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled mid-transfer.
 */
internal suspend fun downloadResponseToFile(response: HttpResponse, targetFile: File, onProgress: (Float) -> Unit) {
    val body = response.bodyAsChannel()
    val contentLength = response.contentLength() ?: -1L

    body.toInputStream().use { input ->
        FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()

                output.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead

                if (contentLength > 0) {
                    onProgress(totalBytesRead.toFloat() / contentLength)
                }
            }
            if (contentLength != -1L && totalBytesRead != contentLength) {
                throw IOException("Incomplete download: expected $contentLength bytes, got $totalBytesRead")
            }
        }
    }
}
