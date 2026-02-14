/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.model.util

import kotlinx.datetime.TimeZone
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Awaits the latch for the given [Duration].
 *
 * @param timeout The maximum time to wait.
 * @return `true` if the count reached zero and `false` if the waiting time elapsed before the count reached zero.
 */
fun CountDownLatch.await(timeout: Duration): Boolean = this.await(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

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

/** Converts this [Instant] to a legacy [Date]. */
fun Instant.toDate(): Date = Date(this.toEpochMilliseconds())

/** Converts these milliseconds to an [Instant]. */
fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

/** Converts these seconds to an [Instant]. */
fun Int.secondsToInstant(): Instant = Instant.fromEpochSeconds(this.toLong())
