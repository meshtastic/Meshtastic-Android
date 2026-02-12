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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TimeExtensionsTest {

    @Test
    fun testNowMillis() {
        val start = Clock.System.now().toEpochMilliseconds()
        val now = nowMillis
        val end = Clock.System.now().toEpochMilliseconds()
        assertTrue(now in start..end)
    }

    @Test
    fun testNowSeconds() {
        val start = Clock.System.now().epochSeconds
        val now = nowSeconds
        val end = Clock.System.now().epochSeconds
        assertTrue(now in start..end)
    }

    @Test
    fun testToDate() {
        val instant = Instant.fromEpochMilliseconds(1234567890L)
        val date = instant.toDate()
        assertEquals(1234567890L, date.time)
    }

    @Test
    fun testLongToInstant() {
        val millis = 1234567890L
        val instant = millis.toInstant()
        assertEquals(millis, instant.toEpochMilliseconds())
    }

    @Test
    fun testIntSecondsToInstant() {
        val seconds = 1234567890
        val instant = seconds.secondsToInstant()
        assertEquals(seconds.toLong(), instant.epochSeconds)
    }

    @Test
    fun testDurationInWholeSeconds() {
        assertEquals(60L, 60.seconds.inWholeSeconds)
        assertEquals(3600L, TimeConstants.ONE_HOUR.inWholeSeconds)
    }

    @Test
    fun testLongSecondsProperty() {
        assertEquals(60.seconds, 60L.seconds)
    }

    @Test
    fun testCountDownLatchAwaitWithDuration() {
        val latch = CountDownLatch(1)
        // This should timeout quickly
        val result = latch.await(10.milliseconds)
        assertEquals(false, result)

        val latch2 = CountDownLatch(1)
        latch2.countDown()
        val result2 = latch2.await(1.seconds)
        assertEquals(true, result2)
    }

    @Test
    fun testTimeZoneToPosixString() {
        val tz = TimeZone.of("UTC")
        assertEquals("UTC0", tz.toPosixString())
    }
}
