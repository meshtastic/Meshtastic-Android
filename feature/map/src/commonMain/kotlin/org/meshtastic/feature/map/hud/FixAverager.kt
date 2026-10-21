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

import org.meshtastic.core.common.util.latLongToMeter
import org.meshtastic.core.repository.PhoneLocation

private const val WINDOW_MS = 30_000L

/** Above this ground speed the user is walking, and old fixes describe somewhere they no longer are. */
private const val MOVING_MPS = 0.7f

/** A fix this many accuracy radii from the running mean is a real move, not noise. */
private const val JUMP_RADII = 3.0

/** No fix is trusted below this, so a reported zero neither divides by zero nor makes every wobble a jump. */
private const val MIN_ACCURACY_M = 1f

/** Weight for a fix that reports no accuracy, as if it were this many meters. */
private const val UNKNOWN_ACCURACY_M = 20f

/** Weight for a height that reports no vertical accuracy, as if it were this many meters: GPS height is the weaker. */
private const val UNKNOWN_VERTICAL_ACCURACY_M = 30f

/**
 * Steadies the phone's own position while it is held still: a mean of the last [WINDOW_MS] of fixes, each weighted by
 * the inverse square of its accuracy, restarted whenever the phone moves. Bearings to nodes a few tens of meters away
 * swing with every meter of GPS wander, so this is what keeps nearby chips from jumping.
 *
 * The reported accuracy is the best single fix's, not divided by the count: GPS error is correlated over this window,
 * so averaging steadies the position without proving it is that much closer to the truth.
 */
internal class FixAverager {
    private val fixes = ArrayDeque<PhoneLocation>()

    /** How many fixes the last result averaged. */
    var count: Int = 0
        private set

    fun add(fix: PhoneLocation): PhoneLocation {
        if (fixes.lastOrNull()?.timeMillis == fix.timeMillis && fixes.lastOrNull() == fix) return mean()
        val moving = (fix.speedMps ?: 0f) > MOVING_MPS
        val current = fixes.takeIf { it.isNotEmpty() }?.let { mean() }
        val jumped =
            current != null &&
                latLongToMeter(current.latitude, current.longitude, fix.latitude, fix.longitude) >
                JUMP_RADII * (fix.accuracyM ?: UNKNOWN_ACCURACY_M).coerceAtLeast(MIN_ACCURACY_M)
        if (moving || jumped) fixes.clear()
        fixes.addLast(fix)
        while (fixes.size > 1 && fix.timeMillis - fixes.first().timeMillis > WINDOW_MS) fixes.removeFirst()
        return mean()
    }

    private fun mean(): PhoneLocation {
        val newest = fixes.last()
        var weightSum = 0.0
        var lat = 0.0
        var lon = 0.0
        fixes.forEach { f ->
            val accuracy = (f.accuracyM ?: UNKNOWN_ACCURACY_M).coerceAtLeast(MIN_ACCURACY_M).toDouble()
            val w = 1 / (accuracy * accuracy)
            weightSum += w
            lat += w * f.latitude
            lon += w * f.longitude
        }
        count = fixes.size
        return newest.copy(
            latitude = lat / weightSum,
            longitude = lon / weightSum,
            accuracyM = fixes.mapNotNull { it.accuracyM }.minOrNull(),
            altitude = meanHeight { it.altitude },
            mslAltitude = meanHeight { it.mslAltitude },
            verticalAccuracyM = fixes.mapNotNull { it.verticalAccuracyM }.minOrNull(),
        )
    }

    /**
     * A height averaged like the position, each fix weighted by the inverse square of its vertical accuracy, over the
     * fixes that report it. GPS height wanders further than position, which is why it most needs the average.
     */
    private fun meanHeight(height: (PhoneLocation) -> Double?): Double? {
        var weightSum = 0.0
        var sum = 0.0
        fixes.forEach { f ->
            val h = height(f) ?: return@forEach
            val accuracy = (f.verticalAccuracyM ?: UNKNOWN_VERTICAL_ACCURACY_M).coerceAtLeast(MIN_ACCURACY_M).toDouble()
            val w = 1 / (accuracy * accuracy)
            weightSum += w
            sum += w * h
        }
        return if (weightSum > 0) sum / weightSum else null
    }
}
