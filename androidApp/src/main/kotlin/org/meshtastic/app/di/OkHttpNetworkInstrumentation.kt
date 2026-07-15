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
package org.meshtastic.app.di

import okhttp3.EventListener
import okhttp3.Interceptor

/**
 * Flavor-provided OkHttp instrumentation applied to the shared Ktor [io.ktor.client.HttpClient] built in
 * [NetworkModule.provideHttpClient].
 *
 * The Android Ktor engine lives in the shared `src/main` source set, so it is compiled into both the google and fdroid
 * flavors. This holder is the seam that keeps analytics out of fdroid: the google `FlavorModule` supplies Datadog's
 * `DatadogInterceptor` + `DatadogEventListener.Factory` (so RUM captures per-request DNS/connect/SSL/download timing
 * for first-party hosts), while the fdroid `FlavorModule` supplies [NONE], pulling in no Datadog artifact at all.
 */
class OkHttpNetworkInstrumentation(
    val interceptors: List<Interceptor> = emptyList(),
    val eventListenerFactory: EventListener.Factory? = null,
) {
    companion object {
        /** No-op instrumentation — used by the analytics-free fdroid flavor. */
        val NONE = OkHttpNetworkInstrumentation()
    }
}
