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
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.atLeast
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.ConnectionIdentity
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
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
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
import kotlin.test.assertFailsWith
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

    private fun createController(
        serviceRepository: ServiceRepository = ServiceRepositoryImpl(),
        myNodeNum: Int? = 1234,
        deviceAddress: MutableStateFlow<String?> = MutableStateFlow(null),
        connectionIdentity: MutableStateFlow<ConnectionIdentity?> = MutableStateFlow(null),
        sessionGeneration: MutableStateFlow<Long> = MutableStateFlow(0L),
        activeSession: MutableStateFlow<RadioSessionContext?>? = null,
        onDeviceAddressChanged: (() -> Unit)? = null,
        scope: CoroutineScope,
    ): RadioControllerImpl {
        every { nodeManager.myNodeNum } returns MutableStateFlow(myNodeNum)
        every { nodeManager.myDeviceId } returns MutableStateFlow(null)
        every { nodeManager.connectionIdentity } returns connectionIdentity
        every { radioInterfaceService.sessionGeneration } returns sessionGeneration
        val resolvedActiveSession =
            activeSession
                ?: MutableStateFlow(deviceAddress.value?.let { RadioSessionContext(sessionGeneration.value, it) })
        every { radioInterfaceService.activeSession } returns resolvedActiveSession
        everySuspend { radioInterfaceService.runWhileSessionActive(any(), any()) } calls
            {
                val session = it.args[0] as RadioSessionContext

                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend () -> Unit)
                if (resolvedActiveSession.value == session) {
                    block()
                    true
                } else {
                    false
                }
            }
        everySuspend { radioInterfaceService.runWithSessionLease(any(), any()) } calls
            {
                val session = it.args[0] as RadioSessionContext

                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as (suspend (RadioSessionLease) -> Unit)
                if (resolvedActiveSession.value == session) {
                    block(
                        object : RadioSessionLease {
                            override val session: RadioSessionContext = session

                            override fun isCurrent(): Boolean = resolvedActiveSession.value == session
                        },
                    )
                    true
                } else {
                    false
                }
            }
        every { meshPrefs.deviceAddress } returns deviceAddress
        every { radioInterfaceService.getDeviceAddress() } returns deviceAddress.value
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
            scope = scope,
            onDeviceAddressChanged = onDeviceAddressChanged,
        )
    }

    @Test
    fun staleAddressIdentityCannotAssociateSelectedTransport() = runTest {
        val selectedAddress = MutableStateFlow<String?>("tcp:new")
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(0L, "ble:old", 42, "device"))

        createController(scope = backgroundScope, deviceAddress = selectedAddress, connectionIdentity = identity)
        runCurrent()

        verifySuspend(exactly(0)) { databaseManager.associateDevice(any(), any(), any(), any()) }
    }

    @Test
    fun delayedOldIdentityCannotPairWithNewAddress() = runTest {
        val selectedAddress = MutableStateFlow<String?>("ble:old")
        val activeSession = MutableStateFlow<RadioSessionContext?>(RadioSessionContext(0L, "ble:old"))
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(0L, "ble:old", 42, "device"))

        createController(
            scope = backgroundScope,
            deviceAddress = selectedAddress,
            connectionIdentity = identity,
            activeSession = activeSession,
        )
        selectedAddress.value = "tcp:new"
        activeSession.value = RadioSessionContext(1L, "tcp:new")
        runCurrent()

        verifySuspend(exactly(0)) { databaseManager.associateDevice(any(), any(), any(), any()) }
    }

    @Test
    fun freshAddressBoundIdentityAssociatesExactlyOnceWithNodeFallback() = runTest {
        val selectedAddress = MutableStateFlow<String?>("tcp:selected")
        val identity = MutableStateFlow<ConnectionIdentity?>(null)
        createController(scope = backgroundScope, deviceAddress = selectedAddress, connectionIdentity = identity)
        runCurrent()

        identity.value = ConnectionIdentity(0L, "tcp:selected", 99, null)
        runCurrent()

        verifySuspend(exactly(1)) { databaseManager.associateDevice("tcp:selected", 99, null, any()) }
    }

    @Test
    fun samePhysicalIdentityAssociatesEachAddressBoundSession() = runTest {
        val selectedAddress = MutableStateFlow<String?>("ble:one")
        val activeSession = MutableStateFlow<RadioSessionContext?>(RadioSessionContext(0L, "ble:one"))
        val identity = MutableStateFlow<ConnectionIdentity?>(null)
        createController(
            scope = backgroundScope,
            deviceAddress = selectedAddress,
            connectionIdentity = identity,
            activeSession = activeSession,
        )
        runCurrent()

        identity.value = ConnectionIdentity(0L, "ble:one", 123, "device")
        runCurrent()
        identity.value = null
        selectedAddress.value = "tcp:two"
        activeSession.value = RadioSessionContext(0L, "tcp:two")
        identity.value = ConnectionIdentity(0L, "tcp:two", 123, "device")
        runCurrent()

        verifySuspend(exactly(1)) { databaseManager.associateDevice("ble:one", 123, "device", any()) }
        verifySuspend(exactly(1)) { databaseManager.associateDevice("tcp:two", 123, "device", any()) }
    }

    @Test
    fun collectorStartupDoesNotMissFirstSessionGenerationBoundary() = runTest {
        val sessionGeneration = MutableStateFlow(0L)

        createController(scope = backgroundScope, sessionGeneration = sessionGeneration)
        sessionGeneration.value = 1L
        runCurrent()

        verify(exactly(1)) { nodeManager.clearStaleConnectionIdentity(1L) }
    }

    @Test
    fun freshIdentityPublishedBeforeBoundaryCollectorRunsIsPreserved() = runTest {
        val sessionGeneration = MutableStateFlow(0L)
        val oldIdentity = ConnectionIdentity(0L, "ble:same", 42, "device")
        val identity = MutableStateFlow<ConnectionIdentity?>(oldIdentity)
        every { nodeManager.clearStaleConnectionIdentity(1L) } calls
            {
                identity.value = identity.value?.takeIf { it.sessionGeneration == 1L }
            }

        createController(scope = backgroundScope, connectionIdentity = identity, sessionGeneration = sessionGeneration)
        sessionGeneration.value = 1L
        val freshIdentity = oldIdentity.copy(sessionGeneration = 1L)
        identity.value = freshIdentity
        runCurrent()

        assertEquals(freshIdentity, identity.value)
        verify(exactly(1)) { nodeManager.clearStaleConnectionIdentity(1L) }
    }

    @Test
    fun sameAddressReconnectRejectsOldGenerationUntilFreshIdentityArrives() = runTest {
        val selectedAddress = MutableStateFlow<String?>("ble:same")
        val sessionGeneration = MutableStateFlow(2L)
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(1L, "ble:same", 42, "device"))

        createController(
            scope = backgroundScope,
            deviceAddress = selectedAddress,
            connectionIdentity = identity,
            sessionGeneration = sessionGeneration,
        )
        runCurrent()
        verifySuspend(exactly(0)) { databaseManager.associateDevice(any(), any(), any(), any()) }

        identity.value = ConnectionIdentity(2L, "ble:same", 42, "device")
        runCurrent()

        verifySuspend(exactly(1)) { databaseManager.associateDevice("ble:same", 42, "device", any()) }
    }

    @Test
    fun sessionChangeCancelsInFlightAssociation() = runTest {
        val selectedAddress = MutableStateFlow<String?>("tcp:selected")
        val sessionGeneration = MutableStateFlow(1L)
        val activeSession = MutableStateFlow<RadioSessionContext?>(RadioSessionContext(1L, "tcp:selected"))
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(1L, "tcp:selected", 99, "device"))
        val associationStarted = CompletableDeferred<Unit>()
        val associationCancelled = CompletableDeferred<Unit>()
        val releaseAssociation = CompletableDeferred<Unit>()
        everySuspend { databaseManager.associateDevice("tcp:selected", 99, "device", any()) } calls
            {
                associationStarted.complete(Unit)
                try {
                    releaseAssociation.await()
                } catch (cancellation: CancellationException) {
                    associationCancelled.complete(Unit)
                    throw cancellation
                }
            }

        createController(
            scope = backgroundScope,
            deviceAddress = selectedAddress,
            connectionIdentity = identity,
            sessionGeneration = sessionGeneration,
            activeSession = activeSession,
        )
        runCurrent()
        assertTrue(associationStarted.isCompleted)

        activeSession.value = null
        runCurrent()

        assertTrue(associationCancelled.isCompleted)
        releaseAssociation.complete(Unit)
        verifySuspend(exactly(1)) { databaseManager.associateDevice("tcp:selected", 99, "device", any()) }
    }

    @Test
    fun connectionStateAndClientNotificationDelegateToServiceRepository() = runTest {
        val serviceRepository = ServiceRepositoryImpl()
        val controller = createController(scope = backgroundScope, serviceRepository = serviceRepository)
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
        val controller = createController(scope = backgroundScope, myNodeNum = 456)
        val packet = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 1, text = "ping")

        controller.sendMessage(packet)

        verifySuspend { commandSender.sendData(packet) }
        verifySuspend { dataHandler.rememberDataPacket(packet, 456, false) }
    }

    @Test
    fun sendSharedContactCallsCommandSenderAdminAwait() = runTest {
        val controller = createController(scope = backgroundScope)
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
        val controller = createController(scope = backgroundScope)

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
    fun stopProvideLocationDelegatesToLocationManager() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.stopProvideLocation()

        verify { locationManager.stop() }
    }

    @Test
    fun setDeviceAddressSwitchesDatabaseAndTransport() = runTest {
        val controller = createController(scope = backgroundScope)
        every { meshPrefs.deviceAddress } returns MutableStateFlow("old:addr")
        val callOrder = mutableListOf<String>()
        everySuspend { radioInterfaceService.disconnect() } calls { callOrder += "disconnect" }
        every { nodeManager.setAllowNodeDbWrites(false) } calls { callOrder += "writes-off" }
        every { meshPrefs.setDeviceAddress("tcp:192.168.1.1") } calls { callOrder += "prefs" }
        everySuspend { messageProcessor.clearEarlyPackets() } calls { callOrder += "buffer" }
        everySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") } calls { callOrder += "database" }
        every { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") } calls
            {
                callOrder += "transport"
                true
            }

        controller.setDeviceAddress("tcp:192.168.1.1")
        advanceUntilIdle()

        assertEquals(listOf("disconnect", "writes-off", "buffer", "database", "prefs", "transport"), callOrder)
    }

    @Test
    fun setDeviceAddressRestoresPreviousSelectionWhenTransportRejectsNewAddress() = runTest {
        val selectedAddress = MutableStateFlow<String?>("old:addr")
        val controller = createController(scope = backgroundScope, deviceAddress = selectedAddress)
        everySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") } returns Unit
        everySuspend { databaseManager.switchActiveDatabase("old:addr") } returns Unit
        every { meshPrefs.setDeviceAddress("tcp:192.168.1.1") } calls { selectedAddress.value = "tcp:192.168.1.1" }
        every { meshPrefs.setDeviceAddress("old:addr") } calls { selectedAddress.value = "old:addr" }
        every { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") } returns false
        every { radioInterfaceService.setDeviceAddress("old:addr") } returns true

        assertFailsWith<IllegalStateException> { controller.setDeviceAddress("tcp:192.168.1.1") }

        verifySuspend { radioInterfaceService.disconnect() }
        verifySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") }
        verifySuspend { databaseManager.switchActiveDatabase("old:addr") }
        verify { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
        verify { meshPrefs.setDeviceAddress("old:addr") }
        verify { radioInterfaceService.setDeviceAddress("old:addr") }
        assertEquals("old:addr", selectedAddress.value)
    }

    @Test
    fun setDeviceAddressFailsClosedWhenDatabaseRollbackFails() = runTest {
        val selectedAddress = MutableStateFlow<String?>("old:addr")
        val controller = createController(scope = backgroundScope, deviceAddress = selectedAddress)
        everySuspend { databaseManager.switchActiveDatabase("tcp:new") } returns Unit
        everySuspend { databaseManager.switchActiveDatabase("old:addr") } throws
            IllegalStateException("database rollback failed")
        every { meshPrefs.setDeviceAddress("tcp:new") } calls { selectedAddress.value = "tcp:new" }
        every { meshPrefs.setDeviceAddress(null) } calls { selectedAddress.value = null }
        every { radioInterfaceService.setDeviceAddress("tcp:new") } returns false
        every { radioInterfaceService.setDeviceAddress(null) } returns true

        val failure = assertFailsWith<IllegalStateException> { controller.setDeviceAddress("tcp:new") }

        assertTrue(
            failure.suppressedExceptions.any { it.message == "database rollback failed" },
            "the database rollback failure must be surfaced on the original switch failure",
        )
        assertNull(selectedAddress.value, "fail-closed rollback must clear the persisted selection")
        verify(atLeast(2)) { nodeManager.setAllowNodeDbWrites(false) }
        verify { radioInterfaceService.setDeviceAddress(null) }
        verify(exactly(0)) { radioInterfaceService.setDeviceAddress("old:addr") }
        verify(exactly(0)) { meshPrefs.setDeviceAddress("old:addr") }
    }

    @Test
    fun setDeviceAddressUsesTransportSnapshotWhenPreferenceFlowLags() = runTest {
        val selectedAddress = MutableStateFlow<String?>("old:preference")
        val controller = createController(scope = backgroundScope, deviceAddress = selectedAddress)
        every { radioInterfaceService.getDeviceAddress() } returns "tcp:first"
        everySuspend { databaseManager.switchActiveDatabase("tcp:second") } returns Unit
        everySuspend { databaseManager.switchActiveDatabase("tcp:first") } returns Unit
        every { meshPrefs.setDeviceAddress("tcp:second") } calls { selectedAddress.value = "tcp:second" }
        every { meshPrefs.setDeviceAddress("tcp:first") } calls { selectedAddress.value = "tcp:first" }
        every { radioInterfaceService.setDeviceAddress("tcp:second") } returns false
        every { radioInterfaceService.setDeviceAddress("tcp:first") } returns true

        assertFailsWith<IllegalStateException> { controller.setDeviceAddress("tcp:second") }

        verifySuspend { databaseManager.switchActiveDatabase("tcp:first") }
        verify { meshPrefs.setDeviceAddress("tcp:first") }
        verify { radioInterfaceService.setDeviceAddress("tcp:first") }
        verifySuspend(exactly(0)) { databaseManager.switchActiveDatabase("old:preference") }
        assertEquals("tcp:first", selectedAddress.value)
    }

    @Test
    fun setDeviceAddressRestoresPreviousSelectionWhenInitializationFails() = runTest {
        val selectedAddress = MutableStateFlow<String?>("old:addr")
        val controller = createController(scope = backgroundScope, deviceAddress = selectedAddress)
        everySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") } returns Unit
        everySuspend { databaseManager.switchActiveDatabase("old:addr") } returns Unit
        everySuspend { messageProcessor.clearEarlyPackets() } calls
            {
                throw IllegalStateException("forced initialization failure")
            }
        every { meshPrefs.setDeviceAddress("old:addr") } calls { selectedAddress.value = "old:addr" }
        every { radioInterfaceService.setDeviceAddress("old:addr") } returns true

        assertFailsWith<IllegalStateException> { controller.setDeviceAddress("tcp:192.168.1.1") }

        verifySuspend { radioInterfaceService.disconnect() }
        verifySuspend(exactly(0)) { databaseManager.switchActiveDatabase("tcp:192.168.1.1") }
        verifySuspend { databaseManager.switchActiveDatabase("old:addr") }
        verify(exactly(0)) { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
        verify { meshPrefs.setDeviceAddress("old:addr") }
        verify { radioInterfaceService.setDeviceAddress("old:addr") }
        assertEquals("old:addr", selectedAddress.value)
    }

    @Test
    fun concurrentDeviceSelectionsAreSerialized() = runTest {
        val selectedAddress = MutableStateFlow<String?>("old:addr")
        val controller = createController(scope = backgroundScope, deviceAddress = selectedAddress)
        val firstSwitchStarted = CompletableDeferred<Unit>()
        val releaseFirstSwitch = CompletableDeferred<Unit>()
        everySuspend { databaseManager.switchActiveDatabase("tcp:first") } calls
            {
                firstSwitchStarted.complete(Unit)
                releaseFirstSwitch.await()
            }
        everySuspend { databaseManager.switchActiveDatabase("tcp:second") } returns Unit
        every { meshPrefs.setDeviceAddress("tcp:first") } calls { selectedAddress.value = "tcp:first" }
        every { meshPrefs.setDeviceAddress("tcp:second") } calls { selectedAddress.value = "tcp:second" }
        every { radioInterfaceService.setDeviceAddress("tcp:first") } returns true
        every { radioInterfaceService.setDeviceAddress("tcp:second") } returns true

        val first = launch { controller.setDeviceAddress("tcp:first") }
        firstSwitchStarted.await()
        val second = launch { controller.setDeviceAddress("tcp:second") }
        runCurrent()

        verifySuspend(exactly(0)) { databaseManager.switchActiveDatabase("tcp:second") }

        releaseFirstSwitch.complete(Unit)
        first.join()
        second.join()

        verifySuspend(exactly(1)) { databaseManager.switchActiveDatabase("tcp:first") }
        verifySuspend(exactly(1)) { databaseManager.switchActiveDatabase("tcp:second") }
        assertEquals("tcp:second", selectedAddress.value)
    }

    @Test
    fun setDeviceAddressSkipsSwitchWhenAddressUnchanged() = runTest {
        val controller = createController(scope = backgroundScope)
        every { meshPrefs.deviceAddress } returns MutableStateFlow("tcp:192.168.1.1")
        every { radioInterfaceService.getDeviceAddress() } returns "tcp:192.168.1.1"
        every { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") } returns false

        controller.setDeviceAddress("tcp:192.168.1.1")
        advanceUntilIdle()

        // Database work is skipped, while both selectors are converged on the requested address.
        verify { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") }
        verifySuspend(exactly(0)) { radioInterfaceService.disconnect() }
        verify(exactly(1)) { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
    }

    @Test
    fun setDeviceAddressDoesNotPersistOrReportSuccessWhenPreferenceMatchesButTransportRejects() = runTest {
        val selectedAddress = MutableStateFlow<String?>("tcp:192.168.1.1")
        var successCallbacks = 0
        val controller =
            createController(
                scope = backgroundScope,
                deviceAddress = selectedAddress,
                onDeviceAddressChanged = { successCallbacks += 1 },
            )
        // The persisted selection matches, but the transport has no matching active selection and rejects the request.
        every { radioInterfaceService.getDeviceAddress() } returns null
        every { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") } returns false

        assertFailsWith<IllegalStateException> { controller.setDeviceAddress("tcp:192.168.1.1") }

        assertEquals("tcp:192.168.1.1", selectedAddress.value)
        assertEquals(0, successCallbacks)
        verify(exactly(0)) { meshPrefs.setDeviceAddress("tcp:192.168.1.1") }
        verifySuspend(exactly(0)) { databaseManager.switchActiveDatabase(any()) }
    }

    @Test
    fun sendReactionPersistsToDatabase() = runTest {
        val controller = createController(scope = backgroundScope)
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
        val controller = createController(scope = backgroundScope)
        val node = Node(num = 99, user = User(id = "!node99"), isFavorite = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun setFavoriteIsNoOpWhenAlreadyInRequestedState() = runTest {
        val controller = createController(scope = backgroundScope)
        val node = Node(num = 99, user = User(id = "!node99"), isFavorite = true)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify(exactly(0)) { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun setIgnoredSendsAdminUpdatesStateAndFiltersPackets() = runTest {
        val controller = createController(scope = backgroundScope)
        val node = Node(num = 99, user = User(id = "!node99"), isIgnored = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setIgnored(99, ignored = true)
        runCurrent()

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
        verifySuspend { packetRepository.updateFilteredBySender("!node99", true) }
    }

    @Test
    fun toggleMutedSendsAdminAndUpdatesState() = runTest {
        val controller = createController(scope = backgroundScope)
        val node = Node(num = 99, user = User(id = "!node99"), isMuted = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.toggleMuted(99)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun nodeManagementReturnsEarlyWhenMyNodeNumIsNull() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = null)

        controller.setFavorite(99, favorite = true)
        controller.setIgnored(99, ignored = true)
        controller.toggleMuted(99)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun removeByNodenumAlwaysRemovesLocallyAndSendsAdminWhenConnected() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.removeByNodenum(packetId = 1, nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun removeByNodenumRemovesLocallyEvenWhenDisconnected() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = null)

        controller.removeByNodenum(packetId = 1, nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        // No admin message sent when disconnected
        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun rebootSendsAdminMessageWithDelay() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.reboot(destNum = 101, packetId = 7)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun shutdownSendsAdminMessage() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.shutdown(destNum = 101, packetId = 8)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun factoryResetSendsAdminMessage() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.factoryReset(destNum = 101, packetId = 9)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun nodedbResetSendsAdminMessage() = runTest {
        val controller = createController(scope = backgroundScope)

        controller.nodedbReset(destNum = 101, packetId = 10, preserveFavorites = true)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun setTimeSendsAdminMessageWithCurrentEpochSeconds() = runTest {
        val controller = createController(scope = backgroundScope)

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
        val controller = createController(scope = backgroundScope)

        controller.refreshMetadata(destNum = 101)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
    }

    @Test
    fun editLocalSettingsChannelWritesDoNotMirrorToLocalCache() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = 1234)

        controller.editLocalSettings {
            setChannel(Channel(index = 0, role = Channel.Role.PRIMARY, settings = ChannelSettings(name = "A")))
            setChannel(Channel(index = 1, role = Channel.Role.SECONDARY, settings = ChannelSettings(name = "B")))
        }
        advanceUntilIdle()

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
        val controller = createController(scope = backgroundScope)
        // A QR-scanned contact arrives with manually_verified = false (proto default).
        val contact = SharedContact(node_num = 42, user = User(id = "!0000002a", long_name = "Test"))

        var sentMessage: AdminMessage? = null
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                sentMessage = (it.args[3] as () -> AdminMessage)()
            }

        controller.importContact(contact)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        // The verification state encoded by the sharer is honored as-is, not forced to true.
        assertEquals(false, sentMessage?.add_contact?.manually_verified)
        verify { nodeManager.handleReceivedUser(42, any(), any(), false) }
    }

    @Test
    fun importContactPreservesEncodedVerificationState() = runTest {
        val controller = createController(scope = backgroundScope)
        // A contact shared as already verified stays verified on import.
        val contact =
            SharedContact(node_num = 42, user = User(id = "!0000002a", long_name = "Test"), manually_verified = true)

        var sentMessage: AdminMessage? = null
        everySuspend { commandSender.sendAdmin(any(), any(), any(), any()) } calls
            {
                @Suppress("UNCHECKED_CAST")
                sentMessage = (it.args[3] as () -> AdminMessage)()
            }

        controller.importContact(contact)

        verifySuspend { commandSender.sendAdmin(any(), any(), any(), any()) }
        assertEquals(true, sentMessage?.add_contact?.manually_verified)
        verify { nodeManager.handleReceivedUser(42, any(), any(), true) }
    }

    @Test
    fun setHamModeSendsAdminWithEchoedLoraValuesAndUpdatesUser() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = 123)
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
        val controller = createController(scope = backgroundScope, myNodeNum = 123)
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
        val controller = createController(scope = backgroundScope, myNodeNum = 123)

        controller.setHamMode(456, HamParameters(call_sign = "KK7ABC", short_name = "KK7A"), 42)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    @Test
    fun importContactReturnsEarlyWhenDisconnected() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = null)
        val contact = SharedContact(node_num = 42, user = User(id = "!0000002a"))

        controller.importContact(contact)

        verifySuspend(exactly(0)) { commandSender.sendAdmin(any(), any(), any(), any()) }
    }
}
