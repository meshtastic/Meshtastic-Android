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
package org.meshtastic.feature.connections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.allStringResources
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.getStringSuspend
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertNull

private const val RUN_SECONDS = 10L
private const val BACKGROUND_READERS = 4
private const val STALL_SECONDS = 5L
private const val DRAIN_SECONDS = 10L

/**
 * A suspending lookup on the main dispatcher must never stall a blocking lookup on the main thread, the way
 * `stringResource` blocks a composition. Here one thread plays the main thread: it runs a coroutine that calls
 * [getStringSuspend] and, between that coroutine's turns, blocks in [getString]. The run is bounded by time, not
 * rounds, because a lookup's cost depends on the host: on Linux each one reads the system theme over D-Bus.
 */
class StringLookupMainThreadTest {

    private val strings: List<StringResource> = Res.allStringResources.values.take(64)

    @Test
    fun `a suspending lookup on the main dispatcher never stalls a blocking lookup on the main thread`() {
        val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "fake-main").apply { isDaemon = true } }
        val scope = CoroutineScope(SupervisorJob())
        var stalledAtRound: Int? = null
        try {
            // Readers on other threads keep the cache lock contended, so the main-dispatched lookup has to queue.
            repeat(BACKGROUND_READERS) { reader ->
                scope.launch(Dispatchers.Default) {
                    var i = reader
                    while (isActive) getStringSuspend(strings[i++ % strings.size])
                }
            }
            scope.launch(mainExecutor.asCoroutineDispatcher()) {
                var i = 0
                while (isActive) {
                    getStringSuspend(strings[i++ % strings.size])
                    yield()
                }
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RUN_SECONDS)
            var round = 0
            while (System.nanoTime() < deadline) {
                val string = strings[round % strings.size]
                val blockingLookup = mainExecutor.submit { getString(string) }
                try {
                    blockingLookup.get(STALL_SECONDS, TimeUnit.SECONDS)
                } catch (_: TimeoutException) {
                    stalledAtRound = round
                    // Interrupting frees the thread so the executor can drain.
                    blockingLookup.cancel(true)
                    break
                }
                round++
            }
        } finally {
            scope.cancel()
            mainExecutor.shutdown()
            mainExecutor.awaitTermination(DRAIN_SECONDS, TimeUnit.SECONDS)
        }
        assertNull(stalledAtRound, "the main thread stalled in a blocking string lookup")
    }
}
