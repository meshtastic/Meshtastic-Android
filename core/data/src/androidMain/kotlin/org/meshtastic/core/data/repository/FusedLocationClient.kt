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
package org.meshtastic.core.data.repository

import android.location.Location
import java.util.concurrent.Executor

/**
 * Play services' fused location provider, which a flavor that ships Play services binds. It fixes faster than the
 * platform's providers and indoors from Wi-Fi. [NONE] is the F-Droid binding, and a phone without a working Play
 * services answers null too, so [LocationRepositoryImpl] falls back to the platform's providers.
 */
fun interface FusedLocationClient {
    /**
     * Deliver a fix to [listener] on [executor] about every [intervalMillis] at high accuracy, and return what stops
     * them, or null when Play services cannot serve. Registration completes later, so a request Play services refuse
     * after this returns reaches [onFailure] on [executor]. Throws `SecurityException` without a location grant.
     */
    fun requestLocationUpdates(
        intervalMillis: Long,
        executor: Executor,
        listener: (Location) -> Unit,
        onFailure: (Exception) -> Unit,
    ): AutoCloseable?

    companion object {
        /** No Play services: the platform's providers serve. */
        val NONE: FusedLocationClient = FusedLocationClient { _, _, _, _ -> null }
    }
}
