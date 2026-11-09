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
package org.meshtastic.core.common.util

import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * 2020-01-01: a boot-relative counter needs ~50 years of uptime to reach this, so it cannot be confused with a real
 * epoch.
 */
const val MIN_PLAUSIBLE_EPOCH_SECONDS = 1577836800

/** Accessor for the current time in milliseconds. */
val nowMillis: Long
    get() = nowInstant.toEpochMilliseconds()

/** Accessor for the current time as a stable [Instant]. */
val nowInstant: Instant
    get() = Clock.System.now()

/** Accessor for the current time in seconds. */
val nowSeconds: Long
    get() = nowInstant.epochSeconds

/** Accessor for the system default time zone. */
val systemTimeZone: TimeZone
    get() = TimeZone.currentSystemDefault()

/** Clamps a seconds-since-epoch timestamp so it never exceeds the current wall-clock time. */
fun clampTimestampToNow(epochSeconds: Int): Int = minOf(epochSeconds, nowSeconds.toInt())

/** Converts these milliseconds to an [Instant]. */
fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

/** Returns [seconds] when it is a plausible epoch no later than [maxValidTime], else null */
fun validSecondsOrNull(seconds: Int?, maxValidTime: Int): Int? = seconds?.takeIf {
    it in MIN_PLAUSIBLE_EPOCH_SECONDS..maxValidTime
}
