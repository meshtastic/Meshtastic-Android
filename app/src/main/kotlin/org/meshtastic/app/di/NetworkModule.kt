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
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import coil3.util.Logger
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.meshtastic.core.common.BuildConfigProvider
import javax.inject.Singleton

private const val DISK_CACHE_PERCENT = 0.02
private const val MEMORY_CACHE_PERCENT = 0.25

@InstallIn(SingletonComponent::class)
@Module
interface NetworkModule {

    @Binds
    @Singleton
    fun bindMqttRepository(
        impl: org.meshtastic.core.network.repository.MQTTRepositoryImpl,
    ): org.meshtastic.core.network.repository.MQTTRepository

    companion object {
        @Provides
        @Singleton
        fun provideImageLoader(
            okHttpClient: OkHttpClient,
            @ApplicationContext application: Context,
            buildConfigProvider: BuildConfigProvider,
        ): ImageLoader {
            val sharedOkHttp = okHttpClient.newBuilder().build()
            return ImageLoader.Builder(context = application)
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { sharedOkHttp }))
                    add(SvgDecoder.Factory(scaleToDensity = true))
                }
                .memoryCache {
                    MemoryCache.Builder().maxSizePercent(context = application, percent = MEMORY_CACHE_PERCENT).build()
                }
                .diskCache { DiskCache.Builder().maxSizePercent(percent = DISK_CACHE_PERCENT).build() }
                .logger(
                    logger = if (buildConfigProvider.isDebug) DebugLogger(minLevel = Logger.Level.Verbose) else null,
                )
                .crossfade(enable = true)
                .build()
        }

        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        @Provides
        @Singleton
        fun provideHttpClient(okHttpClient: OkHttpClient, json: Json): HttpClient = HttpClient(engineFactory = OkHttp) {
            engine { preconfigured = okHttpClient }

            install(plugin = ContentNegotiation) { json(json) }
        }
    }
}
