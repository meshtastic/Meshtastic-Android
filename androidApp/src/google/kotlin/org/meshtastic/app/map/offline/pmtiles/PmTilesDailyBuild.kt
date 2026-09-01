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
package org.meshtastic.app.map.offline.pmtiles

import co.touchlab.kermit.Logger
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Resolves the most recent Protomaps daily basemap build, the same public dataset the sibling iOS app slices its own
 * offline regions from (`Meshtastic/Helpers/Map/PMTilesExtractor.swift`).
 *
 * There is no "latest" alias — a build is published (usually) once a day at a date-stamped URL, and a given date's file
 * can be briefly missing around the daily publish window — so this probes backward a bounded number of days for the
 * first one that responds. A HEAD request only; the multi-gigabyte body is never touched here, only later, one tile's
 * worth of bytes at a time, by [ch.poole.geo.pmtiles.Reader] against whichever URL this resolves to.
 */
internal object PmTilesDailyBuild {

    private val LOG = Logger.withTag("PmTilesDailyBuild")
    private const val BASE_URL = "https://build.protomaps.com"
    private const val MAX_DAYS_BACK = 16
    private const val HEAD_TIMEOUT_MS = 8_000
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** The newest daily build URL that actually exists, or null if none of the last [MAX_DAYS_BACK] days do. */
    fun resolveLatestUrl(today: LocalDate = LocalDate.now(ZoneOffset.UTC)): URL? {
        for (daysBack in 0 until MAX_DAYS_BACK) {
            val candidate = urlFor(today.minusDays(daysBack.toLong()))
            if (exists(candidate)) return candidate
        }
        LOG.w { "No Protomaps daily build found in the last $MAX_DAYS_BACK days" }
        return null
    }

    private fun urlFor(date: LocalDate): URL = URL("$BASE_URL/${DATE_FORMAT.format(date)}.pmtiles")

    private fun exists(url: URL): Boolean {
        val connection = url.openConnection() as? HttpURLConnection ?: return false
        return try {
            connection.requestMethod = "HEAD"
            connection.connectTimeout = HEAD_TIMEOUT_MS
            connection.readTimeout = HEAD_TIMEOUT_MS
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (e: java.io.IOException) {
            LOG.d(e) { "Probe failed for $url" }
            false
        } finally {
            connection.disconnect()
        }
    }
}
