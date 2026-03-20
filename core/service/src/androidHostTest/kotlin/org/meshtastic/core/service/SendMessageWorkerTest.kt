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
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.service.worker.SendMessageWorker
import org.meshtastic.core.testing.FakeRadioController
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        val packetId = 12345
        val dataPacket = DataPacket(to = "dest", bytes = "Hello".encodeToByteArray().toByteString(), dataType = 0)
        everySuspend { packetRepository.getPacketByPacketId(packetId) } returns dataPacket
        everySuspend { packetRepository.updateMessageStatus(any(), any()) } returns Unit

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
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
        verifySuspend { packetRepository.updateMessageStatus(dataPacket, MessageStatus.ENROUTE) }
    }

    @Test
    fun `doWork returns retry when radio is disconnected`() = runTest {
        // Arrange
        val packetId = 12345
        val dataPacket = DataPacket(to = "dest", bytes = "Hello".encodeToByteArray().toByteString(), dataType = 0)
        everySuspend { packetRepository.getPacketByPacketId(packetId) } returns dataPacket
        radioController.setConnectionState(ConnectionState.Disconnected)

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
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
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.updateMessageStatus(any(), any()) }
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
        verifySuspend(mode = VerifyMode.exactly(0)) { packetRepository.getPacketByPacketId(any()) }
    }

    @Test
    fun `doWork returns retry and marks queued when send throws`() = runTest {
        val packetId = 12345
        val dataPacket = DataPacket(to = "dest", bytes = "Hello".encodeToByteArray().toByteString(), dataType = 0)
        everySuspend { packetRepository.getPacketByPacketId(packetId) } returns dataPacket
        everySuspend { packetRepository.updateMessageStatus(any(), any()) } returns Unit
        radioController.throwOnSend = true

        val worker =
            TestListenableWorkerBuilder<SendMessageWorker>(context)
                .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
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
        verifySuspend { packetRepository.updateMessageStatus(dataPacket, MessageStatus.QUEUED) }
    }
}
