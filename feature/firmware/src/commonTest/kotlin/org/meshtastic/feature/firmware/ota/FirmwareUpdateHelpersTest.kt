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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.firmware.ota

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FirmwareUpdateHelpersTest {

    // ----- formatTransferProgress -----

    @Test
    fun `formatTransferProgress omits speed when throughput is zero`() {
        assertEquals("50%", formatTransferProgress(progress = 0.5f, totalBytes = 1000, bytesPerSecond = 0))
    }

    @Test
    fun `formatTransferProgress omits speed when throughput is non-positive`() {
        assertEquals("0%", formatTransferProgress(progress = 0f, totalBytes = 1000, bytesPerSecond = -5))
    }

    @Test
    fun `formatTransferProgress includes KiB per second and ETA`() {
        // 50% of 2048 bytes (1024 remaining) at 1024 B/s → 1.0 KiB/s, 1s ETA.
        assertEquals(
            "50% (1.0 KiB/s, ETA: 1s)",
            formatTransferProgress(progress = 0.5f, totalBytes = 2048, bytesPerSecond = 1024),
        )
    }

    // ----- retryWithDelay -----

    @Test
    fun `retryWithDelay returns first success without further attempts`() = runTest {
        var calls = 0
        val result =
            retryWithDelay(attempts = 3, retryDelayMillis = 1000, onAttempt = {}) {
                calls++
                Result.success("ok")
            }
        assertTrue(result.isSuccess)
        assertEquals("ok", result.getOrNull())
        assertEquals(1, calls)
    }

    @Test
    fun `retryWithDelay retries until success and reports each attempt`() = runTest {
        var calls = 0
        val attemptsSeen = mutableListOf<Int>()
        val result =
            retryWithDelay(attempts = 5, retryDelayMillis = 1000, onAttempt = { attemptsSeen += it }) {
                calls++
                if (calls < 3) Result.failure(RuntimeException("nope $calls")) else Result.success(calls)
            }
        assertEquals(3, result.getOrNull())
        assertEquals(3, calls)
        assertEquals(listOf(1, 2, 3), attemptsSeen)
    }

    @Test
    fun `retryWithDelay returns the last failure after exhausting attempts`() = runTest {
        var calls = 0
        val lastError = RuntimeException("final")
        val result =
            retryWithDelay<Unit>(attempts = 3, retryDelayMillis = 1000, onAttempt = {}) {
                calls++
                if (calls < 3) {
                    Result.failure<Unit>(RuntimeException("attempt $calls"))
                } else {
                    Result.failure<Unit>(lastError)
                }
            }
        assertTrue(result.isFailure)
        assertSame(lastError, result.exceptionOrNull())
        assertEquals(3, calls)
    }
}
