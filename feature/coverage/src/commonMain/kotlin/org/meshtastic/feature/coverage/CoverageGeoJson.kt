/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.meshtastic.feature.coverage

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The coverage as a GeoJSON FeatureCollection with simplestyle-spec properties — the same shape the
 * hosted planner's export produces, so the app's existing import path accepts it unchanged.
 */
fun Coverage.toGeoJson(): String {
    val features = reachable.joinToString(",\n") { p ->
        val color = when {
            p.rxDbm > site.rxSensitivityDbm + 30 -> "#67ea94"
            p.rxDbm > site.rxSensitivityDbm + 15 -> "#ffd166"
            else -> "#ef476f"
        }
        """    {"type":"Feature","geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]},""" +
            """"properties":{"rx_dbm":${p.rxDbm.toFixed1()},"marker-color":"$color"}}"""
    }
    return """{
  "type": "FeatureCollection",
  "properties": {"generator": "meshtastic-kp1812", "name": "${site.name}", "model": "ITU-R P.1812"},
  "features": [
$features
  ]
}
"""
}

/**
 * One decimal place, without `String.format` — which is JVM-only and does not exist on
 * Kotlin/Native or wasm. Adding the native targets is what surfaced that.
 */
internal fun Double.toFixed1(): String {
    val scaled = (this * 10).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    return "$sign${magnitude / 10}.${magnitude % 10}"
}
