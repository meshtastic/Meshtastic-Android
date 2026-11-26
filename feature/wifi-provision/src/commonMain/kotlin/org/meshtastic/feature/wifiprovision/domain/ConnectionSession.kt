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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.RELEASE_TIMEOUT

/**
 * Owns the lifetime of everything started for one connection attempt: the scan or connect, and every network scan and
 * credentials request after it, all launched in [scope]. [restart] and [end] cancel that whole generation at once, so
 * nothing from an earlier connection can write state after the screen has moved on.
 *
 * The cancelled generation's coroutines are joined and its [service] is closed in [applicationScope], which outlives
 * the ViewModel. Every such release is tracked: a new attempt calls [awaitRelease] before it touches BLE, so it never
 * overlaps the teardown of the connection before it.
 *
 * Call from the thread the ViewModel runs on; [parent] is its scope.
 */
internal class ConnectionSession(private val parent: CoroutineScope, private val applicationScope: CoroutineScope) {
    private var job: Job = newJob()
    private val releases = mutableListOf<Job>()

    /** The service of the current generation, or null before one is connected and after [end]. */
    var service: NymeaWifiService? = null
        private set

    /** The scope every operation of the current generation runs in. */
    var scope: CoroutineScope = parent + job
        private set

    /** Cancels the current generation and starts the next one, with [replacement] as its service. */
    fun restart(replacement: NymeaWifiService? = null): CoroutineScope {
        val replacedJob = job
        val replacedService = service
        replacedJob.cancel()
        releases.removeAll { it.isCompleted }
        releases += applicationScope.launch { release(replacedJob, replacedService) }
        job = newJob()
        service = replacement
        scope = parent + job
        return scope
    }

    /** Cancels the current generation and starts closing its service, without waiting for either. */
    fun end() {
        restart()
    }

    /**
     * Waits until every release started so far has finished, for at most [RELEASE_TIMEOUT]. A release that blocks
     * (closing a BLE connection can wait on the stack) must not leave Retry hanging, so after the timeout the new
     * attempt goes ahead and the release carries on in the background.
     */
    suspend fun awaitRelease() {
        releases.removeAll { it.isCompleted }
        val inFlight = releases.toList()
        if (inFlight.isEmpty()) return
        if (withTimeoutOrNull(RELEASE_TIMEOUT) { inFlight.joinAll() } == null) {
            Logger.w { "$TAG: a previous connection is still closing; continuing" }
        }
    }

    // Non-cancellable so a cancelled caller still closes the service; joining first means a peripheral a cancelled
    // connect was still installing belongs to the service before it is closed. A failing close must not escape into
    // the application scope.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun release(generation: Job, replacedService: NymeaWifiService?) {
        withContext(NonCancellable) {
            generation.cancelAndJoin()
            try {
                replacedService?.close()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "$TAG: closing the previous connection failed" }
            }
        }
    }

    private fun newJob(): Job = SupervisorJob(parent.coroutineContext[Job])

    private companion object {
        const val TAG = "ConnectionSession"
    }
}
