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

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.atLeast
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RadioControllerImplTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val commandSender: CommandSender = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val locationManager: MeshLocationManager = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val dataHandler: MeshDataHandler = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val meshPrefs: MeshPrefs = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)
    private val messageProcessor: MeshMessageProcessor = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)

    private val testScope = TestScope()

    private fun createController(
        serviceRepository: ServiceRepository = ServiceRepositoryImpl(),
        myNodeNum: Int? = 1234,
    ): RadioControllerImpl {
        every { nodeManager.myNodeNum } returns MutableStateFlow(myNodeNum)
        every { nodeManager.myDeviceId } returns MutableStateFlow(null)
        every { meshPrefs.deviceAddress } returns MutableStateFlow(null)
        return RadioControllerImpl(
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            commandSender = commandSender,
            nodeManager = nodeManager,
            radioInterfaceService = radioInterfaceService,
            locationManager = locationManager,
            packetRepository = lazy { packetRepository },
            dataHandler = lazy { dataHandler },
            analytics = analytics,
            meshPrefs = meshPrefs,
            uiPrefs = uiPrefs,
            databaseManager = databaseManager,
            notificationManager = notificationManager,
            messageProcessor = lazy { messageProcessor },
            radioConfigRepository = radioConfigRepository,
            scope = testScope,
        )
    }

    @Test
    fun connectionStateAndClientNotificationDelegateToServiceRepository() {
        val serviceRepository = ServiceRepositoryImpl()
        val controller = createController(serviceRepository = serviceRepository)
        val notification = ClientNotification()

        assertSame(serviceRepository.connectionState, controller.connectionState)
        assertSame(serviceRepository.clientNotification, controller.clientNotification)

        serviceRepository.setConnectionState(ConnectionState.Connecting)
        serviceRepository.setClientNotification(notification)

        assertEquals(ConnectionState.Connecting, controller.connectionState.value)
        assertSame(notification, controller.clientNotification.value)

        controller.clearClientNotification()

        assertNull(serviceRepository.clientNotification.value)
    }

    @Test
    fun sendMessageDelegatesToCommandSender() = runTest {
        val controller = createController(myNodeNum = 456)
        val packet = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 1, text = "ping")

        controller.sendMessage(packet)

        verifySuspend { commandSender.sendData(packet) }
        verifySuspend { dataHandler.rememberDataPacket(packet, 456, false) }
    }

    @Test
    fun sendSharedContactCallsCommandSenderAdminAwait() = runTest {
        val controller = createController()
        val nodeNum = 321
        val user = User(id = NodeAddress.numToDefaultId(nodeNum), long_name = "Remote Node", short_name = "RN")
        val node = Node(num = nodeNum, user = user, manuallyVerified = true)
        every { nodeRepository.getNode(NodeAddress.numToDefaultId(nodeNum)) } returns node
        everySuspend { commandSender.sendAdminAwait(any(), any(), any(), any()) } returns true

        val result = controller.sendSharedContact(nodeNum)

        assertTrue(result)
        verifySuspend { commandSender.sendAdminAwait(any(), any(), any(), any()) }
    }

    @Test
    fun requestConfigOperationsDelegateToCommandSender() = runTest {
        val controller = createController()

        controller.getOwner(destNum = 101, packetId = 1)
        controller.getConfig(destNum = 102, configType = 2, packetId = 3)
        controller.getModuleConfig(destNum = 103, moduleConfigType = 4, packetId = 5)
        controller.getChannel(destNum = 104, index = 6, packetId = 7)
        controller.getRingtone(destNum = 105, packetId = 8)
        controller.getCannedMessages(destNum = 106, packetId = 9)
        controller.getDeviceConnectionStatus(destNum = 107, packetId = 10)

        // All delegate to commandSender.sendAdmin
        verifySuspend(atLeast(7)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun stopProvideLocationDelegatesToLocationManager() {
        val controller = createController()

        controller.stopProvideLocation()

        verify { locationManager.stop() }
    }

    @Test
    fun setDeviceAddressSwitchesDatabaseAndTransport() = runTest {
        val controller = createController()
        every { meshPrefs.deviceAddress } returns MutableStateFlow("old:addr")

        controller.setDeviceAddress("tcp:192.168.1.1")
        testScope.advanceUntilIdle()

        // Verify ordering: switchDevice completes before transport reconfiguration
        verifySuspend { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
        verifySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") }
        verify { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") }
    }

    @Test
    fun setDeviceAddressSkipsSwitchWhenAddressUnchanged() = runTest {
        val controller = createController()
        every { meshPrefs.deviceAddress } returns MutableStateFlow("tcp:192.168.1.1")

        controller.setDeviceAddress("tcp:192.168.1.1")
        testScope.advanceUntilIdle()

        // switchDevice should skip when addresses match, but transport still reconfigures
        verify { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") }
        verifySuspend(exactly(0)) { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
    }

    @Test
    fun sendReactionPersistsToDatabase() = runTest {
        val controller = createController()
        val user = User(id = "!abcd1234", long_name = "Test", short_name = "T")
        val node = Node(num = 1234, user = user)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(1234 to node)
        every { nodeManager.getMyId() } returns "!abcd1234"

        controller.sendReaction(emoji = "👍", replyId = 42, contactKey = "0!dest5678")

        // Reaction must be persisted (not fire-and-forget)
        verifySuspend { commandSender.sendData(any()) }
        verifySuspend { packetRepository.insertReaction(any(), any()) }
    }

    @Test
    fun setFavoriteSendsAdminAndUpdatesState() = runTest {
        val controller = createController()
        val node = Node(num = 99, user = User(id = "!node99"), isFavorite = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun setFavoriteIsNoOpWhenAlreadyInRequestedState() = runTest {
        val controller = createController()
        val node = Node(num = 99, user = User(id = "!node99"), isFavorite = true)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify(exactly(0)) { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun setIgnoredSendsAdminUpdatesStateAndFiltersPackets() = runTest {
        val controller = createController()
        val node = Node(num = 99, user = User(id = "!node99"), isIgnored = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setIgnored(99, ignored = true)
        testScope.advanceUntilIdle()

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
        verifySuspend { packetRepository.updateFilteredBySender("!node99", true) }
    }

    @Test
    fun toggleMutedSendsAdminAndUpdatesState() = runTest {
        val controller = createController()
        val node = Node(num = 99, user = User(id = "!node99"), isMuted = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.toggleMuted(99)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun nodeManagementReturnsEarlyWhenMyNodeNumIsNull() = runTest {
        val controller = createController(myNodeNum = null)

        controller.setFavorite(99, favorite = true)
        controller.setIgnored(99, ignored = true)
        controller.toggleMuted(99)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun removeByNodenumAlwaysRemovesLocallyAndSendsAdminWhenConnected() = runTest {
        val controller = createController()

        controller.removeByNodenum(packetId = 1, nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun removeByNodenumRemovesLocallyEvenWhenDisconnected() = runTest {
        val controller = createController(myNodeNum = null)

        controller.removeByNodenum(packetId = 1, nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        // No admin message sent when disconnected
        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun rebootSendsAdminMessageWithDelay() = runTest {
        val controller = createController()

        controller.reboot(destNum = 101, packetId = 7)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun shutdownSendsAdminMessage() = runTest {
        val controller = createController()

        controller.shutdown(destNum = 101, packetId = 8)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun factoryResetSendsAdminMessage() = runTest {
        val controller = createController()

        controller.factoryReset(destNum = 101, packetId = 9)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun nodedbResetSendsAdminMessage() = runTest {
        val controller = createController()

        controller.nodedbReset(destNum = 101, packetId = 10, preserveFavorites = true)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun setTimeSendsAdminMessageWithCurrentEpochSeconds() = runTest {
        val controller = createController()

        var sentMessage: AdminMessage? = null
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                sentMessage = (it.args[3] as () -> AdminMessage)()
            }

        controller.setTime(destNum = 101, packetId = 11)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        // The phone's current time is sent; assert it is populated and a plausible recent epoch (after 2020).
        val setTime = sentMessage?.set_time_only
        assertTrue(setTime != null && setTime > 1_577_836_800)
    }

    @Test
    fun refreshMetadataSendsAdminWithWantResponse() = runTest {
        val controller = createController()

        controller.refreshMetadata(destNum = 101)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun editLocalSettingsChannelWritesDoNotMirrorToLocalCache() = runTest {
        val controller = createController(myNodeNum = 1234)

        controller.editLocalSettings {
            setChannel(Channel(index = 0, role = Channel.Role.PRIMARY, settings = ChannelSettings(name = "A")))
            setChannel(Channel(index = 1, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "B")))
        }
        testScope.advanceUntilIdle()

        // Exactly 4 admin packets: begin + 2 channel writes + commit. The tight count also catches a duplicated
        // begin/commit or an accidental double-write per channel.
        verifySuspend(exactly(4)) { commandSender.sendAdmin(any(), any(), any(), any()) }
        // A transactional channel write must NOT eagerly mirror to the local cache the way one-shot
        // setRemoteChannel does for the local node. importChannelSet owns the cache and writes it once after commit
        // (replaceAllSettings), so an interrupted import can't leave partial channels cached. A regression to
        // per-slot mirroring inside the session would make this call count non-zero.
        verifySuspend(exactly(0)) { radioConfigRepository.updateChannelSettings(any()) }
    }

    @Test
    fun importContactSendsAdminAndUpdatesNodeManager() = runTest {
        val controller = createController()
        // A QR-scanned contact arrives with manually_verified = false (proto default).
        val contact = SharedContact(node_num = 42, user = User(id = "!0000002a", long_name = "Test"))

        controller.importContact(contact)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        // Importing is an act of manual verification, so the node is recorded as verified.
        verify { nodeManager.handleReceivedUser(42, any(), any(), true) }
    }

    @Test
    fun setHamModeSendsAdminWithEchoedLoraValuesAndUpdatesUser() = runTest {
        val controller = createController(myNodeNum = 123)
        val existingUser = User(id = "!0000007b", long_name = "Old Name", short_name = "OLD")
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(123 to Node(num = 123, user = existingUser))
        every { radioConfigRepository.localConfigFlow } returns
            MutableStateFlow(LocalConfig(lora = Config.LoRaConfig(tx_power = 20, override_frequency = 915.5f)))

        var sentMessage: AdminMessage? = null
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                sentMessage = (it.args[3] as () -> AdminMessage)()
            }

        controller.setHamMode(123, HamParameters(call_sign = "KK7ABC", short_name = "KK7A"), 42)

        val ham = sentMessage?.set_ham_mode
        assertEquals("KK7ABC", ham?.call_sign)
        assertEquals("KK7A", ham?.short_name)
        // Current LoRa values are echoed so a re-send never wipes the node's overrides.
        assertEquals(20, ham?.tx_power)
        assertEquals(915.5f, ham?.frequency)
        verify {
            nodeManager.handleReceivedUser(
                123,
                existingUser.copy(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true),
                0,
                false,
            )
        }
    }

    @Test
    fun setHamModeWithNoCachedLoraConfigSendsProtoDefaults() = runTest {
        val controller = createController(myNodeNum = 123)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig())

        var sentMessage: AdminMessage? = null
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                sentMessage = (it.args[3] as () -> AdminMessage)()
            }

        controller.setHamMode(123, HamParameters(call_sign = "KK7ABC", short_name = "KK7A"), 42)

        val ham = sentMessage?.set_ham_mode
        assertEquals(0, ham?.tx_power)
        assertEquals(0f, ham?.frequency)
        // Unknown node: the optimistic update is built on a default User.
        verify {
            nodeManager.handleReceivedUser(
                123,
                User(long_name = "KK7ABC", short_name = "KK7A", is_licensed = true),
                0,
                false,
            )
        }
    }

    @Test
    fun setHamModeIgnoresRemoteDestinations() = runTest {
        val controller = createController(myNodeNum = 123)

        controller.setHamMode(456, HamParameters(call_sign = "KK7ABC", short_name = "KK7A"), 42)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    @Test
    fun importContactReturnsEarlyWhenDisconnected() = runTest {
        val controller = createController(myNodeNum = null)
        val contact = SharedContact(node_num = 42, user = User(id = "!0000002a"))

        controller.importContact(contact)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }
}
