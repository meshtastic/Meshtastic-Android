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
package org.meshtastic.core.data.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

// Real dispatchers + runBlocking, NOT runTest — the refresher's detached scope must interleave with the
// callers on real coroutine semantics, and Unconfined keeps the interleaving deterministic.
class SingleFlightRefresherTest {

    private class GatedWork {
        val gate = CompletableDeferred<Unit>()
        var starts = 0
        var completions = 0

        suspend fun run() {
            starts++
            gate.await()
            completions++
        }
    }

    @Test
    fun concurrentCallersShareOneFlight() = runBlocking {
        val work = GatedWork()
        val refresher = SingleFlightRefresher(Dispatchers.Unconfined, "test") { work.run() }

        val first = launch { refresher.refresh() }
        val second = launch { refresher.refresh() }
        withTimeout(2_000) { while (work.starts == 0) yield() }

        work.gate.complete(Unit)
        first.join()
        second.join()

        assertEquals(1, work.starts, "second caller joins the in-flight refresh instead of starting its own")
        assertEquals(1, work.completions)
    }

    @Test
    fun timedOutCallerFallsBackWhileRefreshRunsToCompletion() = runBlocking {
        val work = GatedWork()
        val refresher = SingleFlightRefresher(Dispatchers.Unconfined, "test") { work.run() }

        refresher.refresh(maxWaitMs = 50)

        assertEquals(1, work.starts)
        assertFalse(work.completions > 0, "caller returned before the refresh finished")

        work.gate.complete(Unit)
        withTimeout(2_000) { while (work.completions == 0) yield() }
        assertEquals(1, work.completions, "the refresh ran to completion after the caller stopped waiting")
    }

    @Test
    fun callerCancellationDoesNotAbortTheSharedRefresh() = runBlocking {
        val work = GatedWork()
        val refresher = SingleFlightRefresher(Dispatchers.Unconfined, "test") { work.run() }

        val caller = launch { refresher.refresh() }
        withTimeout(2_000) { while (work.starts == 0) yield() }
        caller.cancel()
        caller.join()

        work.gate.complete(Unit)
        withTimeout(2_000) { while (work.completions == 0) yield() }
        assertEquals(1, work.completions, "the detached refresh survives its caller's cancellation")
    }

    @Test
    fun failureIsContainedAndTheNextRefreshStartsANewFlight() = runBlocking {
        var attempts = 0
        val refresher =
            SingleFlightRefresher(Dispatchers.Unconfined, "test") {
                attempts++
                if (attempts == 1) error("network down")
            }

        refresher.refresh() // must not throw
        refresher.refresh()

        assertEquals(2, attempts, "a failed flight is not reused; the next caller starts a fresh one")
    }
}
