/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.network.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import coil3.util.Logger
import com.geeksville.mesh.network.BuildConfig
import com.geeksville.mesh.network.retrofit.ApiService
import com.geeksville.mesh.network.retrofit.NoOpApiService
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

private const val DISK_CACHE_PERCENT = 0.02
private const val MEMORY_CACHE_PERCENT = 0.25

@InstallIn(SingletonComponent::class)
@Module
class ApiModule {

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        return NoOpApiService()
    }

    @Provides
    @Singleton
    fun imageLoader(
        httpClient: OkHttpClient,
        @ApplicationContext application: Context,
    ): ImageLoader {
        val sharedOkHttp = httpClient.newBuilder().build()
        return ImageLoader.Builder(application)
            .components {
                add(
                    OkHttpNetworkFetcherFactory({ sharedOkHttp })
                )
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(application, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .maxSizePercent(DISK_CACHE_PERCENT)
                    .build()
            }
            .logger(if (BuildConfig.DEBUG) DebugLogger(Logger.Level.Verbose) else null)
            .crossfade(true)
            .build()
    }
}
