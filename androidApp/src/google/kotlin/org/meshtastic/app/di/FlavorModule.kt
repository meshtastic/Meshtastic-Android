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
@file:Suppress("ktlint:standard:max-line-length")

package org.meshtastic.app.di

import com.datadog.android.okhttp.DatadogEventListener
import com.datadog.android.okhttp.DatadogInterceptor
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.app.map.prefs.di.GoogleMapsKoinModule
import org.meshtastic.app.theme.GoogleFontsEventFontResolver
import org.meshtastic.core.ui.theme.EventFontResolver
import org.meshtastic.feature.car.di.FeatureCarModule

@Module(
    includes = [GoogleMapsKoinModule::class, GoogleAiModule::class, AppFunctionsModule::class, FeatureCarModule::class],
)
class FlavorModule {
    /** Downloadable Google Fonts for event branding — Google flavor only. */
    @Single fun eventFontResolver(): EventFontResolver = GoogleFontsEventFontResolver()

    /**
     * Datadog network instrumentation for the shared Ktor `HttpClient`. The interceptor emits RUM Resource spans with
     * full DNS/connect/SSL/download timing for first-party (`meshtastic.org`) requests, and the event listener supplies
     * the fine-grained timing breakdown. Requires `Trace.enable(...)` (see `GooglePlatformAnalytics.initDatadog`).
     */
    @Single
    fun okHttpNetworkInstrumentation(): OkHttpNetworkInstrumentation = OkHttpNetworkInstrumentation(
        interceptors = listOf(DatadogInterceptor.Builder(tracedHosts = listOf("meshtastic.org")).build()),
        eventListenerFactory = DatadogEventListener.Factory(),
    )
}
