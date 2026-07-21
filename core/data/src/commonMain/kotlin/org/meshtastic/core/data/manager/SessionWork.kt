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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext

/**
 * Launches deferred [block] without a lease for explicitly trusted work, or acquires a nested lifecycle lease before
 * the caller can release its packet lease. The undispatched leased path prevents teardown from racing the coroutine's
 * first dispatcher turn. [onRejected] runs only when a non-null [session] can no longer admit work.
 */
internal fun RadioInterfaceService.launchSessionWork(
    scope: CoroutineScope,
    session: RadioSessionContext?,
    onRejected: () -> Unit = {},
    block: suspend () -> Unit,
): Job = if (session == null) {
    scope.handledLaunch { block() }
} else {
    scope.handledLaunch(start = CoroutineStart.UNDISPATCHED) {
        val admitted = runWithSessionLease(session) { block() }
        if (!admitted) onRejected()
    }
}
