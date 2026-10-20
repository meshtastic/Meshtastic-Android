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
package org.meshtastic.core.network

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.di.CoroutineDispatchers

/** Fetches the flasher's GLB device models; the URL is the illustration's with the extension swapped. */
@Single
class DeviceModelRemoteDataSource(private val client: HttpClient, private val dispatchers: CoroutineDispatchers) {

    /**
     * True when the model is served, false when the flasher answered without one, null when it could not be asked. The
     * flasher is a single-page app that answers a missing path with 200 and its HTML shell, so only the GLB content
     * type counts.
     */
    suspend fun exists(imageFile: String): Boolean? = withContext(dispatchers.io) {
        safeCatching { client.head(modelUrl(imageFile)).isGlb() }.getOrNull()
    }

    /** The model bytes, or null when absent or the fetch failed. */
    suspend fun fetch(imageFile: String): ByteArray? = withContext(dispatchers.io) {
        safeCatching {
            val response = client.get(modelUrl(imageFile))
            if (response.isGlb()) response.readRawBytes() else null
        }
            .getOrNull()
    }

    private fun HttpResponse.isGlb(): Boolean =
        status.isSuccess() && contentType()?.withoutParameters() == GLB_CONTENT_TYPE

    companion object {
        private val GLB_CONTENT_TYPE = ContentType("model", "gltf-binary")

        fun modelUrl(imageFile: String): String =
            "${HttpClientDefaults.DEVICE_MODEL_BASE_URL}/${imageFile.substringBeforeLast('.')}.glb"
    }
}
