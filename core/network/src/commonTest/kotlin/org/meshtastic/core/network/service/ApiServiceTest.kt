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
package org.meshtastic.core.network.service

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.meshtastic.core.model.FirmwareTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApiServiceTest {
    @Test
    fun `service decodes release manifest served as octet stream`() = runTest {
        val manifestUrl = "https://downloads.example/firmware/manifest.json"
        val engine = MockEngine { request ->
            assertEquals(manifestUrl, request.url.toString())
            respond(
                content =
                """
                    {
                      "version": "2.7.26.54e0d8d",
                      "targets": [
                        {"board": "t-deck", "platform": "esp32"}
                      ]
                    }
                    """
                    .trimIndent(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()),
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json() } }

        try {
            val manifest = ApiServiceImpl(client).getFirmwareReleaseManifest(manifestUrl)

            assertEquals("2.7.26.54e0d8d", manifest.version)
            assertEquals(listOf(FirmwareTarget(board = "t-deck", platform = "esp32")), manifest.targets)
        } finally {
            client.close()
        }
    }

    @Test
    fun `firmware release manifest decoder accepts release asset JSON and unknown fields`() {
        val manifest =
            decodeFirmwareReleaseManifest(
                """
                {
                  "version": "2.7.26.54e0d8d",
                  "targets": [
                    {"board": "t-deck", "platform": "esp32", "future": true}
                  ],
                  "unknown": "ignored"
                }
                """
                    .trimIndent(),
            )

        assertEquals("2.7.26.54e0d8d", manifest.version)
        assertEquals(listOf(FirmwareTarget(board = "t-deck", platform = "esp32")), manifest.targets)
    }

    @Test
    fun `firmware release manifest decoder coerces explicit nulls to defaults`() {
        val manifest =
            decodeFirmwareReleaseManifest(
                """
                {
                  "version": null,
                  "targets": [
                    {
                      "board": "t-deck",
                      "platform": null
                    }
                  ]
                }
                """
                    .trimIndent(),
            )

        assertEquals("", manifest.version)
        assertEquals(FirmwareTarget(board = "t-deck", platform = ""), manifest.targets.single())
    }

    @Test
    fun `firmware release manifest decoder rejects malformed JSON`() {
        assertFailsWith<SerializationException> { decodeFirmwareReleaseManifest("not-json") }
    }
}
