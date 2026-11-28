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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import kotlin.time.Duration.Companion.minutes

/**
 * Ceiling on one inbound session handler. A handler that suspends forever holds up teardown's drain, so it never
 * completes and the SDK's bounded session buffer fills; two minutes is far above any real handler.
 */
internal val SESSION_HANDLER_TIMEOUT = 2.minutes

/** Runs [block], cancelling it after [SESSION_HANDLER_TIMEOUT] and logging [label] instead of failing the pipeline. */
internal suspend fun withHandlerTimeout(label: String, block: suspend () -> Unit) {
    try {
        withTimeout(SESSION_HANDLER_TIMEOUT) { block() }
    } catch (timeout: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        Logger.e(timeout) {
            "Session handler exceeded ${SESSION_HANDLER_TIMEOUT.inWholeMilliseconds}ms and was cancelled; " +
                "dropping its packet to keep the receive pipeline alive (handler=$label)"
        }
    }
}

/**
 * Runs [block] to completion as a child of [session]'s scope, so teardown joins it; [onRejected] runs instead when the
 * session is closed or closes before [block] starts.
 */
internal fun RadioInterfaceService.launchSessionWork(
    scope: CoroutineScope,
    session: RadioSessionContext?,
    onRejected: () -> Unit = {},
    block: suspend () -> Unit,
): Job {
    if (session == null) return scope.handledLaunch { block() }
    val sessionScope = sessionScope(session)
    if (sessionScope == null) {
        onRejected()
        return Job().apply { complete() }
    }
    return sessionScope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
        if (!isActive) {
            onRejected()
            return@handledLaunch
        }
        withContext(NonCancellable) { block() }
    }
}
