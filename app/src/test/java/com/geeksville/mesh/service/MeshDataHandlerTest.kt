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
package com.geeksville.mesh.service

import dagger.Lazy
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.junit.Before
import org.junit.Test
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.data.repository.PacketRepository
import org.meshtastic.core.data.repository.RadioConfigRepository
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.prefs.mesh.MeshPrefs
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.service.filter.MessageFilterService
import org.meshtastic.proto.Data
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.StoreForwardPlusPlus

class MeshDataHandlerTest {

    private val nodeManager: MeshNodeManager = mockk(relaxed = true)
    private val packetHandler: PacketHandler = mockk(relaxed = true)
    private val serviceRepository: ServiceRepository = mockk(relaxed = true)
    private val packetRepository: PacketRepository = mockk(relaxed = true)
    private val packetRepositoryLazy: Lazy<PacketRepository> = mockk { every { get() } returns packetRepository }
    private val serviceBroadcasts: MeshServiceBroadcasts = mockk(relaxed = true)
    private val serviceNotifications: MeshServiceNotifications = mockk(relaxed = true)
    private val analytics: PlatformAnalytics = mockk(relaxed = true)
    private val dataMapper: MeshDataMapper = mockk(relaxed = true)
    private val configHandler: MeshConfigHandler = mockk(relaxed = true)
    private val configFlowManager: MeshConfigFlowManager = mockk(relaxed = true)
    private val commandSender: MeshCommandSender = mockk(relaxed = true)
    private val historyManager: MeshHistoryManager = mockk(relaxed = true)
    private val meshPrefs: MeshPrefs = mockk(relaxed = true)
    private val connectionManager: MeshConnectionManager = mockk(relaxed = true)
    private val tracerouteHandler: MeshTracerouteHandler = mockk(relaxed = true)
    private val neighborInfoHandler: MeshNeighborInfoHandler = mockk(relaxed = true)
    private val radioConfigRepository: RadioConfigRepository = mockk(relaxed = true)
    private val messageFilterService: MessageFilterService = mockk(relaxed = true)

    private lateinit var meshDataHandler: MeshDataHandler

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.i(any(), any()) } returns 0
        every { android.util.Log.w(any(), any<String>()) } returns 0
        every { android.util.Log.e(any(), any()) } returns 0

        meshDataHandler =
            MeshDataHandler(
                nodeManager,
                packetHandler,
                serviceRepository,
                packetRepositoryLazy,
                serviceBroadcasts,
                serviceNotifications,
                analytics,
                dataMapper,
                configHandler,
                configFlowManager,
                commandSender,
                historyManager,
                meshPrefs,
                connectionManager,
                tracerouteHandler,
                neighborInfoHandler,
                radioConfigRepository,
                messageFilterService,
            )
        // Use UnconfinedTestDispatcher for running coroutines synchronously in tests
        meshDataHandler.start(CoroutineScope(UnconfinedTestDispatcher()))

        every { nodeManager.myNodeNum } returns 123
        every { nodeManager.getMyId() } returns "!0000007b"

        // Default behavior for dataMapper to return a valid DataPacket when requested
        every { dataMapper.toDataPacket(any()) } answers
            {
                val packet = firstArg<MeshPacket>()
                DataPacket(
                    to = "to",
                    channel = 0,
                    bytes = packet.decoded?.payload,
                    dataType = packet.decoded?.portnum?.value ?: 0,
                    id = packet.id,
                )
            }
    }

    @Test
    fun `handleReceivedData with SFPP LINK_PROVIDE updates SFPP status`() = runTest {
        val sfppMessage =
            StoreForwardPlusPlus(
                sfpp_message_type = StoreForwardPlusPlus.SFPP_message_type.LINK_PROVIDE,
                encapsulated_id = 999,
                encapsulated_from = 456,
                encapsulated_to = 789,
                encapsulated_rxtime = 1000,
                message = "EncryptedPayload".toByteArray().toByteString(),
                message_hash = "Hash".toByteArray().toByteString(),
            )

        val payload = StoreForwardPlusPlus.ADAPTER.encode(sfppMessage).toByteString()
        val meshPacket =
            MeshPacket(
                from = 456,
                to = 123,
                decoded = Data(portnum = PortNum.STORE_FORWARD_PLUSPLUS_APP, payload = payload),
                id = 1001,
            )

        meshDataHandler.handleReceivedData(meshPacket, 123)

        // SFPP_ROUTING because commit_hash is empty
        coVerify {
            packetRepository.updateSFPPStatus(
                packetId = 999,
                from = 456,
                to = 789,
                hash = any(),
                status = MessageStatus.SFPP_ROUTING,
                rxTime = 1000L,
                myNodeNum = 123,
            )
        }
    }
}
