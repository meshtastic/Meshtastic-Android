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
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.PacketRepository
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SendMessageWorkerTest {

    private lateinit var context: Context
    private lateinit var packetRepository: PacketRepository
    private lateinit var radioController: RadioController

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        packetRepository = mockk(relaxed = true)
        radioController = mockk(relaxed = true)
        every { radioController.connectionState } returns MutableStateFlow(ConnectionState.Connected)
    }

    @Test
    fun `doWork returns success when packet is sent successfully`() = runTest {
        // Arrange
        val packetId = 12345
        val dataPacket = DataPacket(to = "dest", bytes = "Hello".encodeToByteArray().toByteString(), dataType = 0)
        coEvery { packetRepository.getPacketByPacketId(packetId) } returns dataPacket
        every { radioController.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { radioController.sendMessage(any()) } just Runs
        coEvery { packetRepository.updateMessageStatus(any(), any()) } just Runs

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
        coVerify { radioController.sendMessage(dataPacket) }
        coVerify { packetRepository.updateMessageStatus(dataPacket, MessageStatus.ENROUTE) }
    }

    @Test
    fun `doWork returns retry when radio is disconnected`() = runTest {
        // Arrange
        val packetId = 12345
        val dataPacket = DataPacket(to = "dest", bytes = "Hello".encodeToByteArray().toByteString(), dataType = 0)
        coEvery { packetRepository.getPacketByPacketId(packetId) } returns dataPacket
        every { radioController.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)

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
        coVerify(exactly = 0) { radioController.sendMessage(any()) }
    }

    @Test
    fun `doWork returns failure when packet is missing`() = runTest {
        // Arrange
        val packetId = 999
        coEvery { packetRepository.getPacketByPacketId(packetId) } returns null

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
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
