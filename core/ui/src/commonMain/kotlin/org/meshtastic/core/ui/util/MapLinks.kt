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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs
import kotlin.math.roundToLong

private const val OSM_ZOOM = 17
private const val MICRODEGREES = 1_000_000L
private const val FRACTION_DIGITS = 6

/** An OpenStreetMap page centred on the point, with a marker on it, at the zoom Android's `geo:` intent asks for. */
fun openStreetMapUrl(latitude: Double, longitude: Double): String {
    val lat = latitude.toUrlDegrees()
    val lon = longitude.toUrlDegrees()
    return "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=$OSM_ZOOM/$lat/$lon"
}

/** Fixed six-decimal degrees: `Double.toString` turns small values into exponent form, which a URL cannot carry. */
internal fun Double.toUrlDegrees(): String {
    val micro = (abs(this) * MICRODEGREES).roundToLong()
    val sign = if (this < 0 && micro != 0L) "-" else ""
    val fraction = (micro % MICRODEGREES).toString().padStart(FRACTION_DIGITS, '0')
    return "$sign${micro / MICRODEGREES}.$fraction"
}

/**
 * Opens a position on OpenStreetMap in the browser, for platforms with no map app to hand it to. An OSM link carries no
 * label, so the label is dropped.
 */
@Composable
internal fun rememberOpenMapInBrowser(): (latitude: Double, longitude: Double, label: String) -> Unit {
    val openUrl = rememberOpenUrl()
    return remember(openUrl) { { latitude, longitude, _ -> openUrl(openStreetMapUrl(latitude, longitude)) } }
}
