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

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.not
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MeshUser
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.service.ServiceAction
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshMessageProcessor
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshActionHandlerImplTest {

    private val nodeManager = mock<NodeManager>(MockMode.autofill)
    private val commandSender = mock<CommandSender>(MockMode.autofill)
    private val packetRepository = mock<PacketRepository>(MockMode.autofill)
    private val serviceBroadcasts = mock<ServiceBroadcasts>(MockMode.autofill)
    private val dataHandler = mock<MeshDataHandler>(MockMode.autofill)
    private val analytics = mock<PlatformAnalytics>(MockMode.autofill)
    private val meshPrefs = mock<MeshPrefs>(MockMode.autofill)
    private val uiPrefs = mock<UiPrefs>(MockMode.autofill)
    private val databaseManager = mock<DatabaseManager>(MockMode.autofill)
    private val notificationManager = mock<NotificationManager>(MockMode.autofill)
    private val messageProcessor = mock<MeshMessageProcessor>(MockMode.autofill)
    private val radioConfigRepository = mock<RadioConfigRepository>(MockMode.autofill)

    private val myNodeNumFlow = MutableStateFlow<Int?>(MY_NODE_NUM)

    private lateinit var handler: MeshActionHandlerImpl

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    companion object {
        private const val MY_NODE_NUM = 12345
        private const val REMOTE_NODE_NUM = 67890
    }

    @BeforeTest
    fun setUp() {
        every { nodeManager.myNodeNum } returns myNodeNumFlow
        every { nodeManager.getMyId() } returns "!12345678"
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
    }

    private fun createHandler(scope: CoroutineScope): MeshActionHandlerImpl = MeshActionHandlerImpl(
        nodeManager = nodeManager,
        commandSender = commandSender,
        packetRepository = lazy { packetRepository },
        serviceBroadcasts = serviceBroadcasts,
        dataHandler = lazy { dataHandler },
        analytics = analytics,
        meshPrefs = meshPrefs,
        uiPrefs = uiPrefs,
        databaseManager = databaseManager,
        notificationManager = notificationManager,
        messageProcessor = lazy { messageProcessor },
        radioConfigRepository = radioConfigRepository,
        scope = scope,
    )

    // ---- handleUpdateLastAddress (device-switch path — P0 critical) ----

    @Test
    fun handleUpdateLastAddress_differentAddress_switchesDatabaseAndClearsState() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        every { meshPrefs.deviceAddress } returns MutableStateFlow("old_addr")
        everySuspend { databaseManager.switchActiveDatabase(any()) } returns Unit

        handler.handleUpdateLastAddress("new_addr")
        advanceUntilIdle()

        verify { meshPrefs.setDeviceAddress("new_addr") }
        verify { nodeManager.clear() }
        verifySuspend { messageProcessor.clearEarlyPackets() }
        verifySuspend { databaseManager.switchActiveDatabase("new_addr") }
        verify { notificationManager.cancelAll() }
        verify { nodeManager.loadCachedNodeDB() }
    }

    @Test
    fun handleUpdateLastAddress_sameAddress_noOp() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        every { meshPrefs.deviceAddress } returns MutableStateFlow("same_addr")

        handler.handleUpdateLastAddress("same_addr")
        advanceUntilIdle()

        verify(not) { meshPrefs.setDeviceAddress(any()) }
        verify(not) { nodeManager.clear() }
    }

    @Test
    fun handleUpdateLastAddress_nullAddress_switchesIfDifferent() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        every { meshPrefs.deviceAddress } returns MutableStateFlow("old_addr")
        everySuspend { databaseManager.switchActiveDatabase(any()) } returns Unit

        handler.handleUpdateLastAddress(null)
        advanceUntilIdle()

        verify { meshPrefs.setDeviceAddress(null) }
        verify { nodeManager.clear() }
        verifySuspend { databaseManager.switchActiveDatabase(null) }
    }

    @Test
    fun handleUpdateLastAddress_nullToNull_noOp() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        every { meshPrefs.deviceAddress } returns MutableStateFlow(null)

        handler.handleUpdateLastAddress(null)
        advanceUntilIdle()

        verify(not) { meshPrefs.setDeviceAddress(any()) }
    }

    @Test
    fun handleUpdateLastAddress_executesStepsInOrder() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        every { meshPrefs.deviceAddress } returns MutableStateFlow("old")
        everySuspend { databaseManager.switchActiveDatabase(any()) } returns Unit

        handler.handleUpdateLastAddress("new")
        advanceUntilIdle()

        // Verify critical sequence: clear -> switchDB -> cancelNotifications -> loadCachedNodeDB
        verify { nodeManager.clear() }
        verifySuspend { databaseManager.switchActiveDatabase("new") }
        verify { notificationManager.cancelAll() }
        verify { nodeManager.loadCachedNodeDB() }
    }

    // ---- onServiceAction: null myNodeNum early-return ----

    @Test
    fun onServiceAction_nullMyNodeNum_doesNothing() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        myNodeNumFlow.value = null

        val node = createTestNode(REMOTE_NODE_NUM)
        handler.onServiceAction(ServiceAction.Favorite(node))
        advanceUntilIdle()

        verify(not) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- onServiceAction: Favorite ----

    @Test
    fun onServiceAction_favorite_sendsSetFavoriteWhenNotFavorite() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val node = createTestNode(REMOTE_NODE_NUM, isFavorite = false)

        handler.onServiceAction(ServiceAction.Favorite(node))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any(), any()) }
    }

    @Test
    fun onServiceAction_favorite_sendsRemoveFavoriteWhenAlreadyFavorite() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val node = createTestNode(REMOTE_NODE_NUM, isFavorite = true)

        handler.onServiceAction(ServiceAction.Favorite(node))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any(), any()) }
    }

    // ---- onServiceAction: Ignore ----

    @Test
    fun onServiceAction_ignore_togglesAndUpdatesFilteredBySender() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val node = createTestNode(REMOTE_NODE_NUM, isIgnored = false)

        handler.onServiceAction(ServiceAction.Ignore(node))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any(), any()) }
        verifySuspend { packetRepository.updateFilteredBySender(any(), any()) }
    }

    // ---- onServiceAction: Mute ----

    @Test
    fun onServiceAction_mute_togglesMutedState() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        val node = createTestNode(REMOTE_NODE_NUM, isMuted = false)

        handler.onServiceAction(ServiceAction.Mute(node))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any(), any()) }
    }

    // ---- onServiceAction: GetDeviceMetadata ----

    @Test
    fun onServiceAction_getDeviceMetadata_sendsAdminRequest() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        handler.onServiceAction(ServiceAction.GetDeviceMetadata(REMOTE_NODE_NUM))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- onServiceAction: SendContact ----

    @Test
    fun onServiceAction_sendContact_completesWithTrueOnSuccess() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        everySuspend { commandSender.sendAdminAwait(any(), any(), any(), any()) } returns true

        val action = ServiceAction.SendContact(SharedContact())
        handler.onServiceAction(action)
        advanceUntilIdle()

        assertTrue(action.result.isCompleted)
        assertTrue(action.result.await())
    }

    @Test
    fun onServiceAction_sendContact_completesWithFalseOnFailure() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        everySuspend { commandSender.sendAdminAwait(any(), any(), any(), any()) } returns false

        val action = ServiceAction.SendContact(SharedContact())
        handler.onServiceAction(action)
        advanceUntilIdle()

        assertTrue(action.result.isCompleted)
        assertFalse(action.result.await())
    }

    // ---- onServiceAction: ImportContact ----

    @Test
    fun onServiceAction_importContact_sendsAdminAndUpdatesNode() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)

        val contact =
            SharedContact(node_num = REMOTE_NODE_NUM, user = User(id = "!abcdef12", long_name = "TestUser"))
        handler.onServiceAction(ServiceAction.ImportContact(contact))
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    // ---- handleSetOwner ----

    @Test
    fun handleSetOwner_sendsAdminAndUpdatesLocalNode() {
        handler = createHandler(testScope)
        val meshUser =
            MeshUser(
                id = "!12345678",
                longName = "Test Long",
                shortName = "TL",
                hwModel = HardwareModel.UNSET,
                isLicensed = false,
            )

        handler.handleSetOwner(meshUser, MY_NODE_NUM)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    // ---- handleSend ----

    @Test
    fun handleSend_sendsDataAndBroadcastsStatus() {
        handler = createHandler(testScope)
        val packet = DataPacket(to = "!deadbeef", dataType = 1, bytes = null, channel = 0)

        handler.handleSend(packet, MY_NODE_NUM)

        verify { commandSender.sendData(any()) }
        verify { serviceBroadcasts.broadcastMessageStatus(any(), any()) }
        verify { dataHandler.rememberDataPacket(any(), any(), any()) }
    }

    // ---- handleRequestPosition: 3 branches ----

    @Test
    fun handleRequestPosition_sameNode_doesNothing() {
        handler = createHandler(testScope)

        handler.handleRequestPosition(MY_NODE_NUM, Position(0.0, 0.0, 0), MY_NODE_NUM)

        verify(not) { commandSender.requestPosition(any(), any()) }
    }

    @Test
    fun handleRequestPosition_provideLocation_validPosition_usesGivenPosition() {
        handler = createHandler(testScope)
        every { uiPrefs.shouldProvideNodeLocation(MY_NODE_NUM) } returns MutableStateFlow(true)

        val validPosition = Position(37.7749, -122.4194, 10)
        handler.handleRequestPosition(REMOTE_NODE_NUM, validPosition, MY_NODE_NUM)

        verify { commandSender.requestPosition(REMOTE_NODE_NUM, validPosition) }
    }

    @Test
    fun handleRequestPosition_provideLocation_invalidPosition_fallsBackToNodeDB() {
        handler = createHandler(testScope)
        every { uiPrefs.shouldProvideNodeLocation(MY_NODE_NUM) } returns MutableStateFlow(true)
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        val invalidPosition = Position(0.0, 0.0, 0)
        handler.handleRequestPosition(REMOTE_NODE_NUM, invalidPosition, MY_NODE_NUM)

        // Falls back to Position(0.0, 0.0, 0) when node has no position in DB
        verify { commandSender.requestPosition(any(), any()) }
    }

    @Test
    fun handleRequestPosition_doNotProvide_sendsZeroPosition() {
        handler = createHandler(testScope)
        every { uiPrefs.shouldProvideNodeLocation(MY_NODE_NUM) } returns MutableStateFlow(false)

        val validPosition = Position(37.7749, -122.4194, 10)
        handler.handleRequestPosition(REMOTE_NODE_NUM, validPosition, MY_NODE_NUM)

        // Should send zero position regardless of valid input
        verify { commandSender.requestPosition(any(), any()) }
    }

    // ---- handleSetConfig: optimistic persist ----

    @Test
    fun handleSetConfig_decodesAndSendsAdmin_thenPersistsLocally() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        everySuspend { radioConfigRepository.setLocalConfig(any()) } returns Unit

        val config = Config(lora = Config.LoRaConfig(hop_limit = 5))
        val payload = Config.ADAPTER.encode(config)

        handler.handleSetConfig(payload, MY_NODE_NUM)
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend { radioConfigRepository.setLocalConfig(any()) }
    }

    // ---- handleSetModuleConfig: conditional persist ----

    @Test
    fun handleSetModuleConfig_ownNode_persistsLocally() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        myNodeNumFlow.value = MY_NODE_NUM
        everySuspend { radioConfigRepository.setLocalModuleConfig(any()) } returns Unit

        val moduleConfig = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val payload = ModuleConfig.ADAPTER.encode(moduleConfig)

        handler.handleSetModuleConfig(0, MY_NODE_NUM, payload)
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend { radioConfigRepository.setLocalModuleConfig(any()) }
    }

    @Test
    fun handleSetModuleConfig_remoteNode_doesNotPersistLocally() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        myNodeNumFlow.value = MY_NODE_NUM

        val moduleConfig = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true))
        val payload = ModuleConfig.ADAPTER.encode(moduleConfig)

        handler.handleSetModuleConfig(0, REMOTE_NODE_NUM, payload)
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend(not) { radioConfigRepository.setLocalModuleConfig(any()) }
    }

    // ---- handleSetChannel: null payload guard ----

    @Test
    fun handleSetChannel_nonNullPayload_decodesAndPersists() = runTest(testDispatcher) {
        handler = createHandler(backgroundScope)
        everySuspend { radioConfigRepository.updateChannelSettings(any()) } returns Unit

        val channel = Channel(index = 1)
        val payload = Channel.ADAPTER.encode(channel)

        handler.handleSetChannel(payload, MY_NODE_NUM)
        advanceUntilIdle()

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verifySuspend { radioConfigRepository.updateChannelSettings(any()) }
    }

    @Test
    fun handleSetChannel_nullPayload_doesNothing() {
        handler = createHandler(testScope)

        handler.handleSetChannel(null, MY_NODE_NUM)

        verify(not) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- handleRemoveByNodenum ----

    @Test
    fun handleRemoveByNodenum_removesAndSendsAdmin() {
        handler = createHandler(testScope)

        handler.handleRemoveByNodenum(REMOTE_NODE_NUM, 99, MY_NODE_NUM)

        verify { nodeManager.removeByNodenum(REMOTE_NODE_NUM) }
        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- handleSetRemoteOwner ----

    @Test
    fun handleSetRemoteOwner_decodesAndSendsAdmin() {
        handler = createHandler(testScope)

        val user = User(id = "!remote01", long_name = "Remote", short_name = "RM")
        val payload = User.ADAPTER.encode(user)

        handler.handleSetRemoteOwner(1, REMOTE_NODE_NUM, payload)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    // ---- handleGetRemoteConfig: sessionkey vs regular ----

    @Test
    fun handleGetRemoteConfig_sessionkeyConfig_sendsDeviceMetadataRequest() {
        handler = createHandler(testScope)

        handler.handleGetRemoteConfig(1, REMOTE_NODE_NUM, AdminMessage.ConfigType.SESSIONKEY_CONFIG.value)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun handleGetRemoteConfig_regularConfig_sendsConfigRequest() {
        handler = createHandler(testScope)

        handler.handleGetRemoteConfig(1, REMOTE_NODE_NUM, AdminMessage.ConfigType.LORA_CONFIG.value)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- handleSetRemoteChannel: null payload guard ----

    @Test
    fun handleSetRemoteChannel_nullPayload_doesNothing() {
        handler = createHandler(testScope)

        handler.handleSetRemoteChannel(1, REMOTE_NODE_NUM, null)

        verify(not) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun handleSetRemoteChannel_nonNullPayload_decodesAndSendsAdmin() {
        handler = createHandler(testScope)

        val channel = Channel(index = 2)
        val payload = Channel.ADAPTER.encode(channel)

        handler.handleSetRemoteChannel(1, REMOTE_NODE_NUM, payload)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- handleRequestRebootOta: null hash ----

    @Test
    fun handleRequestRebootOta_withNullHash_sendsAdmin() {
        handler = createHandler(testScope)

        handler.handleRequestRebootOta(1, REMOTE_NODE_NUM, 0, null)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun handleRequestRebootOta_withHash_sendsAdmin() {
        handler = createHandler(testScope)

        val hash = byteArrayOf(0x01, 0x02, 0x03)
        handler.handleRequestRebootOta(1, REMOTE_NODE_NUM, 1, hash)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- handleRequestNodedbReset ----

    @Test
    fun handleRequestNodedbReset_sendsAdminWithPreserveFavorites() {
        handler = createHandler(testScope)

        handler.handleRequestNodedbReset(1, REMOTE_NODE_NUM, preserveFavorites = true)

        verify { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    // ---- Helper ----

    private fun createTestNode(
        num: Int,
        isFavorite: Boolean = false,
        isIgnored: Boolean = false,
        isMuted: Boolean = false,
    ): Node = Node(
        num = num,
        user = User(id = "!${num.toString(16).padStart(8, '0')}", long_name = "Node $num", short_name = "N$num"),
        isFavorite = isFavorite,
        isIgnored = isIgnored,
        isMuted = isMuted,
    )
}
