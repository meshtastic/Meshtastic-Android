/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.content.Context
import com.datadog.android.okhttp.DatadogEventListener
import com.datadog.android.okhttp.DatadogInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl
import java.io.File

@Module
class GoogleNetworkModule {

    @Single fun bindApiService(apiServiceImpl: ApiServiceImpl): ApiService = apiServiceImpl

    @Single
    fun provideOkHttpClient(context: Context, buildConfigProvider: BuildConfigProvider): OkHttpClient =
        OkHttpClient.Builder()
            .cache(
                cache =
                Cache(
                    directory = File(context.applicationContext.cacheDir, "http_cache"),
                    maxSize = 50L * 1024L * 1024L, // 50 MiB
                ),
            )
            .addInterceptor(
                interceptor =
                HttpLoggingInterceptor().apply {
                    if (buildConfigProvider.isDebug) {
                        setLevel(HttpLoggingInterceptor.Level.BODY)
                    }
                },
            )
            .addInterceptor(interceptor = DatadogInterceptor.Builder(tracedHosts = listOf("meshtastic.org")).build())
            .eventListenerFactory(eventListenerFactory = DatadogEventListener.Factory())
            .build()
}
