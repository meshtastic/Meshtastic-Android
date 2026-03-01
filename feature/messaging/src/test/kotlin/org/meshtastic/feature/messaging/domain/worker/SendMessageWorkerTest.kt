package org.meshtastic.feature.messaging.domain.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.database.entity.Packet
import org.meshtastic.core.database.entity.PacketEntity
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.RadioController
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
        val dataPacket = DataPacket("dest", 0, "Hello")
        val packet = mockk<Packet>(relaxed = true)
        val packetEntity = PacketEntity(packet = packet)
        every { packet.data } returns dataPacket
        coEvery { packetRepository.getPacketByPacketId(packetId) } returns packetEntity
        every { radioController.connectionState } returns MutableStateFlow(ConnectionState.Connected)
        coEvery { radioController.sendMessage(any()) } just Runs
        coEvery { packetRepository.updateMessageStatus(any(), any()) } just Runs

        val worker = TestListenableWorkerBuilder<SendMessageWorker>(context)
            .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
            })
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
        val dataPacket = DataPacket("dest", 0, "Hello")
        val packet = mockk<Packet>(relaxed = true)
        val packetEntity = PacketEntity(packet = packet)
        every { packet.data } returns dataPacket
        coEvery { packetRepository.getPacketByPacketId(packetId) } returns packetEntity
        every { radioController.connectionState } returns MutableStateFlow(ConnectionState.Disconnected)

        val worker = TestListenableWorkerBuilder<SendMessageWorker>(context)
            .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
            })
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

        val worker = TestListenableWorkerBuilder<SendMessageWorker>(context)
            .setInputData(workDataOf(SendMessageWorker.KEY_PACKET_ID to packetId))
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker? = SendMessageWorker(appContext, workerParameters, packetRepository, radioController)
            })
            .build()

        // Act
        val result = worker.doWork()

        // Assert
        assertEquals(ListenableWorker.Result.failure(), result)
    }
}
