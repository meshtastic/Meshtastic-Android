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
package org.meshtastic.feature.map.survey

import org.meshtastic.core.model.SignalSample

private const val NODE_ID_DIGITS = 8
private const val HEX = 16

internal const val SURVEY_CSV_HEADER = "time_ms,latitude,longitude,accuracy_m,from,rssi,snr,direct"

/** One row per reading. Carries the phone's route, so it is only ever written where the user chooses. */
internal fun List<SignalSample>.toSurveyCsv(): String = buildString {
    appendLine(SURVEY_CSV_HEADER)
    this@toSurveyCsv.forEach { sample ->
        val from = "!" + sample.fromNum.toUInt().toString(HEX).padStart(NODE_ID_DIGITS, '0')
        appendLine(
            listOf(
                sample.timeMillis,
                sample.latitude,
                sample.longitude,
                sample.accuracyM ?: "",
                from,
                sample.rssi,
                sample.snr ?: "",
                sample.direct,
            )
                .joinToString(","),
        )
    }
}
