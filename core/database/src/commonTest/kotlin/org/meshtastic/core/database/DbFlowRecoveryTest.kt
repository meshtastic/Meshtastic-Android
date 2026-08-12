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
package org.meshtastic.core.database

import app.cash.turbine.test
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies the restart contract that keeps process-lifetime eager StateFlows alive across a Room pool wedge (#6608): a
 * terminal acquire-timeout must restart the upstream, and an unrelated failure must still terminate it.
 */
class DbFlowRecoveryTest {

    private fun poolTimeout() =
        IllegalStateException("Timed out attempting to acquire a reader connection from the database pool")

    @Test
    fun poolTimeoutRestartsTheUpstreamAndEmitsAfterRecovery() = runTest {
        var attempts = 0
        flow {
            attempts += 1
            if (attempts < 3) throw poolTimeout()
            emit("nodes")
        }
            .retryOnDbPoolFailure("test")
            .test(timeout = EMISSION_TIMEOUT) {
                assertEquals("nodes", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

        assertEquals(3, attempts, "each recoverable pool failure must restart the upstream")
    }

    /**
     * A closed pool must NOT be retried. `observeCurrentDb` re-latches on the replacement by itself, so retrying adds
     * nothing — and it would keep a flow alive that used to terminate, leaving a backoff loop running inside a scope
     * that has already gone away.
     */
    @Test
    fun closedPoolFailureIsNotRetried() = runTest {
        var attempts = 0
        flow<Int> {
            attempts += 1
            throw IllegalStateException("Connection pool is closed")
        }
            .retryOnDbPoolFailure("test")
            .test(timeout = EMISSION_TIMEOUT) { assertEquals("Connection pool is closed", awaitError().message) }

        assertEquals(1, attempts, "a closed pool is handled by re-latching, not by retrying")
    }

    @Test
    fun stalledFlowIsRestarted() = runTest {
        var attempts = 0
        flow {
            attempts += 1
            if (attempts == 1) throw DbFlowStalledException("no first emission")
            emit(1)
        }
            .retryOnDbPoolFailure("test")
            .test(timeout = EMISSION_TIMEOUT) {
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

        assertEquals(2, attempts)
    }

    @Test
    fun unrelatedFailuresPropagate() = runTest {
        var attempts = 0
        flow<Int> {
            attempts += 1
            throw IllegalStateException("no such column: bogus")
        }
            .retryOnDbPoolFailure("test")
            .test(timeout = EMISSION_TIMEOUT) { assertEquals("no such column: bogus", awaitError().message) }

        assertEquals(1, attempts, "a non-pool failure must not be retried")
    }

    @Test
    fun backoffResetsAfterASuccessfulEmission() = runTest {
        var attempts = 0
        flow {
            attempts += 1
            emit(attempts)
            throw poolTimeout()
        }
            .retryOnDbPoolFailure("test")
            .test(timeout = EMISSION_TIMEOUT) {
                assertEquals(listOf(1, 2, 3, 4), List(4) { awaitItem() })
                cancelAndIgnoreRemainingEvents()
            }

        // With the backoff reset, four emissions cost four one-second delays rather than exponential growth.
        assertTrue(testScheduler.currentTime <= 4_000L, "successful emissions must reset the retry backoff")
    }

    private companion object {
        // Turbine awaits run on the test scheduler's virtual clock, which the retry backoff also advances; this bound
        // must therefore exceed the capped 30s backoff rather than any wall-clock expectation.
        val EMISSION_TIMEOUT = 120.seconds
    }
}
