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

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Factory

/**
 * A helper class that manages a single [Job]. When a new job is launched, any previous job is cancelled. This is useful
 * for ensuring that only the latest operation of a certain type is running at a time (e.g. for search or settings
 * updates).
 */
@Factory
class SequentialJob {
    private val job = atomic<Job?>(null)

    /**
     * Cancels the previous job (if any) and launches a new one in the given [scope]. The new job uses [handledLaunch]
     * to ensure exceptions are reported.
     *
     * @param timeoutMs Optional timeout in milliseconds. If > 0, the [block] is wrapped in [withTimeout] so that
     *   indefinitely-suspended coroutines (e.g. blocked DataStore reads) throw [TimeoutCancellationException] instead
     *   of hanging silently.
     */
    fun launch(scope: CoroutineScope, timeoutMs: Long = 0, block: suspend CoroutineScope.() -> Unit) {
        cancel()
        val newJob =
            scope.handledLaunch {
                if (timeoutMs > 0) {
                    try {
                        withTimeout(timeoutMs, block)
                    } catch (e: TimeoutCancellationException) {
                        Logger.w { "SequentialJob timed out after ${timeoutMs}ms" }
                        throw e
                    }
                } else {
                    block()
                }
            }
        job.value = newJob

        newJob.invokeOnCompletion { job.compareAndSet(newJob, null) }
    }

    /** Cancels the current job. */
    fun cancel() {
        job.getAndSet(null)?.cancel()
    }
}
