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
package org.meshtastic.app.map.tiles

import android.content.Context
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import java.io.File

/** Roughly a few thousand tiles — enough that panning around a working area stops re-downloading. */
private const val TILE_CACHE_BYTES = 64L * 1024 * 1024

/**
 * An hour is long enough that scrolling never re-fetches, and short enough that a time-varying layer like weather radar
 * cannot go badly stale behind it.
 */
private const val FALLBACK_MAX_AGE_SECONDS = 60 * 60

/**
 * The client raster map tiles are fetched through, and the disk cache that makes panning cheap.
 *
 * Deliberately separate from the app's shared Ktor client: tiles would evict API responses from a shared cache, and
 * they want a far larger budget than anything else the app fetches.
 */
@Single
class MapTileHttpClient(private val context: Context, private val buildConfig: BuildConfigProvider) {

    /** Built on first use: nothing needs a tile cache until a raster source is actually selected. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "map-tiles"), TILE_CACHE_BYTES))
            .addNetworkInterceptor(FallbackCachePolicy)
            .addInterceptor(IdentifyingUserAgent(buildConfig))
            .build()
    }
}

/**
 * Names the app on every tile request.
 *
 * OpenStreetMap's tile usage policy requires an identifying User-Agent and blocks clients that send a generic library
 * default, which `okhttp/x.y` is. The OSMdroid map this replaces set the same requirement through its tile source
 * policy flags; nothing carried it over when the fetch moved to OkHttp.
 */
private class IdentifyingUserAgent(private val buildConfig: BuildConfigProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response = chain.proceed(
        chain
            .request()
            .newBuilder()
            .header("User-Agent", "Meshtastic-Android/${buildConfig.versionName} (${buildConfig.applicationId})")
            .build(),
    )
}

/**
 * Supplies a cache lifetime for tile servers that state none.
 *
 * The servers we ship all send `Cache-Control` or `Expires` and are left alone. A custom source the user typed in is
 * often a plain file server that sends neither, and OkHttp will not cache a response it was told nothing about — which
 * would leave exactly the wipe-while-scrolling this cache exists to fix.
 */
private object FallbackCachePolicy : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val response = chain.proceed(chain.request())
        val serverStatedPolicy = response.header("Cache-Control") != null || response.header("Expires") != null
        return if (serverStatedPolicy) {
            response
        } else {
            response.newBuilder().header("Cache-Control", "public, max-age=$FALLBACK_MAX_AGE_SECONDS").build()
        }
    }
}
