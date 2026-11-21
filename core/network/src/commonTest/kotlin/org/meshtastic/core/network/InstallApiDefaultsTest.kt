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
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InstallApiDefaultsTest {
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(userAgent: String?, logRequests: Boolean = false) = HttpClient(MockEngine) {
        engine {
            addHandler { request ->
                requests += request
                respond("{}")
            }
        }
        installApiDefaults(Json, userAgent = userAgent, logRequests = logRequests)
    }

    @Test
    fun aRelativePathGoesToTheApiHost() = runTest {
        client(userAgent = null).use { it.get("resource/deviceHardware") }

        assertEquals("https://api.meshtastic.org/resource/deviceHardware", requests.single().url.toString())
    }

    @Test
    fun theHostsUserAgentIsSent() = runTest {
        client(userAgent = "Meshtastic-Test/1.0").use { it.get("resource/deviceHardware") }

        assertEquals("Meshtastic-Test/1.0", requests.single().headers[HttpHeaders.UserAgent])
    }

    @Test
    fun timeoutsAndRetryAreInstalled() {
        client(userAgent = null).use {
            assertNotNull(it.pluginOrNull(HttpTimeout))
            assertNotNull(it.pluginOrNull(HttpRequestRetry))
        }
    }

    @Test
    fun requestsAreLoggedOnlyWhenAsked() {
        client(userAgent = null).use { assertNull(it.pluginOrNull(Logging)) }
        client(userAgent = null, logRequests = true).use { assertNotNull(it.pluginOrNull(Logging)) }
    }
}
