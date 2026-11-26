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

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

private const val DOWNLOAD_BUFFER_SIZE = 8192

/**
 * GETs [url] into [targetFile], streaming the body so [maxBytes] stops the transfer itself rather than only what is
 * written. The status check and the copy both run inside the response block, because a plain `get` buffers the whole
 * body before returning.
 *
 * @return `false` when the request failed or the server answered with a non-success status; an over-limit or short body
 *   throws as in [downloadResponseToFile].
 */
internal suspend fun downloadUrlToFile(
    client: HttpClient,
    url: String,
    targetFile: File,
    onProgress: (Float) -> Unit,
    maxBytes: Long = MAX_FIRMWARE_ZIP_BYTES,
): Boolean {
    var copyFailure: Exception? = null
    val completed =
        try {
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    Logger.w { "Download failed: ${response.status.value} for $url" }
                    return@execute false
                }
                // Surfaced after the response closes, so a copy failure is never mistaken for a request failure.
                copyFailure = copyFailureOrNull(response, targetFile, onProgress, maxBytes)
                copyFailure == null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.w(e) { "Download failed for $url" }
            return false
        }
    copyFailure?.let { throw it }
    return completed
}

/** Runs [downloadResponseToFile] and returns its failure instead of throwing it; cancellation still propagates. */
private suspend fun copyFailureOrNull(
    response: HttpResponse,
    targetFile: File,
    onProgress: (Float) -> Unit,
    maxBytes: Long,
): Exception? = try {
    downloadResponseToFile(response, targetFile, onProgress, maxBytes)
    null
} catch (e: CancellationException) {
    throw e
} catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
    e
}

/**
 * Streams [response]'s body into [targetFile], reporting fractional progress via [onProgress] as bytes arrive.
 *
 * Shared by the Android and desktop JVM [FirmwareFileHandler] implementations so the two byte-copy loops (and their
 * cancellation/completeness checks) cannot drift apart — see [extractZipEntriesBounded] for the same rationale on the
 * zip-extraction side. Streams incrementally rather than buffering the whole body: ktor's built-in `onDownload`
 * progress hook pairs naturally with `HttpResponse.body(): ByteArray`, but that holds an entire firmware image — tens
 * of MB — in memory (twice over, briefly), which isn't worth it just to drop a manual loop on a download this size.
 *
 * @param maxBytes ceiling on the body; the transfer stops and [targetFile] is deleted once it is exceeded.
 * @throws IOException if the transfer is shorter than the response's declared `Content-Length`, or longer than
 *   [maxBytes].
 * @throws kotlinx.coroutines.CancellationException if the calling coroutine is cancelled mid-transfer.
 */
internal suspend fun downloadResponseToFile(
    response: HttpResponse,
    targetFile: File,
    onProgress: (Float) -> Unit,
    maxBytes: Long = MAX_FIRMWARE_ZIP_BYTES,
) {
    val body = response.bodyAsChannel()
    val contentLength = response.contentLength() ?: -1L

    body.toInputStream().use { input ->
        FileOutputStream(targetFile).use { output ->
            val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (input.read(buffer).also { bytesRead = it } != -1) {
                coroutineContext.ensureActive()

                totalBytesRead += bytesRead
                if (totalBytesRead > maxBytes) {
                    output.close()
                    targetFile.delete()
                    throw IOException("Download too large: more than $maxBytes bytes")
                }
                output.write(buffer, 0, bytesRead)

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
