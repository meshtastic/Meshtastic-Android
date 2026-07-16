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
package org.meshtastic.feature.map.mapcompose.tile

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.io.Buffer
import kotlinx.io.RawSource
import ovh.plrapps.mapcompose.core.TileStreamProvider

/**
 * MapCompose [TileStreamProvider] that serves tiles from [TileDiskCache] first and the network second (write-through).
 *
 * kotlinx-io types ([RawSource], [Buffer]) appear here because they are MapCompose's API surface; this file is the one
 * accepted exception to the "Okio in commonMain" rule — everything else in the tile pipeline is Okio.
 *
 * Per the MapCompose contract, exceptions thrown from `getTileStream` are unrecoverable for the whole map, so every
 * failure path here is swallowed into a `null` return: the tile is skipped and the map stays interactive.
 */
class MeshTileStreamProvider(
    private val source: TileSource,
    private val cache: TileDiskCache,
    private val client: HttpClient,
    private val userAgent: String,
) : TileStreamProvider {

    @Suppress("TooGenericExceptionCaught")
    override suspend fun getTileStream(row: Int, col: Int, zoomLvl: Int): RawSource? = try {
        val cached = cache.read(source.id, zoomLvl, row, col)
        val bytes = cached ?: fetch(row, col, zoomLvl)?.also { cache.write(source.id, zoomLvl, row, col, it) }
        bytes?.let { Buffer().apply { write(it) } }
    } catch (e: Exception) {
        Logger.d { "Tile ${source.id}/$zoomLvl/$row/$col failed: ${e.message}" }
        null
    }

    private suspend fun fetch(row: Int, col: Int, zoomLvl: Int): ByteArray? {
        // OSMF tile policy requires a meaningful User-Agent identifying the application.
        val response: HttpResponse =
            client.get(source.tileUrl(zoomLvl, row, col)) { header(HttpHeaders.UserAgent, userAgent) }
        return if (response.status.isSuccess()) response.body() else null
    }
}
