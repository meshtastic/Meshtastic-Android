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
package org.meshtastic.core.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** Runs a test with a structured child scope driven by the test scheduler and always cancels it afterward. */
fun runWithRenderScope(block: suspend TestScope.(CoroutineScope) -> Unit) = runTest {
    val renderScope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job(coroutineContext[Job]))
    try {
        block(renderScope)
    } finally {
        renderScope.cancel()
    }
}

private val SETTLE_POLL_INTERVAL = 5.milliseconds

/**
 * Runs the test scheduler until [isSettled] holds, waiting in real time between passes.
 *
 * Compose resources are loaded on an internal `Dispatchers.Default` scope, so state derived from a string resource
 * lands outside the test scheduler and cannot be observed by draining it.
 *
 * Not safe in a test that asserts on exact virtual time: waiting in real time suspends the test coroutine, and
 * `runTest` advances the virtual clock to the next scheduled task whenever that happens, firing pending `delay`s early.
 * Such a test should instead load the resources it needs before scheduling anything it later advances past.
 */
suspend fun TestScope.runUntilSettled(timeout: Duration = 10.seconds, isSettled: () -> Boolean) {
    val start = TimeSource.Monotonic.markNow()
    while (true) {
        runCurrent()
        if (isSettled()) return
        check(start.elapsedNow() < timeout) { "condition was still not settled after $timeout" }
        withContext(Dispatchers.Default) { delay(SETTLE_POLL_INTERVAL) }
    }
}
