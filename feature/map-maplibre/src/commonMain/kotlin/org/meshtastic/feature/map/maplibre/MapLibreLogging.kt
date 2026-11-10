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
package org.meshtastic.feature.map.maplibre

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import org.maplibre.compose.logging.MapLogLevel
import org.maplibre.compose.logging.MapLogRecord
import org.maplibre.compose.logging.MapLogger
import org.maplibre.compose.logging.MapLogging
import org.maplibre.compose.util.DelicateMaplibreComposeApi

/**
 * Routes maplibre-compose's log through Kermit. Its default logger prints to the platform console, which the packaged
 * desktop app does not have, so a swallowed map failure never reached the Debug screen export.
 */
// The library documents the setter as process-wide and read on every log call; it is set once at startup.
@OptIn(DelicateMaplibreComposeApi::class)
fun installMapLibreLogging(logger: Logger = Logger.withTag(MAP_LOG_TAG)) {
    MapLogging.logger = KermitMapLogger(logger)
}

internal const val MAP_LOG_TAG = "MapLibre"

internal class KermitMapLogger(private val logger: Logger) : MapLogger {
    override fun log(record: MapLogRecord) {
        logger.log(
            record.level.toSeverity(),
            logger.tag,
            record.throwable,
            mapLogMessage(record.category, record.message),
        )
    }
}

internal fun mapLogMessage(category: String?, message: String): String = category?.let { "[$it] $message" } ?: message

internal fun MapLogLevel.toSeverity(): Severity = when (this) {
    MapLogLevel.Debug -> Severity.Debug
    MapLogLevel.Info -> Severity.Info
    MapLogLevel.Warning -> Severity.Warn
    MapLogLevel.Error -> Severity.Error
    else -> Severity.Info
}
