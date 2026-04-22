/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import okio.ByteString
import org.koin.core.annotation.Single
import org.meshtastic.core.model.SessionStatus
import org.meshtastic.core.repository.SessionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * In-memory implementation of [SessionManager] backed by an atomicfu-protected [PersistentMap].
 *
 * Per-node state replaces the single global passkey atomic that previously lived in `CommandSenderImpl`. Without this,
 * bouncing remote-admin between two nodes within the firmware's 300 s TTL silently invalidated the first node's session
 * because its passkey was overwritten by the second node's response.
 *
 * Threshold rationale (see `firmware/src/modules/AdminModule.cpp:1460-1481`):
 * - Firmware TTL = 300 s, with passkey rotation at the 150 s halfway mark on the next response sent.
 * - We treat 240 s as the "active enough to navigate without refreshing" boundary to leave headroom for in-flight
 *   packets, mesh latency, and clock skew. A user navigated into the remote-admin screen at 299 s would otherwise
 *   immediately time out on the next request.
 */
@Single
class SessionManagerImpl(private val clock: Clock) : SessionManager {

    private val entries = atomic<PersistentMap<Int, SessionEntry>>(persistentMapOf())

    private val refreshFlow = MutableSharedFlow<Int>(extraBufferCapacity = REFRESH_BUFFER)
    override val sessionRefreshFlow: SharedFlow<Int> = refreshFlow.asSharedFlow()

    override fun recordSession(srcNodeNum: Int, passkey: ByteString) {
        if (passkey.size == 0) return
        val now = clock.now()
        entries.update { it.put(srcNodeNum, SessionEntry(passkey, now)) }
        Logger.d { "Recorded session refresh from $srcNodeNum (${passkey.size} bytes)" }
        refreshFlow.tryEmit(srcNodeNum)
    }

    override fun getPasskey(destNum: Int): ByteString = entries.value[destNum]?.passkey ?: ByteString.EMPTY

    override fun clearAll() {
        if (entries.value.isNotEmpty()) {
            Logger.d { "Clearing ${entries.value.size} session entries" }
        }
        entries.value = persistentMapOf()
    }

    override fun observeSessionStatus(destNum: Int): Flow<SessionStatus> = merge(
        flowOf(Unit),
        refreshFlow.filter { it == destNum }.map {},
        flow {
            while (true) {
                delay(RECHECK_INTERVAL)
                emit(Unit)
            }
        },
    )
        .map { computeStatus(destNum) }
        .distinctUntilChanged()

    private fun computeStatus(destNum: Int): SessionStatus {
        val entry = entries.value[destNum] ?: return SessionStatus.NoSession
        val age = clock.now() - entry.refreshedAt
        return if (age < ACTIVE_THRESHOLD) {
            SessionStatus.Active(entry.refreshedAt)
        } else {
            SessionStatus.Stale(entry.refreshedAt)
        }
    }

    private data class SessionEntry(val passkey: ByteString, val refreshedAt: Instant)

    companion object {
        /**
         * "Active enough to navigate" window. Set below the firmware TTL (300 s) to leave room for packet flight time
         * and clock skew so users don't get sent into a screen that immediately times out.
         */
        val ACTIVE_THRESHOLD = 240.seconds

        /** Re-emit interval for [observeSessionStatus] so the UI transitions Active → Stale without user input. */
        val RECHECK_INTERVAL = 60.seconds

        private const val REFRESH_BUFFER = 8
    }
}
