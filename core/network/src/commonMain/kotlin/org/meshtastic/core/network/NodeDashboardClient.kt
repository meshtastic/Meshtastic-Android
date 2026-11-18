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

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.koin.core.annotation.Single

/**
 * A node-kmp host's dashboard API, `/api/node/v1/`, at [base], the origin of its phone link (`http(s)://host:port`). A
 * browser page the dashboard served sends its sign-in cookie with every request to its own origin.
 */
@Single
class NodeDashboardClient {
    // Made on first use, from the engine the platform ships.
    private val client by lazy { HttpClient() }

    /** The body of `GET` [path] under the API, or null on any failure. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun get(base: String, path: String): String? = try {
        val response = client.get("$base$PREFIX$path")
        if (response.status.isSuccess()) response.bodyAsText() else null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.d(e) { "Dashboard GET $path failed" }
        null
    }

    /** `POST` [json] to [path] under the API; true when the host took it. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun post(base: String, path: String, json: String): Boolean = try {
        client
            .post("$base$PREFIX$path") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            .status
            .isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e) { "Dashboard POST $path failed" }
        false
    }

    /** `PUT` [json] to [path] under the API: the status code and body, or null when nothing came back. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun putForReply(base: String, path: String, json: String): Pair<Int, String>? = try {
        val response =
            client.put("$base$PREFIX$path") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
        response.status.value to response.bodyAsText()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e) { "Dashboard PUT $path failed" }
        null
    }

    /** `PUT` [json] to [path] under the API; true when the host took it. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun put(base: String, path: String, json: String): Boolean = try {
        client
            .put("$base$PREFIX$path") {
                contentType(ContentType.Application.Json)
                setBody(json)
            }
            .status
            .isSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e) { "Dashboard PUT $path failed" }
        false
    }

    companion object {
        const val PREFIX = "/api/node/v1/"
        private const val PHONE_PATH = "/api/node/v1/phone"

        /**
         * The dashboard origin a WebSocket radio [address] reaches, `w` then `ws(s)://host/api/node/v1/phone…`, as
         * `http(s)://host`; null for any other address, a firmware `/api/v1/ws` included.
         */
        fun baseOf(address: String?): String? {
            val origin =
                address?.takeIf { it.startsWith('w') }?.drop(1)?.substringBefore(PHONE_PATH, missingDelimiterValue = "")
            return when {
                origin.isNullOrEmpty() -> null
                origin.startsWith("wss://") -> "https://" + origin.removePrefix("wss://")
                origin.startsWith("ws://") -> "http://" + origin.removePrefix("ws://")
                else -> null
            }
        }
    }
}
