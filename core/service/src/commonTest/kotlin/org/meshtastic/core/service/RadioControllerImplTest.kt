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
import dev.mokkery.matcher.capture.capture
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.atLeast
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.database.DatabaseManager
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.Reaction
import org.meshtastic.core.repository.ConnectionIdentity
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.MeshPrefs
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketHandler
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.REMOTE_READ_TIMEOUT
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.HamParameters
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.OTAMode
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import org.meshtastic.sdk.AdminApi
import org.meshtastic.sdk.AdminEdit
import org.meshtastic.sdk.AdminResult
import org.meshtastic.sdk.ChannelIndex
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.NodeId
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport
import org.meshtastic.sdk.testing.toFrame
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val TEST_PUBLIC_KEY = ByteArray(32) { 0x2B.toByte() }.toByteString()

private val EDIT_SESSION = RadioSessionContext(generation = 7L, address = "tcp:edit")

class RadioControllerImplTest {

    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val packetHandler: PacketHandler = mock(MockMode.autofill)
    private val nodeManager: NodeManager = mock(MockMode.autofill)
    private val radioInterfaceService: RadioInterfaceService = mock(MockMode.autofill)
    private val locationManager: MeshLocationManager = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val dataHandler: MeshDataHandler = mock(MockMode.autofill)
    private val analytics: PlatformAnalytics = mock(MockMode.autofill)
    private val meshPrefs: MeshPrefs = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val databaseManager: DatabaseManager = mock(MockMode.autofill)
    private val serviceNotifications: MeshNotificationManager = mock(MockMode.autofill)
    private val meshConfigFlowManager: MeshConfigFlowManager = mock(MockMode.autofill)
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
        sessionScope: CoroutineScope = scope,
    ): RadioControllerImpl {
        every { nodeManager.myNodeNum } returns MutableStateFlow(myNodeNum)
        every { nodeManager.myDeviceId } returns MutableStateFlow(null)
        every { nodeManager.reportsHeardOnCurrentLora } returns MutableStateFlow(false)
        every { radioConfigRepository.localConfigFlow } returns MutableStateFlow(LocalConfig.Builder().build())
        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet.Builder().build())
        every { nodeManager.connectionIdentity } returns connectionIdentity
        every { radioInterfaceService.sessionGeneration } returns sessionGeneration
        val resolvedActiveSession =
            activeSession
                ?: MutableStateFlow(deviceAddress.value?.let { RadioSessionContext(sessionGeneration.value, it) })
        every { radioInterfaceService.activeSession } returns resolvedActiveSession
        every { radioInterfaceService.isSessionActive(any()) } calls { resolvedActiveSession.value == it.args[0] }
        every { radioInterfaceService.sessionScope(any()) } calls
            {
                if (resolvedActiveSession.value == it.args[0]) sessionScope else null
            }
        every { meshPrefs.deviceAddress } returns deviceAddress
        every { radioInterfaceService.getDeviceAddress() } returns deviceAddress.value
        return RadioControllerImpl(
            serviceRepository = serviceRepository,
            nodeRepository = nodeRepository,
            tracerouteHandler = mock(MockMode.autofill),
            neighborInfoHandler = mock(MockMode.autofill),
            packetHandler = packetHandler,
            nodeManager = nodeManager,
            radioInterfaceService = radioInterfaceService,
            sessionAdmin = SessionAdmin(radioInterfaceService, scope.asServiceScope()),
            locationManager = locationManager,
            packetRepository = lazy { packetRepository },
            dataHandler = lazy { dataHandler },
            analytics = analytics,
            meshPrefs = meshPrefs,
            uiPrefs = uiPrefs,
            databaseManager = databaseManager,
            serviceNotifications = serviceNotifications,
            meshConfigFlowManager = lazy { meshConfigFlowManager },
            radioOperationLock = RadioOperationLock(),
            radioConfigRepository = radioConfigRepository,
            scope = scope.asServiceScope(),
            deviceAddressChangeHook = { onDeviceAddressChanged?.invoke() },
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

    // Regression: association ran outside its session's scope, so teardown, which cancels that scope before the
    // session stops being active, left it running to write for a closed session.
    @Test
    fun sessionTeardownCancelsInFlightAssociation() = runTest {
        val selectedAddress = MutableStateFlow<String?>("tcp:selected")
        val activeSession = MutableStateFlow<RadioSessionContext?>(RadioSessionContext(1L, "tcp:selected"))
        val identity = MutableStateFlow<ConnectionIdentity?>(ConnectionIdentity(1L, "tcp:selected", 99, "device"))
        val associationStarted = CompletableDeferred<Unit>()
        val associationCancelled = CompletableDeferred<Unit>()
        everySuspend { databaseManager.associateDevice("tcp:selected", 99, "device", any()) } calls
            {
                associationStarted.complete(Unit)
                try {
                    awaitCancellation()
                } catch (cancellation: CancellationException) {
                    associationCancelled.complete(Unit)
                    throw cancellation
                }
            }
        val sessionScope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))

        createController(
            scope = backgroundScope,
            deviceAddress = selectedAddress,
            connectionIdentity = identity,
            sessionGeneration = MutableStateFlow(1L),
            activeSession = activeSession,
            sessionScope = sessionScope,
        )
        runCurrent()
        assertTrue(associationStarted.isCompleted)

        sessionScope.cancel()
        runCurrent()

        assertTrue(associationCancelled.isCompleted)
    }

    @Test
    fun connectionStateAndClientNotificationDelegateToServiceRepository() = runTest {
        val serviceRepository = ServiceRepositoryImpl()
        val controller = createController(scope = backgroundScope, serviceRepository = serviceRepository)
        val notification = ClientNotification.Builder().build()

        assertSame(serviceRepository.connectionState, controller.connectionState)
        assertSame(serviceRepository.clientNotification, controller.clientNotification)

        serviceRepository.setConnectionState(ConnectionState.Connecting(attempt = 1))
        serviceRepository.setClientNotification(notification)

        assertEquals(ConnectionState.Connecting(attempt = 1), controller.connectionState.value)
        assertSame(notification, controller.clientNotification.value)

        controller.clearClientNotification()

        assertNull(serviceRepository.clientNotification.value)
    }

    @Test
    fun sendMessageDelegatesToPacketHandler() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = 456)
        val packet = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 1, text = "ping")

        controller.sendMessage(packet)

        verifySuspend { packetHandler.sendData(packet) }
        verifySuspend { dataHandler.rememberDataPacket(packet, 456, false) }
    }

    @Test
    fun sendMessageDoesNotPersistWhenQueueRejects() = runTest {
        val controller = createController(scope = backgroundScope, myNodeNum = 456)
        val packet = DataPacket(to = NodeAddress.ID_BROADCAST, channel = 1, text = "ping")
        val rejection = PacketQueueRejectedException("Data packet")
        everySuspend { packetHandler.sendData(packet) } throws rejection

        val failure = assertFailsWith<PacketQueueRejectedException> { controller.sendMessage(packet) }

        assertSame(rejection, failure)
        verifySuspend(exactly(0)) { dataHandler.rememberDataPacket(any(), any(), any()) }
    }

    @Test
    fun localChannelDoesNotPersistWithoutARadioSession() = runTest {
        val controller =
            editController(backgroundScope, recordingAdmin(), myNodeNum = 456, activeSession = MutableStateFlow(null))
        val channel =
            Channel.Builder()
                .also { wb ->
                    wb.index = 0
                    wb.role = Channel.Role.PRIMARY
                    wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "Primary" }.build()
                }
                .build()

        assertFailsWith<PacketQueueRejectedException> { controller.setLocalChannel(channel) }

        verifySuspend(exactly(0)) { radioConfigRepository.updateChannelSettings(any()) }
    }

    @Test
    fun fixedPositionWithoutASessionIsRejectedBeforeTheLocalUpdate() = runTest {
        val controller = editController(backgroundScope, recordingAdmin(), activeSession = MutableStateFlow(null))
        val position = Position(latitude = 1.0, longitude = 2.0, altitude = 3)

        assertFailsWith<PacketQueueRejectedException> { controller.setFixedPosition(123, position) }

        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun fixedPositionIsSentThroughTheSdkAndProjectedLocally() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.setFixedPosition(1234, Position(latitude = 1.0, longitude = 2.0, altitude = 3))

        assertEquals(listOf("fixed_position:10000000"), admin.commands)
        verify(exactly(1)) { nodeManager.handleReceivedPosition(1234, 1234, any(), any(), any()) }
    }

    @Test
    fun removingAFixedPositionSendsTheRemovalWithoutProjectingZero() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.setFixedPosition(99, Position(latitude = 0.0, longitude = 0.0, altitude = 0))

        assertEquals(listOf("remove_fixed_position"), admin.commands)
        assertEquals(listOf(NodeId(99)), admin.targets)
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun fixedPositionIsRejectedBeforeSendingWhenTheLocalNodeIsUnknown() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)

        assertFailsWith<LocalNodeUnavailableException> {
            controller.setFixedPosition(99, Position(latitude = 1.0, longitude = 2.0, altitude = 3))
        }

        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun sendSharedContactReportsTheSdkResult() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val nodeNum = 321
        val user =
            User.Builder()
                .also { wb ->
                    wb.id = NodeAddress.numToDefaultId(nodeNum)
                    wb.long_name = "Remote Node"
                    wb.short_name = "RN"
                    wb.public_key = TEST_PUBLIC_KEY
                }
                .build()
        val node = Node(num = nodeNum, user = user, manuallyVerified = true)
        every { nodeRepository.getNode(NodeAddress.numToDefaultId(nodeNum)) } returns node

        assertTrue(controller.sendSharedContact(nodeNum))
        assertEquals(listOf("contact:321:true"), admin.commands)

        admin.contactResult = AdminResult.Timeout
        assertFalse(controller.sendSharedContact(nodeNum))
    }

    @Test
    fun remoteReadsGoThroughTheSdkWithTheRemoteReadTimeout() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        assertTrue(controller.getOwner(destNum = 101) is AdminResult.Success)
        controller.getConfig(destNum = 102, configType = 2)
        controller.getModuleConfig(destNum = 103, moduleConfigType = 4)
        controller.getChannel(destNum = 104, index = 6)
        controller.getRingtone(destNum = 105)
        controller.getCannedMessages(destNum = 106)
        controller.getDeviceConnectionStatus(destNum = 107)
        controller.getDeviceMetadata(destNum = 108)

        assertEquals(
            listOf("owner", "config:2", "module:4", "channel:6", "ringtone", "canned", "connection", "metadata"),
            admin.commands,
        )
        assertEquals((101..108).map(::NodeId), admin.targets)
        assertEquals(List(8) { REMOTE_READ_TIMEOUT }, admin.rpcTimeouts)
    }

    @Test
    fun localReadsUseTheSessionAdminDirectly() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.getOwner(destNum = 1234)

        assertEquals(listOf("owner"), admin.commands)
        assertTrue(admin.targets.isEmpty() && admin.rpcTimeouts.isEmpty())
    }

    @Test
    fun adminCallsWithoutASessionAreUnreachable() = runTest {
        val controller = editController(backgroundScope, recordingAdmin(), activeSession = MutableStateFlow(null))

        assertEquals(AdminResult.NodeUnreachable, controller.getOwner(destNum = 101))
        assertEquals(AdminResult.NodeUnreachable, controller.reboot(destNum = 101))
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
        everySuspend { databaseManager.switchActiveDatabase("tcp:192.168.1.1") } calls { callOrder += "database" }
        every { radioInterfaceService.setDeviceAddress("tcp:192.168.1.1") } calls
            {
                callOrder += "transport"
                true
            }

        controller.setDeviceAddress("tcp:192.168.1.1")
        advanceUntilIdle()

        assertEquals(listOf("disconnect", "writes-off", "database", "prefs", "transport"), callOrder)
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
        every { nodeManager.setAllowNodeDbWrites(false) } calls
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
        val user =
            User.Builder()
                .also { wb ->
                    wb.id = "!abcd1234"
                    wb.long_name = "Test"
                    wb.short_name = "T"
                }
                .build()
        val node = Node(num = 1234, user = user)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(1234 to node)
        every { nodeManager.getMyId() } returns "!abcd1234"
        // Production PacketHandlerImpl.sendData stamps QUEUED on successful queue admission. DataPacket.status is
        // nullable but defaults to UNKNOWN, so the default value does not exercise the controller's null fallback.
        // Mirror the production stamp here so this assertion exercises the realistic happy-path contract; the explicit
        // ERROR-stamping path is covered by sendReactionPersistsStatusStampedByPacketHandler.
        everySuspend { packetHandler.sendData(any()) } calls
            {
                (it.args[0] as DataPacket).status = MessageStatus.QUEUED
            }

        val reactions = mutableListOf<Reaction>()
        everySuspend { packetRepository.insertReaction(capture(reactions), any()) } returns Unit

        controller.sendReaction(emoji = "👍", replyId = 42, contactKey = "0!dest5678")

        // Reaction must be persisted (not fire-and-forget) with the queue-admission QUEUED status.
        verifySuspend { packetHandler.sendData(any()) }
        assertEquals(MessageStatus.QUEUED, reactions.single().status)
    }

    @Test
    fun sendReactionDoesNotPersistWhenQueueRejects() = runTest {
        val controller = createController(scope = backgroundScope)
        val user =
            User.Builder()
                .also { wb ->
                    wb.id = "!abcd1234"
                    wb.long_name = "Test"
                    wb.short_name = "T"
                }
                .build()
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(1234 to Node(num = 1234, user = user))
        every { nodeManager.getMyId() } returns "!abcd1234"
        val rejection = PacketQueueRejectedException("Reaction")
        everySuspend { packetHandler.sendData(any()) } calls
            {
                (it.args[0] as DataPacket).status = MessageStatus.ERROR
                throw rejection
            }

        val failure =
            assertFailsWith<PacketQueueRejectedException> {
                controller.sendReaction(emoji = "👍", replyId = 42, contactKey = "0!dest5678")
            }

        assertSame(rejection, failure)
        verifySuspend(exactly(0)) { packetRepository.insertReaction(any(), any()) }
    }

    @Test
    fun sendReactionFallsBackToQueuedWhenSenderLeavesStatusNull() = runTest {
        val controller = createController(scope = backgroundScope)
        val user =
            User.Builder()
                .also { wb ->
                    wb.id = "!abcd1234"
                    wb.long_name = "Test"
                    wb.short_name = "T"
                }
                .build()
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(1234 to Node(num = 1234, user = user))
        every { nodeManager.getMyId() } returns "!abcd1234"
        everySuspend { packetHandler.sendData(any()) } calls { (it.args[0] as DataPacket).status = null }
        val reactions = mutableListOf<Reaction>()
        everySuspend { packetRepository.insertReaction(capture(reactions), any()) } returns Unit

        controller.sendReaction(emoji = "👍", replyId = 42, contactKey = "0!dest5678")

        assertEquals(MessageStatus.QUEUED, reactions.single().status)
    }

    @Test
    fun setFavoriteSendsTheSdkCommandAndUpdatesState() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isFavorite = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        assertEquals(listOf("favorite:99:true"), admin.commands)
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun unfavoritingClearsTheNodesLowBatteryWarning() = runTest {
        val controller = editController(backgroundScope, recordingAdmin())
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isFavorite = true)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = false)

        verify { serviceNotifications.cancelLowBatteryNotification(99) }
    }

    @Test
    fun setFavoriteIsNoOpWhenAlreadyInRequestedState() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isFavorite = true)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setFavorite(99, favorite = true)

        assertTrue(admin.commands.isEmpty())
        verify(exactly(0)) { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun setIgnoredSendsTheSdkCommandUpdatesStateAndFiltersPackets() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isIgnored = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.setIgnored(99, ignored = true)
        runCurrent()

        assertEquals(listOf("ignored:99:true"), admin.commands)
        verify { nodeManager.updateNode(any(), any(), any()) }
        verifySuspend { packetRepository.updateFilteredBySender("!node99", true) }
    }

    @Test
    fun toggleMutedSendsTheSdkCommandAndUpdatesState() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isMuted = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        controller.toggleMuted(99)

        assertEquals(listOf("muted:99"), admin.commands)
        verify { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun nodeCommandsWithoutASessionAreRejectedBeforeTheLocalUpdate() = runTest {
        val controller = editController(backgroundScope, recordingAdmin(), activeSession = MutableStateFlow(null))
        val node = Node(num = 99, user = User.Builder().also { wb -> wb.id = "!node99" }.build(), isFavorite = false)
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(99 to node)

        assertFailsWith<PacketQueueRejectedException> { controller.setFavorite(99, favorite = true) }

        verify(exactly(0)) { nodeManager.updateNode(any(), any(), any()) }
    }

    @Test
    fun nodeManagementReturnsEarlyWhenMyNodeNumIsNull() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)

        controller.setFavorite(99, favorite = true)
        controller.setIgnored(99, ignored = true)
        controller.toggleMuted(99)

        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun removeByNodenumAlwaysRemovesLocallyAndSendsTheSdkCommandWhenConnected() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.removeByNodenum(nodeNum = 55)
        advanceUntilIdle()

        verifySuspend { nodeManager.removeByNodenum(55) }
        assertEquals(listOf("remove:55"), admin.commands)
        verify(exactly(0)) { nodeManager.cancelPendingRemoval(any()) }
    }

    // Regression: a removal the radio refused left the node suppressed for the rest of the session.
    @Test
    fun removeByNodenumEndsThePendingRemovalWhenTheRadioRefuses() = runTest {
        val admin = recordingAdmin().apply { removeResult = AdminResult.Unauthorized }
        val controller = editController(backgroundScope, admin)

        controller.removeByNodenum(nodeNum = 55)
        advanceUntilIdle()

        verify { nodeManager.cancelPendingRemoval(55) }
    }

    // Regression: a commit the SDK rethrew escaped as MeshtasticException instead of the transaction failure.
    @Test
    fun editSettingsReportsACommitTheSdkRethrowsAsATransactionFailure() = runTest {
        val admin = recordingAdmin().apply { beforeCommit = { throw MeshtasticException.NotConnected() } }
        val controller = editController(backgroundScope, admin)

        assertFailsWith<EditSettingsTransactionException> { controller.editLocalSettings { setConfig(deviceConfig) } }
    }

    @Test
    fun removeByNodenumRetainsLocalRemovalWithoutASession() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, activeSession = MutableStateFlow(null))

        controller.removeByNodenum(nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun sendInputEventSendsTheSdkCommandAndIsDroppedWithoutASession() = runTest {
        val activeSession = MutableStateFlow<RadioSessionContext?>(EDIT_SESSION)
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, activeSession = activeSession)

        controller.sendInputEvent(eventCode = 7)
        activeSession.value = null
        controller.sendInputEvent(eventCode = 8)

        assertEquals(listOf("input:7"), admin.commands)
    }

    @Test
    fun removeByNodenumRemovesLocallyEvenWhenDisconnected() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)

        controller.removeByNodenum(nodeNum = 55)

        verifySuspend { nodeManager.removeByNodenum(55) }
        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun lifecycleCommandsReachTheTargetNodeWithTheirArguments() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.reboot(destNum = 101)
        controller.shutdown(destNum = 101)
        controller.factoryReset(destNum = 101)
        controller.nodedbReset(destNum = 101, preserveFavorites = false)
        controller.setTime(destNum = 101)
        controller.rebootToDfu(nodeNum = 101)

        assertEquals(
            listOf("reboot:5s", "shutdown:5s", "factoryReset:false", "nodeDbReset:false", "time", "dfu"),
            admin.commands,
        )
        assertEquals(List(6) { NodeId(101) }, admin.targets)
    }

    @Test
    fun playRingtoneAsksTheTargetNodeToPlay() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.playRingtone(destNum = 101)
        runCurrent()

        assertEquals(listOf("playRingtone"), admin.commands)
        assertEquals(listOf(NodeId(101)), admin.targets)
    }

    @Test
    fun esp32OtaRequestCarriesTheRebootModeAndFirmwareHash() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val hash = ByteArray(32) { it.toByte() }

        controller.requestRebootOta(destNum = 1234, mode = OTAMode.OTA_BLE.value, hash = hash)
        runCurrent()

        val event = admin.otaEvents.single()
        assertEquals(OTAMode.OTA_BLE, event.reboot_ota_mode)
        assertEquals(hash.toByteString(), event.ota_hash)
        assertTrue(admin.targets.isEmpty())
    }

    /** SDK admin double: records transaction writes and, like the SDK, commits even when the block throws. */
    private class RecordingSdkAdmin(private val delegate: AdminApi) : AdminApi by delegate {
        val writes = mutableListOf<String>()
        val targets = mutableListOf<NodeId>()
        var transactions = 0
        var commits = 0
        var commitFailure: AdminResult<Nothing>? = null
        var beforeCommit: suspend () -> Unit = {}

        val commands = mutableListOf<String>()
        val rpcTimeouts = mutableListOf<Duration>()
        var contactResult: AdminResult<Unit> = AdminResult.Success(Unit)

        override fun forNode(dest: NodeId): AdminApi = also { targets += dest }

        override fun withRpcTimeout(timeout: Duration): AdminApi = also { rpcTimeouts += timeout }

        override suspend fun getOwner() = read("owner", User.Builder().build())

        override suspend fun getConfig(type: AdminMessage.ConfigType) =
            read("config:${type.value}", Config.Builder().build())

        override suspend fun getModuleConfig(type: AdminMessage.ModuleConfigType) =
            read("module:${type.value}", ModuleConfig.Builder().build())

        override suspend fun getChannel(index: ChannelIndex) = read("channel:${index.raw}", Channel.Builder().build())

        override suspend fun getRingtone() = read("ringtone", "")

        override suspend fun getCannedMessages() = read("canned", "")

        override suspend fun getDeviceConnectionStatus() =
            read("connection", org.meshtastic.proto.DeviceConnectionStatus.Builder().build())

        override suspend fun getDeviceMetadata() =
            read("metadata", org.meshtastic.proto.DeviceMetadata.Builder().build())

        override suspend fun setTime(at: kotlin.time.Instant?) = record("time")

        override suspend fun reboot(after: Duration) = record("reboot:$after")

        override suspend fun shutdown(after: Duration) = record("shutdown:$after")

        override suspend fun factoryReset(preserveBleBonds: Boolean) = record("factoryReset:$preserveBleBonds")

        override suspend fun nodeDbReset(preserveFavorites: Boolean) = record("nodeDbReset:$preserveFavorites")

        override suspend fun enterDfuMode() = record("dfu")

        override suspend fun playRingtone() = record("playRingtone")

        val otaEvents = mutableListOf<AdminMessage.OTAEvent>()

        override suspend fun otaRequest(event: AdminMessage.OTAEvent): AdminResult<Unit> {
            otaEvents += event
            return AdminResult.Success(Unit)
        }

        override suspend fun setFixedPosition(position: org.meshtastic.proto.Position) =
            record("fixed_position:${position.latitude_i}")

        override suspend fun removeFixedPosition() = record("remove_fixed_position")

        override suspend fun setRingtone(rtttl: String) = record("ringtone:$rtttl")

        override suspend fun setCannedMessages(messages: String) = record("canned:$messages")

        private fun <T> read(command: String, value: T): AdminResult<T> {
            commands += command
            return AdminResult.Success(value)
        }

        override suspend fun setFavorite(node: NodeId, favorite: Boolean) = record("favorite:${node.raw}:$favorite")

        override suspend fun setIgnored(node: NodeId, ignored: Boolean) = record("ignored:${node.raw}:$ignored")

        override suspend fun toggleMuted(node: NodeId) = record("muted:${node.raw}")

        var removeResult: AdminResult<Unit> = AdminResult.Success(Unit)

        override suspend fun removeNode(node: NodeId): AdminResult<Unit> {
            commands += "remove:${node.raw}"
            return removeResult
        }

        override suspend fun sendInputEvent(event: AdminMessage.InputEvent) = record("input:${event.event_code}")

        override suspend fun addContact(contact: SharedContact): AdminResult<Unit> {
            commands += "contact:${contact.node_num}:${contact.manually_verified}"
            return contactResult
        }

        private fun record(command: String): AdminResult<Unit> {
            commands += command
            return AdminResult.Success(Unit)
        }

        override suspend fun <T> editSettings(block: suspend AdminEdit.() -> T): AdminResult<T> {
            transactions++
            val payload =
                try {
                    recordingEdit.block()
                } catch (failure: Throwable) {
                    commit()
                    throw failure
                }
            commit()
            return commitFailure ?: AdminResult.Success(payload)
        }

        private suspend fun commit() = withContext(NonCancellable) {
            beforeCommit()
            commits++
        }

        private val recordingEdit =
            object : AdminEdit {
                override suspend fun setConfig(config: Config) {
                    writes += "config"
                }

                override suspend fun setModuleConfig(config: ModuleConfig) {
                    writes += "module_config"
                }

                override suspend fun setOwner(user: User) {
                    writes += "owner:${user.long_name}"
                }

                override suspend fun setChannel(channel: Channel) {
                    writes += "channel:${channel.index}"
                }

                override suspend fun setFavorite(node: NodeId, favorite: Boolean) {
                    writes += "favorite"
                }

                override suspend fun setIgnored(node: NodeId, ignored: Boolean) {
                    writes += "ignored"
                }

                override suspend fun setFixedPosition(position: org.meshtastic.proto.Position) {
                    writes += "fixed_position:${position.latitude_i}"
                }

                override suspend fun removeFixedPosition() {
                    writes += "remove_fixed_position"
                }
            }
    }

    private fun editController(
        scope: CoroutineScope,
        admin: RecordingSdkAdmin,
        myNodeNum: Int? = 1234,
        activeSession: MutableStateFlow<RadioSessionContext?> = MutableStateFlow(EDIT_SESSION),
    ): RadioControllerImpl {
        val controller = createController(scope = scope, myNodeNum = myNodeNum, activeSession = activeSession)
        every { radioInterfaceService.adminForSession(any()) } returns admin
        return controller
    }

    private fun recordingAdmin() = RecordingSdkAdmin(mock(MockMode.autofill))

    private fun TestScope.hamClient(lora: Config.LoRaConfig? = null): Pair<FakeRadioTransport, RadioClient> {
        val frames =
            listOfNotNull(
                lora?.let { l ->
                    FromRadio.Builder()
                        .also { fr -> fr.config = Config.Builder().also { c -> c.lora = l }.build() }
                        .build()
                        .toFrame()
                },
            )
        val transport =
            FakeRadioTransport(
                identity = TransportIdentity("fake:ham"),
                frames = frames,
                autoHandshake = true,
                autoQueueStatus = true,
                nodeNum = 123,
            )
        val client =
            RadioClient.Builder()
                .transport(transport)
                .storage(InMemoryStorageProvider())
                .autoSyncTimeOnConnect(false)
                .coroutineContext(backgroundScope.coroutineContext)
                .rpcTimeout(60.seconds)
                .sendAckTimeout(60.seconds)
                .build()
        return transport to client
    }

    private fun hamAdminOf(packet: MeshPacket): AdminMessage? =
        packet.decoded?.takeIf { it.portnum == PortNum.ADMIN_APP }?.let { AdminMessage.ADAPTER.decode(it.payload) }

    private suspend fun TestScope.setHamAcked(
        controller: RadioControllerImpl,
        transport: FakeRadioTransport,
        destNum: Int,
        params: HamParameters,
    ): AdminResult<Unit> {
        val result = async { controller.setHamMode(destNum, params) }
        runCurrent()
        transport
            .outboundPackets()
            .lastOrNull { hamAdminOf(it)?.set_ham_mode != null }
            ?.let { transport.injectRoutingAck(it.id) }
        runCurrent()
        return result.await()
    }

    private fun channel(index: Int, role: Channel.Role) = Channel.Builder()
        .also { wb ->
            wb.index = index
            wb.role = role
            wb.settings = ChannelSettings.Builder().also { wb -> wb.name = "Ch$index" }.build()
        }
        .build()

    private val committedOwner =
        User.Builder()
            .also { wb ->
                wb.id = "!000004d2"
                wb.long_name = "Committed owner"
            }
            .build()

    private val deviceConfig = Config.Builder().also { wb -> wb.device = Config.DeviceConfig.Builder().build() }.build()

    private val statusModuleConfig =
        ModuleConfig.Builder()
            .also { wb ->
                wb.statusmessage =
                    ModuleConfig.StatusMessageConfig.Builder().also { wb -> wb.node_status = "Ready" }.build()
            }
            .build()

    @Test
    fun editLocalSettingsWritesChannelsInOrderWithoutMirroringThemLocally() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.editLocalSettings {
            setChannel(channel(0, Channel.Role.PRIMARY))
            setChannel(channel(1, Channel.Role.SECONDARY))
        }
        advanceUntilIdle()

        assertEquals(listOf("channel:0", "channel:1"), admin.writes)
        assertEquals(1, admin.commits)
        assertTrue(admin.targets.isEmpty())
        // The batch operation owns the complete slot set and reconciles it after the transaction.
        verifySuspend(exactly(0)) { radioConfigRepository.updateChannelSettings(any()) }
    }

    @Test
    fun editLocalSettingsLeavesConfigAndOwnerToTheSdkAndProjectsTheFixedPositionAfterTheCommit() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val fixedPosition = Position(latitude = 47.6, longitude = -122.3, altitude = 42)
        admin.beforeCommit = {
            verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
        }

        controller.editLocalSettings {
            setOwner(committedOwner)
            setConfig(deviceConfig)
            setModuleConfig(statusModuleConfig)
            setFixedPosition(fixedPosition)
        }
        advanceUntilIdle()

        assertEquals(1, admin.commits)
        verify(exactly(1)) { nodeManager.handleReceivedPosition(1234, 1234, any(), any(), any()) }
        // The SDK reports the committed local config, module config and owner as session events.
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
        verifySuspend(exactly(0)) { nodeManager.updateNodeStatusAndPersist(any(), any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalModuleConfig(any()) }
    }

    @Test
    fun editSettingsRetainsInterleavedStagedProjections() = runTest {
        val controller = editController(backgroundScope, recordingAdmin())

        controller.editSettings(destNum = 99) {
            coroutineScope {
                launch { setOwner(committedOwner) }
                launch { setFixedPosition(Position(latitude = 47.6, longitude = -122.3, altitude = 42)) }
            }
        }
        advanceUntilIdle()

        verify(exactly(1)) { nodeManager.handleReceivedUser(99, committedOwner, any(), any(), any()) }
        verify(exactly(1)) { nodeManager.handleReceivedPosition(99, 1234, any(), any(), any()) }
    }

    @Test
    fun editSettingsRemovesFixedPositionWithoutProjectingZero() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.editLocalSettings {
            setFixedPosition(Position(latitude = 0.0, longitude = 0.0, altitude = 0, time = 1, satellitesInView = 9))
        }
        advanceUntilIdle()

        assertEquals(listOf("remove_fixed_position"), admin.writes)
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun editSettingsRejectsFixedPositionBeforeWritingItWhenLocalNodeIsUnavailable() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)

        assertFailsWith<LocalNodeUnavailableException> {
            controller.editSettings(destNum = 99) {
                setFixedPosition(Position(latitude = 47.6, longitude = -122.3, altitude = 42))
            }
        }

        assertTrue(admin.writes.isEmpty())
        assertEquals(1, admin.commits)
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun editSettingsContinuesProjectionsAfterOneFails() = runTest {
        val controller = editController(backgroundScope, recordingAdmin())
        every { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) } throws
            IllegalStateException("projection failed")

        controller.editSettings(destNum = 99) {
            setOwner(committedOwner)
            setFixedPosition(Position(latitude = 47.6, longitude = -122.3, altitude = 42))
        }
        advanceUntilIdle()

        verify(exactly(1)) { nodeManager.handleReceivedPosition(99, 1234, any(), any(), any()) }
    }

    @Test
    fun editSettingsDiscardsStagedLocalProjectionsWhenTheCommitFails() = runTest {
        val admin = recordingAdmin().apply { commitFailure = AdminResult.Timeout }
        val controller = editController(backgroundScope, admin)

        assertFailsWith<EditSettingsTransactionException> {
            controller.editLocalSettings {
                setOwner(committedOwner)
                setConfig(deviceConfig)
                setModuleConfig(statusModuleConfig)
                setFixedPosition(Position(latitude = 1.0, longitude = 2.0, altitude = 3))
            }
        }
        advanceUntilIdle()

        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
        verifySuspend(exactly(0)) { nodeManager.updateNodeStatusAndPersist(any(), any()) }
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalModuleConfig(any()) }
    }

    @Test
    fun editSettingsRethrowsABlockFailureAfterCommittingAndDiscardsProjections() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val blockFailure = IllegalArgumentException("transaction failed after writes")

        val failure =
            assertFailsWith<IllegalArgumentException> {
                controller.editLocalSettings {
                    setOwner(committedOwner)
                    setConfig(deviceConfig)
                    throw blockFailure
                }
            }
        advanceUntilIdle()

        assertSame(blockFailure, failure)
        assertEquals(1, admin.commits)
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
    }

    @Test
    fun editSettingsAddressesARemoteNodeAndProjectsOnlyItsOwner() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)

        controller.editSettings(destNum = 99) {
            setOwner(committedOwner)
            setConfig(deviceConfig)
        }
        advanceUntilIdle()

        assertEquals(listOf(NodeId(99)), admin.targets)
        verify(exactly(1)) { nodeManager.handleReceivedUser(99, committedOwner, any(), any(), any()) }
        verifySuspend(exactly(0)) { radioConfigRepository.setLocalConfig(any()) }
    }

    @Test
    fun editSettingsSkipsProjectionsWhenTheSessionEndsDuringTheCommit() = runTest {
        val admin = recordingAdmin()
        val activeSession = MutableStateFlow<RadioSessionContext?>(EDIT_SESSION)
        val controller = editController(backgroundScope, admin, activeSession = activeSession)
        admin.beforeCommit = { activeSession.value = null }

        controller.editLocalSettings { setFixedPosition(Position(latitude = 47.6, longitude = -122.3, altitude = 42)) }
        advanceUntilIdle()

        assertEquals(1, admin.commits)
        verify(exactly(0)) { nodeManager.handleReceivedPosition(any(), any(), any(), any(), any()) }
    }

    @Test
    fun editSettingsFailsWithoutAnActiveSession() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, activeSession = MutableStateFlow(null))

        assertFailsWith<EditSettingsTransactionException> { controller.editLocalSettings { setConfig(deviceConfig) } }

        assertEquals(0, admin.transactions)
    }

    @Test
    fun editLocalSettingsFailsBeforeBeginWhenNodeIdentityIsUnknown() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)

        assertFailsWith<LocalNodeUnavailableException> { controller.editLocalSettings {} }

        assertEquals(0, admin.transactions)
    }

    @Test
    fun editSettingsCommitsWhenTheCallerIsCancelled() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val blockStarted = CompletableDeferred<Unit>()
        val keepBlockOpen = CompletableDeferred<Unit>()

        val job = launch {
            controller.editLocalSettings {
                setOwner(committedOwner)
                blockStarted.complete(Unit)
                keepBlockOpen.await()
            }
        }
        blockStarted.await()
        job.cancel(CancellationException("cancel settings transaction"))
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(1, admin.commits)
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
    }

    @Test
    fun importContactSendsTheSdkCommandAndUpdatesNodeManager() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        // A QR-scanned contact arrives with manually_verified = false (proto default).
        val contact =
            SharedContact.Builder()
                .also { wb ->
                    wb.node_num = 42
                    wb.user =
                        User.Builder()
                            .also { wb ->
                                wb.id = "!0000002a"
                                wb.long_name = "Test"
                                wb.public_key = TEST_PUBLIC_KEY
                            }
                            .build()
                }
                .build()

        controller.importContact(contact)

        // The verification state encoded by the sharer is honored as-is, not forced to true.
        assertEquals(listOf("contact:42:false"), admin.commands)
        verify { nodeManager.handleReceivedUser(42, any(), any(), false) }
    }

    @Test
    fun importContactPreservesEncodedVerificationState() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        // A contact shared as already verified stays verified on import.
        val contact =
            SharedContact.Builder()
                .also { wb ->
                    wb.node_num = 42
                    wb.user =
                        User.Builder()
                            .also { wb ->
                                wb.id = "!0000002a"
                                wb.long_name = "Test"
                                wb.public_key = TEST_PUBLIC_KEY
                            }
                            .build()
                    wb.manually_verified = true
                }
                .build()

        controller.importContact(contact)

        assertEquals(listOf("contact:42:true"), admin.commands)
        verify { nodeManager.handleReceivedUser(42, any(), any(), true) }
    }

    private fun hamParams(longName: String? = null) = HamParameters.Builder()
        .also { wb ->
            wb.call_sign = "KK7ABC"
            wb.short_name = "KK7A"
            if (longName != null) wb.long_name = longName
        }
        .build()

    private fun TestScope.connectedHam(
        lora: Config.LoRaConfig?,
        myNodeNum: Int = 123,
    ): Triple<RadioControllerImpl, FakeRadioTransport, RadioClient> {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = myNodeNum)
        val (transport, client) = hamClient(lora = lora)
        every { radioInterfaceService.clientForSession(any()) } returns client
        return Triple(controller, transport, client)
    }

    // Regression: sending the caller's zero tx_power/frequency instead of the radio's LoRa values.
    // Oracle: firmware AdminModule.cpp:1942-1943 applies both verbatim.
    @Test
    fun setHamModeEchoesTheLiveSdkLoraValues() = runTest {
        val (controller, transport, client) =
            connectedHam(
                Config.LoRaConfig.Builder()
                    .also { wb ->
                        wb.tx_power = 20
                        wb.override_frequency = 915.5f
                    }
                    .build(),
            )
        client.connect()
        runCurrent()
        val existingUser =
            User.Builder()
                .also { wb ->
                    wb.id = "!0000007b"
                    wb.long_name = "Old Name"
                    wb.short_name = "OLD"
                }
                .build()
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(123 to Node(num = 123, user = existingUser))

        setHamAcked(controller, transport, 123, hamParams(longName = "Attic Heltec"))

        val ham = assertNotNull(transport.outboundPackets().mapNotNull { hamAdminOf(it)?.set_ham_mode }.lastOrNull())
        assertEquals("KK7ABC", ham.call_sign)
        assertEquals("KK7A", ham.short_name)
        assertEquals("Attic Heltec", ham.long_name)
        assertEquals(20, ham.tx_power)
        assertEquals(915.5f, ham.frequency)
        // The optimistic name has to match what firmware composes, or the node reads as renamed until its NodeInfo
        // lands and silently corrects it.
        verify {
            nodeManager.handleReceivedUser(
                123,
                existingUser
                    .newBuilder()
                    .also { wb ->
                        wb.long_name = "KK7ABC//Attic Heltec"
                        wb.short_name = "KK7A"
                        wb.is_licensed = true
                    }
                    .build(),
                0,
                false,
            )
        }
    }

    @Test
    fun setHamModeWithoutALongNameNamesTheNodeAfterTheCallSignAlone() = runTest {
        val (controller, transport, client) = connectedHam(Config.LoRaConfig.Builder().build())
        client.connect()
        runCurrent()
        val existingUser =
            User.Builder()
                .also { wb ->
                    wb.id = "!0000007b"
                    wb.long_name = "Old Name"
                    wb.short_name = "OLD"
                }
                .build()
        every { nodeManager.nodeDBbyNodeNum } returns mapOf(123 to Node(num = 123, user = existingUser))

        setHamAcked(controller, transport, 123, hamParams())

        // long_name is optional: firmware leaves the bare call sign rather than a dangling "//".
        verify {
            nodeManager.handleReceivedUser(
                123,
                existingUser
                    .newBuilder()
                    .also { wb ->
                        wb.long_name = "KK7ABC"
                        wb.short_name = "KK7A"
                        wb.is_licensed = true
                    }
                    .build(),
                0,
                false,
            )
        }
    }

    // Regression: with no LoRa config in the SDK, zeros were sent and firmware applied them verbatim (AdminModule.cpp).
    @Test
    fun setHamModeWithNoLoraConfigInTheSdkFailsBadRequestAndWritesNothing() = runTest {
        val (controller, transport, client) = connectedHam(lora = null)
        client.connect()
        runCurrent()
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        val result = controller.setHamMode(123, hamParams())

        assertEquals(AdminResult.Failed(Routing.Error.BAD_REQUEST), result)
        assertTrue(transport.outboundPackets().none { hamAdminOf(it)?.set_ham_mode != null })
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
    }

    // Regression: a ham send to a node absent from the node DB must still seed a user from the proto defaults.
    @Test
    fun setHamModeForAnUnknownNodeSeedsTheUserFromTheHamParameters() = runTest {
        val (controller, transport, client) = connectedHam(Config.LoRaConfig.Builder().build())
        client.connect()
        runCurrent()
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()

        setHamAcked(controller, transport, 123, hamParams())

        verify {
            nodeManager.handleReceivedUser(
                123,
                User.Builder()
                    .also { wb ->
                        wb.long_name = "KK7ABC"
                        wb.short_name = "KK7A"
                        wb.is_licensed = true
                    }
                    .build(),
                0,
                false,
            )
        }
    }

    // Regression: dropping the local-only guard sends ham mode to a remote node.
    @Test
    fun setHamModeIgnoresRemoteDestinations() = runTest {
        val (controller, transport, client) =
            connectedHam(
                Config.LoRaConfig.Builder()
                    .also { wb ->
                        wb.tx_power = 20
                        wb.override_frequency = 915.5f
                    }
                    .build(),
            )
        client.connect()
        runCurrent()

        controller.setHamMode(456, hamParams())
        runCurrent()

        assertTrue(transport.outboundPackets().none { hamAdminOf(it)?.set_ham_mode != null })
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }

    // Regression: a null client treated as success would show the user as licensed on a radio that was never told.
    @Test
    fun setHamModeWithoutASessionIsUnreachableAndProjectsNothing() = runTest {
        every { nodeManager.nodeDBbyNodeNum } returns emptyMap()
        val controller =
            editController(backgroundScope, recordingAdmin(), myNodeNum = 123, activeSession = MutableStateFlow(null))

        val result = controller.setHamMode(123, hamParams())

        assertEquals(AdminResult.NodeUnreachable, result)
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any(), any()) }
    }

    @Test
    fun importContactReturnsEarlyWhenDisconnected() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin, myNodeNum = null)
        val contact =
            SharedContact.Builder()
                .also { wb ->
                    wb.node_num = 42
                    wb.user = User.Builder().also { wb -> wb.id = "!0000002a" }.build()
                }
                .build()

        controller.importContact(contact)

        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun sendSharedContactRejectsNodeWithNoPublicKey() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val nodeNum = 321
        val user =
            User.Builder()
                .also { wb ->
                    wb.id = NodeAddress.numToDefaultId(nodeNum)
                    wb.long_name = "Heard Only"
                    wb.short_name = "HO"
                }
                .build()
        every { nodeRepository.getNode(NodeAddress.numToDefaultId(nodeNum)) } returns Node(num = nodeNum, user = user)

        val result = controller.sendSharedContact(nodeNum)

        assertFalse(result)
        assertTrue(admin.commands.isEmpty())
    }

    @Test
    fun importContactRejectsEmptyPublicKey() = runTest {
        val admin = recordingAdmin()
        val controller = editController(backgroundScope, admin)
        val contact =
            SharedContact.Builder()
                .also { wb ->
                    wb.node_num = 42
                    wb.user =
                        User.Builder()
                            .also { wb ->
                                wb.id = "!0000002a"
                                wb.long_name = "Test"
                            }
                            .build()
                }
                .build()

        controller.importContact(contact)

        assertTrue(admin.commands.isEmpty())
        verify(exactly(0)) { nodeManager.handleReceivedUser(any(), any(), any(), any()) }
    }
}
