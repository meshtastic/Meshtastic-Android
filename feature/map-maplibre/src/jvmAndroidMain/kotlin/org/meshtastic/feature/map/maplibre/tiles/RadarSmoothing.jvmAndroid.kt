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
package org.meshtastic.feature.map.maplibre.tiles

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceKind
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider
import org.maplibre.compose.resource.MapResourceRequest
import org.meshtastic.feature.map.tiles.RadarSmoothRequest
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private val logger = Logger.withTag("RadarSmoothing")

/** NOAA serves the radar with `max-age=120`; a smoothed tile is as fresh as the one it was cut from. */
private val RADAR_TILE_LIFETIME = 2.minutes

@OptIn(ExperimentalAtomicApi::class)
private val installed = AtomicBoolean(false)

@OptIn(ExperimentalAtomicApi::class)
internal actual fun installRadarSmoothing(client: HttpClient, renderContext: CoroutineContext) {
    if (!installed.compareAndSet(expectedValue = false, newValue = true)) return
    runCatching { DefaultMapRuntime.configure { resourceProvider = RadarResourceProvider(client, renderContext) } }
        .onFailure { logger.w(it) { "Could not install the radar resource provider" } }
}

/**
 * Serves the radar's tiles smoothed once a tile pixel is finer than a radar cell, and declines everything else so
 * MapLibre fetches it natively: other layers, other hosts, and radar tiles zoomed out far enough to need nothing.
 */
internal class RadarResourceProvider(private val client: HttpClient, private val renderContext: CoroutineContext) :
    MapResourceProvider {
    override fun accepts(request: MapResourceRequest): Boolean =
        request.kind == MapResourceKind.Tile && RadarSmoothRequest.from(request.url) != null

    override suspend fun load(request: MapResourceLoadRequest): MapResourceLoad {
        val smooth =
            RadarSmoothRequest.from(request.url) ?: return failed(MapResourceError.Other, "Not a smoothable radar tile")
        val bytes =
            try {
                smooth.render(renderContext, ::fetch)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.d(e) { "Radar tile render failed" }
                null
            }
        return if (bytes == null) {
            failed(MapResourceError.Connection, "Radar tile unavailable")
        } else {
            MapResourceLoad.Bytes(bytes = bytes, expires = Clock.System.now() + RADAR_TILE_LIFETIME)
        }
    }

    private suspend fun fetch(url: String): ByteArray? {
        val response = client.get(url)
        return if (response.status.isSuccess()) response.bodyAsBytes() else null
    }

    private fun failed(reason: MapResourceError, message: String) = MapResourceLoad.Failed(reason, message)
}
