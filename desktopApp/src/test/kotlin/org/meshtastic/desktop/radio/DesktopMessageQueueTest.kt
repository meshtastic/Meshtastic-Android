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
package org.meshtastic.desktop.radio

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.testing.FakeRadioController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopMessageQueueTest {

    @Test
    fun `concurrent enqueue sends only the caller that owns the queued claim`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mock<PacketRepository>(MockMode.autofill)
        val radio = connectedRadio()
        val id = PersistedPacketId(myNodeNum = 42, uuid = 99L)
        val packet = queuedPacket()
        var claimCount = 0
        everySuspend { repository.claimQueuedPacket(id) } calls
            {
                claimCount += 1
                PersistedPacket(
                    id,
                    packet.copy(status = if (claimCount == 1) MessageStatus.QUEUED else MessageStatus.ENROUTE),
                )
            }
        val sendStarted = CompletableDeferred<Unit>()
        val releaseSend = CompletableDeferred<Unit>()
        radio.onSendMessage = {
            sendStarted.complete(Unit)
            releaseSend.await()
        }
        val queue = queue(repository, radio, dispatcher)

        queue.enqueue(id)
        runCurrent()
        assertTrue(sendStarted.isCompleted)

        queue.enqueue(id)
        runCurrent()
        assertEquals(2, claimCount)

        releaseSend.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(packet), radio.sentPackets)
        verifySuspend(mode = VerifyMode.exactly(2)) { repository.claimQueuedPacket(id) }
        verifySuspend(mode = VerifyMode.exactly(0)) { repository.rollbackEnroutePacket(any()) }
    }

    @Test
    fun `send failure rolls back only the exact claimed row`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mock<PacketRepository>(MockMode.autofill)
        val radio = connectedRadio().apply { throwOnSend = true }
        val id = PersistedPacketId(myNodeNum = 42, uuid = 99L)
        val packet = queuedPacket()
        everySuspend { repository.claimQueuedPacket(id) } returns PersistedPacket(id, packet)
        everySuspend { repository.rollbackEnroutePacket(id) } returns true

        queue(repository, radio, dispatcher).enqueue(id)
        advanceUntilIdle()

        assertEquals(emptyList(), radio.sentPackets)
        verifySuspend { repository.rollbackEnroutePacket(id) }
    }

    @Test
    fun `missing claim neither sends nor rolls back`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val repository = mock<PacketRepository>(MockMode.autofill)
        val radio = connectedRadio()
        val id = PersistedPacketId(myNodeNum = 42, uuid = 99L)
        everySuspend { repository.claimQueuedPacket(id) } returns null

        queue(repository, radio, dispatcher).enqueue(id)
        advanceUntilIdle()

        assertEquals(emptyList(), radio.sentPackets)
        verifySuspend(mode = VerifyMode.exactly(0)) { repository.rollbackEnroutePacket(any()) }
    }

    private fun queue(repository: PacketRepository, radio: FakeRadioController, dispatcher: TestDispatcher) =
        DesktopMessageQueue(
            packetRepository = repository,
            radioController = radio,
            dispatchers = CoroutineDispatchers(main = dispatcher, io = dispatcher, default = dispatcher),
        )

    private fun connectedRadio() = FakeRadioController().apply { setConnectionState(ConnectionState.Connected) }

    private fun queuedPacket() = DataPacket(
        id = 7,
        from = "^local",
        to = "!remote",
        bytes = "hello".encodeToByteArray().toByteString(),
        dataType = 1,
        status = MessageStatus.QUEUED,
    )
}
