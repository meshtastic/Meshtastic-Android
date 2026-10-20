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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceModelRemoteDataSourceTest {
    private val dispatchers =
        CoroutineDispatchers(io = Dispatchers.Default, main = Dispatchers.Default, default = Dispatchers.Default)

    @Test
    fun modelUrlSwapsTheIllustrationExtension() {
        assertEquals(
            "https://flasher.meshtastic.org/models/devices/rak4631_case.glb",
            DeviceModelRemoteDataSource.modelUrl("rak4631_case.svg"),
        )
    }

    @Test
    fun aServedGlbExistsAndIsFetched() = runTest {
        val glb = byteArrayOf(0x67, 0x6C, 0x54, 0x46)
        withSource(respondWith = glb, contentType = "model/gltf-binary") { source ->
            assertEquals(true, source.exists("tracker-t1000-e.svg"))
            assertContentEquals(glb, source.fetch("tracker-t1000-e.svg"))
        }
    }

    @Test
    fun theFlashersHtmlShellForAMissingModelIsNotAModel() = runTest {
        withSource(
            respondWith = "<!doctype html>".encodeToByteArray(),
            contentType = "text/html;charset=utf-8",
        ) { source ->
            assertEquals(false, source.exists("t-deck.svg"))
            assertNull(source.fetch("t-deck.svg"))
        }
    }

    @Test
    fun aFailedRequestIsUnknownNotAbsent() = runTest {
        val client = HttpClient(MockEngine { throw IOException("offline") })
        try {
            val source = DeviceModelRemoteDataSource(client, dispatchers)
            assertNull(source.exists("t-deck.svg"))
            assertNull(source.fetch("t-deck.svg"))
        } finally {
            client.close()
        }
    }

    private suspend fun withSource(
        respondWith: ByteArray,
        contentType: String,
        block: suspend (DeviceModelRemoteDataSource) -> Unit,
    ) {
        val client =
            HttpClient(MockEngine { respond(respondWith, headers = headersOf(HttpHeaders.ContentType, contentType)) })
        try {
            block(DeviceModelRemoteDataSource(client, dispatchers))
        } finally {
            client.close()
        }
    }
}
