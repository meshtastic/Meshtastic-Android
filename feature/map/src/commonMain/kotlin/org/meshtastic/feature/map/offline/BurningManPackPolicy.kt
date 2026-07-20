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
package org.meshtastic.feature.map.offline

import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

data class PackLocation(val latitude: Double, val longitude: Double, val timestamp: Instant)

data class BurningManPackManifest(
    val packId: String,
    val sourceBuild: String,
    val installedAt: Instant,
    val userSuppressed: Boolean,
)

enum class PackAction {
    Install,
    Retain,
    Remove,
}

class BurningManPackPolicy(
    private val manifest: BurningManPackManifest? = null,
    private val installationLatched: Boolean = false,
) {

    fun reconcile(now: Instant, location: PackLocation?): PackAction {
        val recentLocation = location?.takeIf { it.timestamp >= now - locationMaxAge }
        return when {
            now >= noLocationCleanupDate -> PackAction.Remove

            now >= outsideAreaCleanupDate && recentLocation != null && !contains(recentLocation) ->
                PackAction.Remove

            recentLocation != null &&
                contains(recentLocation) &&
                manifest == null &&
                !installationLatched -> PackAction.Install

            else -> PackAction.Retain
        }
    }

    fun contains(location: PackLocation): Boolean =
        location.longitude in MIN_LON..MAX_LON && location.latitude in MIN_LAT..MAX_LAT

    private companion object {
        // Midnight in America/Los_Angeles is 07:00 UTC while daylight saving time is active.
        val outsideAreaCleanupDate = Instant.parse("2026-09-08T07:00:00Z")
        val noLocationCleanupDate = Instant.parse("2026-09-12T07:00:00Z")
        val locationMaxAge = 24.hours

        const val MIN_LON = -119.287957
        const val MIN_LAT = 40.722536
        const val MAX_LON = -119.128520
        const val MAX_LAT = 40.843420
    }
}
