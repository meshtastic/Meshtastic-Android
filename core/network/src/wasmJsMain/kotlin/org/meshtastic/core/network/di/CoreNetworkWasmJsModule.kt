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
package org.meshtastic.core.network.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.configureDefaultRetry
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl

/**
 * wasmJs (browser) DI module. Deliberately **no** `@ComponentScan` here: `CoreNetworkModule` (commonMain) already scans
 * `org.meshtastic.core.network` and is compiled into the wasmJs target, so it already reaches
 * [WebNetworkMonitor][org.meshtastic.core.network.repository.WebNetworkMonitor] and
 * [WasmJsRadioTransportFactory][org.meshtastic.core.network.radio.WasmJsRadioTransportFactory]'s `@Single` annotations
 * there — scanning again here would double-register them (unlike `core:ble`'s wasmJs module, whose counterpart module
 * lives in a mutually exclusive `nonWebMain`).
 *
 * Unregistered anywhere yet — no `webApp` module exists this pass to wire it into, the same state
 * `CorePrefsWasmJsModule`/`core:database`'s `SingleDatabaseProvider` are in. Putting the `HttpClient` engine here
 * (rather than in the host app, as Android/Desktop do via their own `NetworkModule`/`DesktopKoinModule`) is a
 * deliberate deviation, since no such host app exists yet; a future `webApp` may relocate it there instead.
 */
@Module
class CoreNetworkWasmJsModule {

    @Single fun bindApiService(apiServiceImpl: ApiServiceImpl): ApiService = apiServiceImpl

    /** Js engine — the same one `mqtt-client-transport-ws`'s own wasmJs actual uses for its WebSocket client. */
    @Single
    fun provideHttpClient(json: Json): HttpClient = HttpClient(Js) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) { url(HttpClientDefaults.API_BASE_URL) }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpClientDefaults.REQUEST_TIMEOUT_MS
            connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
        }
        install(HttpRequestRetry) { configureDefaultRetry() }
    }
}
