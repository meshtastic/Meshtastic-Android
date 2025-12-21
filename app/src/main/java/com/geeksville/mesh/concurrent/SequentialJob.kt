/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.concurrent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 * A helper class that manages a single [Job].
 *
 * When a new job is launched, the previous one is cancelled. This is useful for ensuring that only one operation of a
 * certain type is running at a time.
 */
class SequentialJob @Inject constructor() {
    private val job = AtomicReference<Job?>(null)

    /**
     * Cancels the previous job (if any) and launches a new one in the given [scope].
     *
     * The new job uses [handledLaunch] to ensure exceptions are reported.
     */
    fun launch(scope: CoroutineScope, block: suspend CoroutineScope.() -> Unit) {
        cancel()
        val newJob = scope.handledLaunch(block = block)
        job.set(newJob)

        newJob.invokeOnCompletion { job.compareAndSet(newJob, null) }
    }

    /** Cancels the current job. */
    fun cancel() {
        job.getAndSet(null)?.cancel()
    }
}
