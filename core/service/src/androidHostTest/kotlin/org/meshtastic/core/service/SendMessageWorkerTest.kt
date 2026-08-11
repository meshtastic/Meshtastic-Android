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
package org.meshtastic.core.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PersistedPacket
import org.meshtastic.core.repository.PersistedPacketId
import org.meshtastic.core.service.worker.SendMessageWorker
import org.meshtastic.core.testing.FakeRadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SendMessageWorkerTest {

    private lateinit var context: Context
    private lateinit var packetRepository: PacketRepository
    private lateinit var radioController: FakeRadioController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packetRepository = mock(MockMode.autofill)
        radioController = FakeRadioController()
        radioController.setConnectionState(ConnectionState.Connected)
    }

    @Test
    fun `doWork returns success when packet is sent successfully`() = runTest {
        // Arrange
        val packetId = PersistedPacketId(myNodeNum = 123, uuid = 12345L)
        val dataPacket =
            DataPacket(
                to = "dest",
                bytes = "Hello".encodeToByteArray().toByteString(),
                dataType = 0,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.claimQueuedPacket(packetId) } returns PersistedPacket(packetId, dataPacket)

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(
                    workDataOf(
                        SendMessageWorker.KEY_PACKET_UUID to packetId.uuid,
                        SendMessageWorker.KEY_MY_NODE_NUM to packetId.myNodeNum,
                    ),
                )
                .setWorkerFactory(
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
                    },
                )
                .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(listOf(dataPacket), radioController.sentPackets)
        verifySuspend { packetRepository.claimQueuedPacket(packetId) }
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.rollbackEnroutePacket(any()) }
    }

    @Test
    fun `doWork returns retry when radio is disconnected`() = runTest {
        // Arrange
        val packetId = PersistedPacketId(myNodeNum = 123, uuid = 12345L)
        radioController.setConnectionState(ConnectionState.Disconnected)

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(
                    workDataOf(
                        SendMessageWorker.KEY_PACKET_UUID to packetId.uuid,
                        SendMessageWorker.KEY_MY_NODE_NUM to packetId.myNodeNum,
                    ),
                )
                .setWorkerFactory(
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
                    },
                )
                .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(emptyList<DataPacket>(), radioController.sentPackets)
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.claimQueuedPacket(any()) }
    }

    @Test
    fun `doWork returns failure when packet id is missing`() = runTest {
        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setWorkerFactory(
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
                    },
                )
                .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.claimQueuedPacket(any()) }
    }

    @Test
    fun `doWork returns retry and marks queued when send throws`() = runTest {
        val packetId = PersistedPacketId(myNodeNum = 123, uuid = 12345L)
        val dataPacket =
            DataPacket(
                to = "dest",
                bytes = "Hello".encodeToByteArray().toByteString(),
                dataType = 0,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.claimQueuedPacket(packetId) } returns PersistedPacket(packetId, dataPacket)
        everySuspend { packetRepository.rollbackEnroutePacket(packetId) } returns true
        radioController.throwOnSend = true

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(
                    workDataOf(
                        SendMessageWorker.KEY_PACKET_UUID to packetId.uuid,
                        SendMessageWorker.KEY_MY_NODE_NUM to packetId.myNodeNum,
                    ),
                )
                .setWorkerFactory(
                    object : androidx.work.WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker? =
                            SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
                    },
                )
                .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        verifySuspend { packetRepository.rollbackEnroutePacket(packetId) }
    }

    @Test
    fun `two queued rows with the same mesh packet id both survive and send`() = runTest {
        val firstId = PersistedPacketId(myNodeNum = 123, uuid = 1L)
        val secondId = PersistedPacketId(myNodeNum = 123, uuid = 2L)
        val first =
            DataPacket(
                to = "!aaaa0001",
                bytes = "first".encodeToByteArray().toByteString(),
                dataType = 1,
                id = 77,
                status = MessageStatus.QUEUED,
            )
        val second =
            DataPacket(
                to = "!aaaa0001",
                bytes = "second".encodeToByteArray().toByteString(),
                dataType = 1,
                id = 77,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.claimQueuedPacket(firstId) } returns PersistedPacket(firstId, first)
        everySuspend { packetRepository.claimQueuedPacket(secondId) } returns PersistedPacket(secondId, second)

        val firstResult = worker(packetRepository, firstId).doWork()
        val secondResult = worker(packetRepository, secondId).doWork()

        assertEquals(ListenableWorker.Result.success(), firstResult)
        assertEquals(ListenableWorker.Result.success(), secondResult)
        assertEquals(listOf("first", "second"), radioController.sentPackets.map { it.text })
        verifySuspend { packetRepository.claimQueuedPacket(firstId) }
        verifySuspend { packetRepository.claimQueuedPacket(secondId) }
    }

    @Test
    fun `a stalled radio send does not block an unrelated claimed row`() = runTest {
        val stalledId = PersistedPacketId(myNodeNum = 123, uuid = 1L)
        val otherId = PersistedPacketId(myNodeNum = 123, uuid = 2L)
        val stalled =
            DataPacket(
                to = "!aaaa0001",
                bytes = "stalled".encodeToByteArray().toByteString(),
                dataType = 1,
                status = MessageStatus.QUEUED,
            )
        val other =
            DataPacket(
                to = "!bbbb0002",
                bytes = "other".encodeToByteArray().toByteString(),
                dataType = 1,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.claimQueuedPacket(stalledId) } returns PersistedPacket(stalledId, stalled)
        everySuspend { packetRepository.claimQueuedPacket(otherId) } returns PersistedPacket(otherId, other)
        val stalledSendStarted = CompletableDeferred<Unit>()
        val releaseStalledSend = CompletableDeferred<Unit>()
        radioController.onSendMessage = { packet ->
            if (packet.text == "stalled") {
                stalledSendStarted.complete(Unit)
                releaseStalledSend.await()
            }
        }

        val stalledResult = async { worker(packetRepository, stalledId).doWork() }
        stalledSendStarted.await()

        val otherResult = worker(packetRepository, otherId).doWork()

        assertEquals(ListenableWorker.Result.success(), otherResult)
        assertEquals(listOf("other"), radioController.sentPackets.map { it.text })
        releaseStalledSend.complete(Unit)
        assertEquals(ListenableWorker.Result.success(), stalledResult.await())
        assertEquals(setOf("stalled", "other"), radioController.sentPackets.mapNotNull { it.text }.toSet())
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy and UUID jobs coexist without duplicate send`() = runTest {
        val meshPacketId = 77
        val persistedId = PersistedPacketId(myNodeNum = 123, uuid = 9L)
        val queued =
            DataPacket(
                to = "!aaaa0001",
                bytes = "upgrade".encodeToByteArray().toByteString(),
                dataType = 1,
                id = meshPacketId,
                status = MessageStatus.QUEUED,
            )
        everySuspend { packetRepository.claimQueuedPacketByPacketIdIfUnique(meshPacketId) } returns
            PersistedPacket(persistedId, queued)

        val legacyResult = legacyWorker(packetRepository, meshPacketId).doWork()

        everySuspend { packetRepository.claimQueuedPacket(persistedId) } returns
            PersistedPacket(persistedId, queued.copy(status = MessageStatus.ENROUTE))
        val stableResult = worker(packetRepository, persistedId).doWork()

        assertEquals(ListenableWorker.Result.success(), legacyResult)
        assertEquals(ListenableWorker.Result.success(), stableResult)
        assertEquals(listOf("upgrade"), radioController.sentPackets.map { it.text })
        verifySuspend { packetRepository.claimQueuedPacketByPacketIdIfUnique(meshPacketId) }
        verifySuspend { packetRepository.claimQueuedPacket(persistedId) }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `legacy job fails without sending when packet id is ambiguous`() = runTest {
        val meshPacketId = 77
        everySuspend { packetRepository.claimQueuedPacketByPacketIdIfUnique(meshPacketId) } returns null

        val result = legacyWorker(packetRepository, meshPacketId).doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(emptyList<DataPacket>(), radioController.sentPackets)
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.rollbackEnroutePacket(any()) }
    }

    private fun worker(repository: PacketRepository, persistedId: PersistedPacketId): SendMessageWorker =
        TestListenableWorkerBuilder<SendMessageWorker>(context)
            .setInputData(
                workDataOf(
                    SendMessageWorker.KEY_PACKET_UUID to persistedId.uuid,
                    SendMessageWorker.KEY_MY_NODE_NUM to persistedId.myNodeNum,
                ),
            )
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker? = SendMessageWorker(appContext, workerParameters, repository, radioController)
                },
            )
            .build()

    @Suppress("DEPRECATION")
    private fun legacyWorker(repository: PacketRepository, packetId: Int): SendMessageWorker =
        TestListenableWorkerBuilder<SendMessageWorker>(context)
            .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker? = SendMessageWorker(appContext, workerParameters, repository, radioController)
                },
            )
            .build()
}
