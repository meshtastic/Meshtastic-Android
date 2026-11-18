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
@file:Suppress("UnusedParameter")

package org.meshtastic.web.file

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.repository.FileService
import org.meshtastic.core.ui.util.WEB_DOWNLOAD_SCHEME
import kotlin.js.Promise

/**
 * Browser [FileService]. Writes go to a `download:` URI from the save launcher and leave as a file download; reads come
 * from the `blob:` URL the open launcher makes for the picked file.
 */
@Suppress("TooGenericExceptionCaught")
class WebFileService : FileService {
    override suspend fun write(uri: CommonUri, block: suspend (BufferedSink) -> Unit): Boolean = try {
        require(uri.scheme == WEB_DOWNLOAD_SCHEME) { "Not a download URI" }
        val buffer = Buffer()
        block(buffer)
        downloadBytes(buffer.readByteArray().toUint8Array(), uri.schemeSpecificPart ?: DEFAULT_FILE_NAME)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(e) { "Failed to download $uri" }
        false
    }

    override suspend fun read(uri: CommonUri, block: suspend (BufferedSource) -> Unit): Boolean = try {
        require(uri.scheme == BLOB_SCHEME) { "Not a picked file" }
        val url = uri.toString()
        val bytes =
            try {
                readBlobUrl(url).await<JsUint8Array>().toByteArray()
            } finally {
                revokeObjectUrl(url)
            }
        block(Buffer().write(bytes))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.e(e) { "Failed to read the picked file" }
        false
    }

    private companion object {
        const val BLOB_SCHEME = "blob"
        const val DEFAULT_FILE_NAME = "download"
    }
}

private external interface JsUint8Array : JsAny

private fun readBlobUrl(url: String): Promise<JsUint8Array> =
    js("fetch(url).then((r) => r.arrayBuffer()).then((b) => new Uint8Array(b))")

private fun revokeObjectUrl(url: String): Unit = js("URL.revokeObjectURL(url)")

private fun downloadBytes(bytes: JsUint8Array, fileName: String): Unit = js(
    """{
        const url = URL.createObjectURL(new Blob([bytes], { type: "application/octet-stream" }));
        const link = document.createElement("a");
        link.href = url;
        link.download = fileName;
        link.style.display = "none";
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 60000);
    }""",
)

private fun newUint8Array(size: Int): JsUint8Array = js("new Uint8Array(size)")

private fun setByte(array: JsUint8Array, index: Int, value: Byte): Unit = js("array[index] = value")

private fun byteLength(array: JsUint8Array): Int = js("array.byteLength")

private fun signedByteAt(array: JsUint8Array, index: Int): Byte = js("(array[index] << 24) >> 24")

private fun ByteArray.toUint8Array(): JsUint8Array {
    val array = newUint8Array(size)
    for (i in indices) setByte(array, i, this[i])
    return array
}

private fun JsUint8Array.toByteArray(): ByteArray = ByteArray(byteLength(this)) { signedByteAt(this, it) }
