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

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.memoryCacheMaxSizePercentWhileInBackground
import coil3.network.DeDupeConcurrentRequestStrategy
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import coil3.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.KermitHttpLogger

private const val DISK_CACHE_PERCENT = 0.02
private const val MEMORY_CACHE_PERCENT = 0.25
private const val MEMORY_CACHE_BACKGROUND_PERCENT = 0.1

@Module
class NetworkModule {

    @Single
    fun provideConnectivityManager(application: Application): ConnectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Single
    fun provideNsdManager(application: Application): NsdManager =
        application.getSystemService(Context.NSD_SERVICE) as NsdManager

    @OptIn(ExperimentalCoilApi::class)
    @Single
    fun provideImageLoader(
        httpClient: HttpClient,
        application: Context,
        buildConfigProvider: BuildConfigProvider,
    ): ImageLoader = ImageLoader.Builder(context = application)
        .components {
            add(
                KtorNetworkFetcherFactory(
                    httpClient = httpClient,
                    concurrentRequestStrategy = DeDupeConcurrentRequestStrategy(),
                ),
            )
            add(SvgDecoder.Factory(scaleToDensity = true))
        }
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context = application, percent = MEMORY_CACHE_PERCENT).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(application.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizePercent(percent = DISK_CACHE_PERCENT)
                .build()
        }
        .logger(logger = if (buildConfigProvider.isDebug) DebugLogger(minLevel = Logger.Level.Verbose) else null)
        .memoryCacheMaxSizePercentWhileInBackground(MEMORY_CACHE_BACKGROUND_PERCENT)
        .crossfade(enable = true)
        .build()

    @Single
    fun provideHttpClient(json: Json, buildConfigProvider: BuildConfigProvider): HttpClient =
        HttpClient(engineFactory = Android) {
            install(plugin = ContentNegotiation) { json(json) }
            install(DefaultRequest) { url(HttpClientDefaults.API_BASE_URL) }
            install(plugin = HttpTimeout) {
                requestTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
                socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            }
            install(plugin = HttpRequestRetry) {
                retryOnServerErrors(maxRetries = HttpClientDefaults.MAX_RETRIES)
                exponentialDelay()
            }
            if (buildConfigProvider.isDebug) {
                install(plugin = Logging) {
                    logger = KermitHttpLogger
                    level = LogLevel.BODY
                }
            }
        }
}
