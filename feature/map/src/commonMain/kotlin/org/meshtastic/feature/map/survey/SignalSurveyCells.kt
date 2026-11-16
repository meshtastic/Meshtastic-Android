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
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.core.ui.component.RSSI_BAD_THRESHOLD
import org.meshtastic.core.ui.component.RSSI_FAIR_THRESHOLD
import org.meshtastic.core.ui.component.RSSI_GOOD_THRESHOLD
import org.meshtastic.core.ui.component.determineSignalQuality
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow

internal const val SURVEY_CELL_METERS = 50.0
private const val MAX_CELL_DOUBLINGS = 8
private const val METERS_PER_DEGREE = 111_320.0
private const val DEGREES_PER_HALF_TURN = 180.0
private const val CELL_CENTER = 0.5

// Near a pole a degree of longitude shrinks toward nothing; the floor keeps a cell from spanning the whole map.
private const val MIN_LONGITUDE_SCALE = 0.01

// Web Mercator ground resolution at the equator for zoom 0 with 256 px tiles.
private const val METERS_PER_PIXEL_AT_ZOOM_0 = 156_543.03392

// A square narrower than this is too small to see or tap.
private const val MIN_CELL_PIXELS = 14.0

/** One grid square of a survey, standing for the clearest reading taken inside it. */
data class SurveyCell(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    /** SNR of the clearest reading, or null when no reading in the cell reported one. */
    val bestSnr: Float?,
    /** RSSI of that same reading, so the pair describes one reception. */
    val rssi: Int,
    val samples: Int,
    val senders: Int,
    val lastHeardMillis: Long,
    /** The clearest direct reception from each node heard without a relay here, by node number. */
    val direct: Map<Int, SurveyReception> = emptyMap(),
)

/** One reception's signal, the pair a quality rating is made from. */
data class SurveyReception(val snr: Float?, val rssi: Int)

/** A reading with no SNR sorts below every reading that has one; RSSI breaks the tie. */
private val ClearestFirst = compareBy<SignalSample, Float?>(nullsFirst()) { it.snr }.thenBy { it.rssi }

/**
 * Bins readings into squares about [cellMeters] on a side. A cell shows its clearest reading: one good reception proves
 * the spot is reachable, and a weak one beside it is usually a different sender.
 */
internal fun List<SignalSample>.toSurveyCells(cellMeters: Double = SURVEY_CELL_METERS): List<SurveyCell> {
    val latStep = cellMeters / METERS_PER_DEGREE
    return groupBy { sample ->
        val row = floor(sample.latitude / latStep).toLong()
        row to floor(sample.longitude / lonStep(row, latStep)).toLong()
    }
        .map { (cell, readings) ->
            val (row, column) = cell
            val lonStep = lonStep(row, latStep)
            val clearest = readings.maxWith(ClearestFirst)
            SurveyCell(
                south = row * latStep,
                west = column * lonStep,
                north = (row + 1) * latStep,
                east = (column + 1) * lonStep,
                bestSnr = clearest.snr,
                rssi = clearest.rssi,
                samples = readings.size,
                senders = readings.distinctBy { it.fromNum }.size,
                lastHeardMillis = readings.maxOf { it.timeMillis },
                // A relayed copy carries the relay's signal, not the sender's, so only direct readings say who covers
                // here.
                direct =
                readings
                    .filter { it.direct }
                    .groupBy { it.fromNum }
                    .mapValues { (_, own) -> own.maxWith(ClearestFirst).let { SurveyReception(it.snr, it.rssi) } },
            )
        }
}

/** Longitude width of a cell in [row]. Derived from the row alone, so every cell in a row lines up. */
private fun lonStep(row: Long, latStep: Double): Double {
    val centerLatitude = (row + CELL_CENTER) * latStep
    val scale = cos(centerLatitude * PI / DEGREES_PER_HALF_TURN).coerceAtLeast(MIN_LONGITUDE_SCALE)
    return latStep / scale
}

/**
 * The cell size to draw at [zoom], counted in 256 px tiles as Google Maps does (MapLibre's zoom is one less). Cells
 * double in size as the map zooms out, so a survey stays visible at city scale and never draws finer than 50 m.
 */
internal fun surveyCellMeters(zoom: Double, latitude: Double): Double {
    val scale = cos(latitude * PI / DEGREES_PER_HALF_TURN).coerceAtLeast(MIN_LONGITUDE_SCALE)
    val metersPerPixel = METERS_PER_PIXEL_AT_ZOOM_0 * scale / 2.0.pow(zoom)
    val doublings = ceil(log2(MIN_CELL_PIXELS * metersPerPixel / SURVEY_CELL_METERS)).toInt()
    return SURVEY_CELL_METERS * 2.0.pow(doublings.coerceIn(0, MAX_CELL_DOUBLINGS))
}

/**
 * How good the cell's clearest reading is. SNR against the session's [preset] is the app's own signal rating; RSSI
 * alone stands in only when no reading reported an SNR.
 */
internal fun SurveyCell.quality(preset: ModemPreset?): Quality = SurveyReception(bestSnr, rssi).quality(preset)

/** The same rating for one reception. */
internal fun SurveyReception.quality(preset: ModemPreset?): Quality {
    val measured = snr
    return when {
        measured != null -> determineSignalQuality(measured, preset, rssi)
        rssi > RSSI_GOOD_THRESHOLD -> Quality.GOOD
        rssi > RSSI_FAIR_THRESHOLD -> Quality.FAIR
        rssi > RSSI_BAD_THRESHOLD -> Quality.BAD
        else -> Quality.NONE
    }
}

/** The preset a session recorded by name, or null for custom LoRa settings and names this build does not know. */
internal fun modemPresetNamed(name: String?): ModemPreset? = ModemPreset.entries.firstOrNull { it.name == name }
