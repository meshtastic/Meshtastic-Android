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
package org.meshtastic.feature.wifiprovision.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * Owns the lifetime of everything started for one connection attempt: the scan or connect, and every network scan and
 * credentials request after it, all launched in [scope]. [restart] and [end] cancel that whole generation at once, so
 * nothing from an earlier connection can write state after the screen has moved on, and the [service] of a replaced
 * generation is closed once its coroutines have stopped.
 *
 * Call from the thread the ViewModel runs on; [parent] is its scope and [applicationScope] outlives it.
 */
internal class ConnectionSession(private val parent: CoroutineScope, private val applicationScope: CoroutineScope) {
    private var job: Job = newJob()
    private val replaced = mutableListOf<Pair<Job, NymeaWifiService?>>()

    /** The service of the current generation, or null before one is connected and after [end]. */
    var service: NymeaWifiService? = null
        private set

    /** The scope every operation of the current generation runs in. */
    var scope: CoroutineScope = parent + job
        private set

    /** Cancels the current generation and starts the next one, with [replacement] as its service. */
    fun restart(replacement: NymeaWifiService? = null): CoroutineScope {
        job.cancel()
        replaced += job to service
        job = newJob()
        service = replacement
        scope = parent + job
        return scope
    }

    /** Cancels the current generation and closes its service, without waiting for either. */
    fun end() {
        restart()
        val pending = takeReplaced()
        applicationScope.launch { release(pending) }
    }

    /** Waits out the replaced generations' coroutines, then closes their services. */
    suspend fun releaseReplaced() = release(takeReplaced())

    private fun takeReplaced(): List<Pair<Job, NymeaWifiService?>> = replaced.toList().also { replaced.clear() }

    // Non-cancellable so a caller cancelled mid-release still closes the service; joining first means a peripheral a
    // cancelled connect was still installing belongs to the service before it is closed.
    private suspend fun release(generations: List<Pair<Job, NymeaWifiService?>>) {
        withContext(NonCancellable) {
            for ((generation, replacedService) in generations) {
                generation.cancelAndJoin()
                replacedService?.close()
            }
        }
    }

    private fun newJob(): Job = SupervisorJob(parent.coroutineContext[Job])
}
