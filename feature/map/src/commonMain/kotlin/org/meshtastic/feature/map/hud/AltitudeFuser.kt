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
package org.meshtastic.feature.map.hud

import org.meshtastic.core.repository.PhoneLocation
import org.meshtastic.core.repository.PhoneLocationState
import kotlin.math.abs
import kotlin.math.pow

/** Standard-atmosphere sea-level pressure; only differences in the altitude it gives are used, never its level. */
private const val SEA_LEVEL_HPA = 1013.25
private const val ATMOSPHERE_SCALE_M = 44_330.0
private const val ATMOSPHERE_EXPONENT = 1 / 5.255

/** How long GPS takes to pull the fused height onto its own, in seconds: long against wander, short against weather. */
private const val GPS_TIME_CONSTANT_S = 60.0
private const val MILLIS_PER_SECOND = 1_000.0

/** A fused height must move this far before the view takes it, so barometer noise rebuilds nothing. */
private const val HEIGHT_STEP_M = 0.25

/**
 * True when [new] is [old] again as far as the view cares: the same fix, its heights within [HEIGHT_STEP_M]. Compared
 * against the last state the view took, so a slow climb still passes once it adds up.
 */
internal fun sameFix(old: PhoneLocationState, new: PhoneLocationState): Boolean {
    val a = old.location
    val b = new.location
    if (a == null || b == null) return a == b
    fun close(x: Double?, y: Double?) = if (x == null || y == null) x == y else abs(x - y) < HEIGHT_STEP_M
    return a.copy(altitude = null, mslAltitude = null) == b.copy(altitude = null, mslAltitude = null) &&
        close(a.altitude, b.altitude) &&
        close(a.mslAltitude, b.mslAltitude)
}

/** Height from pressure under the standard atmosphere, as Android's `SensorManager.getAltitude` computes it. */
internal fun pressureAltitudeM(hPa: Float): Double =
    ATMOSPHERE_SCALE_M * (1 - (hPa / SEA_LEVEL_HPA).pow(ATMOSPHERE_EXPONENT))

/**
 * The phone's height from GPS and the barometer together, the way a fused altimeter does it. The barometer feels a step
 * up a stair at once and holds still while standing, but its level drifts with the weather; GPS knows the level but
 * wanders by many meters from fix to fix. So the barometer gives the changes and GPS, averaged over a minute, keeps the
 * level: the fused height is the pressure altitude plus an offset that leans toward GPS a little with each fix.
 *
 * Without a barometer reading the fix passes through as GPS gave it. A fix without a GPS height, once the level is set,
 * keeps the barometer's height on the last level rather than losing the height altogether.
 */
internal class AltitudeFuser {
    private var baroM: Double? = null
    private var offsetM: Double? = null
    private var lastFixMillis: Long? = null

    /** How far the ellipsoid height sits above the sea-level one here, from the last fix that gave both. */
    private var geoidSeparationM: Double? = null

    fun onPressure(hPa: Float) {
        baroM = pressureAltitudeM(hPa)
    }

    /** [fix] with its heights moved onto the fused one; the sea-level height moves with it, the geoid being fixed. */
    fun fuse(fix: PhoneLocation): PhoneLocation {
        val baro = baroM
        val gps = fix.altitude
        return when {
            baro == null -> fix
            gps == null -> held(fix, baro)
            else -> calibrated(fix, baro, gps)
        }
    }

    /** A fix with no GPS height: the barometer's height on the last level, or the fix as it is before any level. */
    private fun held(fix: PhoneLocation, baro: Double): PhoneLocation {
        val height = offsetM?.let { baro + it } ?: return fix
        return fix.copy(altitude = height, mslAltitude = geoidSeparationM?.let { height - it } ?: fix.mslAltitude)
    }

    private fun calibrated(fix: PhoneLocation, baro: Double, gps: Double): PhoneLocation {
        fix.mslAltitude?.let { geoidSeparationM = gps - it }
        val target = gps - baro
        val offset = offsetM
        val previous = lastFixMillis
        offsetM =
            when {
                offset == null || previous == null -> target

                fix.timeMillis > previous -> {
                    val dt = (fix.timeMillis - previous) / MILLIS_PER_SECOND
                    offset + (target - offset) * dt / (GPS_TIME_CONSTANT_S + dt)
                }

                else -> offset
            }
        lastFixMillis = maxOf(fix.timeMillis, previous ?: fix.timeMillis)
        val fused = baro + (offsetM ?: target)
        return fix.copy(altitude = fused, mslAltitude = fix.mslAltitude?.let { it + (fused - gps) })
    }
}
