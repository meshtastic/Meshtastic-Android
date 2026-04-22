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

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.ByteString
import org.meshtastic.core.model.SessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerImplTest {

    private class MutableClock(var now: Instant = Instant.fromEpochSeconds(1_700_000_000)) : Clock {
        override fun now(): Instant = now
    }

    private val nodeA = 0xAAAA
    private val nodeB = 0xBBBB
    private val keyA = ByteString.of(1, 2, 3, 4, 5, 6, 7, 8)
    private val keyB = ByteString.of(9, 8, 7, 6, 5, 4, 3, 2)

    @Test
    fun `recordSession stores per-node passkeys without overwriting siblings`() {
        val mgr = SessionManagerImpl(MutableClock())

        mgr.recordSession(nodeA, keyA)
        mgr.recordSession(nodeB, keyB)

        assertEquals(keyA, mgr.getPasskey(nodeA))
        assertEquals(keyB, mgr.getPasskey(nodeB))
    }

    @Test
    fun `recordSession with empty passkey is a no-op`() {
        val mgr = SessionManagerImpl(MutableClock())
        mgr.recordSession(nodeA, ByteString.EMPTY)
        assertSame(ByteString.EMPTY, mgr.getPasskey(nodeA))
    }

    @Test
    fun `clearAll wipes per-node entries`() {
        val mgr = SessionManagerImpl(MutableClock())
        mgr.recordSession(nodeA, keyA)
        mgr.recordSession(nodeB, keyB)

        mgr.clearAll()

        assertSame(ByteString.EMPTY, mgr.getPasskey(nodeA))
        assertSame(ByteString.EMPTY, mgr.getPasskey(nodeB))
    }

    @Test
    fun `observeSessionStatus emits NoSession initially when no key recorded`() = runTest {
        val mgr = SessionManagerImpl(MutableClock())
        assertEquals(SessionStatus.NoSession, mgr.observeSessionStatus(nodeA).first())
    }

    @Test
    fun `observeSessionStatus reports Active for a fresh recording`() = runTest {
        val clock = MutableClock()
        val mgr = SessionManagerImpl(clock)
        mgr.recordSession(nodeA, keyA)

        val status = mgr.observeSessionStatus(nodeA).first()
        assertIs<SessionStatus.Active>(status)
        assertEquals(clock.now, status.refreshedAt)
    }

    @Test
    fun `observeSessionStatus reports Stale once age exceeds threshold`() = runTest {
        val clock = MutableClock()
        val mgr = SessionManagerImpl(clock)
        mgr.recordSession(nodeA, keyA)

        // Age past the 240s active threshold; still under firmware TTL of 300s.
        clock.now = clock.now.plus(250.seconds)

        val status = mgr.observeSessionStatus(nodeA).first()
        assertIs<SessionStatus.Stale>(status)
    }

    @Test
    fun `sessionRefreshFlow emits srcNodeNum on each non-empty recording`() = runTest {
        val mgr = SessionManagerImpl(MutableClock())

        mgr.sessionRefreshFlow.test {
            mgr.recordSession(nodeA, keyA)
            assertEquals(nodeA, awaitItem())
            mgr.recordSession(nodeB, keyB)
            assertEquals(nodeB, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
