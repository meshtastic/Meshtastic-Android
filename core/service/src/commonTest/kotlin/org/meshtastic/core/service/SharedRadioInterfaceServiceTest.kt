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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import co.touchlab.kermit.Severity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.common.state.RadioOperation
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.repository.NetworkRepository
import org.meshtastic.core.network.repository.SerialDevicePresence
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.core.testing.FakeBluetoothRepository
import org.meshtastic.core.testing.FakeRadioPrefs
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.sdk.ConfigPhase
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** What [SharedRadioInterfaceService] still owns once the SDK client reconnects: the link, sessions and nudges. */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedRadioInterfaceServiceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = CoroutineDispatchers(io = testDispatcher, main = testDispatcher, default = testDispatcher)

    private lateinit var lifecycleOwner: TestLifecycleOwner
    private val bluetoothRepository = FakeBluetoothRepository()
    private val networkAvailable = MutableStateFlow(true)
    private val serialDeviceKeys = MutableStateFlow<Set<String>>(emptySet())
    private val radioPrefs = FakeRadioPrefs()
    private val radioOperationLock = RadioOperationLock()
    private val serviceRepository = FakeServiceRepository()
    private val links = FakeLinks()
    private val services = mutableListOf<SharedRadioInterfaceService>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        lifecycleOwner = TestLifecycleOwner()
        bluetoothRepository.setBluetoothEnabled(true)
    }

    @AfterTest
    fun tearDown() {
        runTest(testDispatcher) { services.forEach { it.disconnect() } }
        lifecycleOwner.destroy()
        Dispatchers.resetMain()
    }

    private fun service(address: String? = BLE, prefs: RadioPrefs = radioPrefs): SharedRadioInterfaceService {
        val network = mock<NetworkRepository>(MockMode.autofill)
        every { network.networkAvailable } returns networkAvailable
        every { network.resolvedList } returns MutableSharedFlow()
        val analytics = mock<PlatformAnalytics>(MockMode.autofill)
        every { analytics.isPlatformServicesAvailable } returns false
        if (prefs === radioPrefs) radioPrefs.setDevAddr(address)
        return SharedRadioInterfaceService(
            dispatchers = dispatchers,
            bluetoothRepository = bluetoothRepository,
            networkRepository = network,
            serialDevicePresence =
            object : SerialDevicePresence {
                override val deviceKeys: StateFlow<Set<String>> = serialDeviceKeys
            },
            processLifecycle = lifecycleOwner.lifecycle,
            radioPrefs = prefs,
            links = links,
            analytics = analytics,
            radioOperationLock = radioOperationLock,
            serviceStateWriter = serviceRepository,
        )
            .also(services::add)
    }

    /** Connects [address] and admits its first session, as the pipeline does when the SDK session starts. */
    private fun connectedService(address: String = BLE): Pair<SharedRadioInterfaceService, FakeLink> {
        val service = service(address)
        service.connect()
        val link = links.opened.last()
        assertNotNull(link.host.openSession())
        return service to link
    }

    @Test
    fun `transport session diagnostic redacts its address`() {
        val diagnostic = ActiveRadioSession(generation = 7L, address = BLE).toString()

        assertFalse(BLE in diagnostic)
        assertTrue("generation=7" in diagnostic)
        assertTrue("address=..." in diagnostic)
    }

    @Test
    fun `connect opens one link for the selected radio and starts its client`() = runTest(testDispatcher) {
        val service = service()

        service.connect()
        val link = links.opened.single()
        val retries = link.retries
        service.connect()

        assertEquals(1, links.opened.size)
        assertEquals(BLE, link.address)
        assertEquals(1, link.connects, "a second connect hurries the running client instead of opening another")
        assertEquals(retries + 1, link.retries)
    }

    @Test
    fun `an address the platform refuses opens nothing`() = runTest(testDispatcher) {
        links.refused += BLE
        val service = service()

        service.connect()
        bluetoothRepository.setBluetoothEnabled(false)
        bluetoothRepository.setBluetoothEnabled(true)

        assertTrue(links.opened.isEmpty())
        assertNull(service.activeSession.value)
        assertEquals(BLE, radioPrefs.devAddr.value, "the saved address is kept, not cleared")
    }

    @Test
    fun `the client's states are published but Connected is left to the snapshot install`() = runTest(testDispatcher) {
        val (_, link) = connectedService()

        link.connection.value = ConnectionState.Configuring(ConfigPhase.Stage2, 0.5f)
        assertEquals(ConnectionState.Configuring(ConfigPhase.Stage2, 0.5f), serviceRepository.connectionState.value)

        link.connection.value = ConnectionState.Connected
        assertEquals(
            ConnectionState.Configuring(ConfigPhase.Stage2, 0.5f),
            serviceRepository.connectionState.value,
            "the app is connected only once the session's snapshot is installed",
        )

        val drop = ConnectionState.Reconnecting(MeshtasticException.Transport("lost"), attempt = 1)
        link.connection.value = drop
        assertEquals(drop, serviceRepository.connectionState.value)
    }

    @Test
    fun `a drop that lands after the install published Connected wins once its session closes`() =
        runTest(testDispatcher) {
            val (service, link) = connectedService()
            val session = assertNotNull(service.activeSession.value)
            val drop = ConnectionState.Reconnecting(MeshtasticException.Transport("lost"), attempt = 1)

            link.connection.value = drop
            // The install was still running under the session's lease when the link dropped.
            serviceRepository.setConnectionState(ConnectionState.Connected)
            link.host.closeSession(session)

            assertEquals(drop, serviceRepository.connectionState.value)
            assertNull(service.activeSession.value)
        }

    @Test
    fun `disconnect drains the session then disconnects and releases the link`() = runTest(testDispatcher) {
        val (service, link) = connectedService()
        val session = assertNotNull(service.activeSession.value)
        val release = CompletableDeferred<Unit>()
        val work = launch { service.runWithSessionLease(session) { release.await() } }

        val disconnect = launch { service.disconnect() }
        assertFalse(disconnect.isCompleted, "disconnect waits for the admitted lease")
        assertEquals(0, link.disconnects, "the client stays up until the session's work has drained")
        assertFalse(service.isSessionActive(session), "teardown rejects new work at once")

        release.complete(Unit)
        work.join()
        disconnect.join()

        assertEquals(1, link.disconnects)
        assertTrue(link.closed)
        assertNull(service.activeSession.value)
        assertEquals(ConnectionState.Disconnected, serviceRepository.connectionState.value)
    }

    @Test
    fun `a link that is no longer selected admits no session`() = runTest(testDispatcher) {
        val (service, first) = connectedService()
        val firstSession = assertNotNull(service.activeSession.value)

        service.setDeviceAddress(TCP)

        val second = links.opened.last()
        assertEquals(TCP, second.address)
        assertNull(first.host.openSession(), "the old link's pipeline must not open a session on the new radio")
        val secondSession = assertNotNull(second.host.openSession())
        assertTrue(secondSession.generation > firstSession.generation)
        assertSame(second.client, service.clientForSession(secondSession))
        assertNull(service.clientForSession(firstSession))
    }

    @Test
    fun `Bluetooth or the network or the selected USB device coming back hurries the client`() =
        runTest(testDispatcher) {
            val service = service(SERIAL)
            service.connect()
            val link = links.opened.single()
            val before = link.retries

            bluetoothRepository.setBluetoothEnabled(false)
            bluetoothRepository.setBluetoothEnabled(true)
            networkAvailable.value = false
            networkAvailable.value = true
            serialDeviceKeys.value = setOf("other")
            serialDeviceKeys.value = setOf("other", SERIAL.drop(1))

            assertEquals(before + 3, link.retries)
        }

    @Test
    fun `connect replaces a client that stopped trying`() = runTest(testDispatcher) {
        val service = service()
        service.connect()
        val failed = links.opened.single()
        failed.connection.value = ConnectionState.Failed(MeshtasticException.Transport("refused"))

        service.connect()

        assertTrue(failed.closed)
        assertEquals(2, links.opened.size)
        assertEquals(1, links.opened.last().connects)
    }

    @Test
    fun `a link whose snapshot was not installed closes and leaves the connection failed`() = runTest(testDispatcher) {
        // Regression: a failed install left the client connected and the app short of Connected with no way on.
        val (service, link) = connectedService(TCP)
        val errors = mutableListOf<String>()
        backgroundScope.launch { service.connectionError.collect { errors += it } }
        link.connection.value = ConnectionState.Connected

        link.host.onSnapshotInstallFailed(assertNotNull(service.activeSession.value))

        assertTrue(link.closed)
        assertNull(service.activeSession.value)
        val state = assertIs<ConnectionState.Failed>(serviceRepository.connectionState.value)
        assertIs<MeshtasticException.HandshakeTimeout>(state.cause)

        service.connect()
        assertEquals(2, links.opened.size, "Connect opens a fresh link")
        // Last: on wasmJs the string resource loads asynchronously, and work launched after it would be queued.
        assertEquals(listOf(connectionErrorText(TransportDisconnectReason.HandshakeRecoveryExhausted)), errors)
    }

    @Test
    fun `a selected radio whose link cannot open is failed until it is put away`() = runTest(testDispatcher) {
        // Regression: a link that could not be built left the card on Disconnected with nothing shown.
        links.unopenable += TCP
        val service = service(TCP)

        service.connect()

        assertTrue(links.opened.isEmpty())
        assertIs<ConnectionState.Failed>(serviceRepository.connectionState.value)

        service.disconnect()

        assertEquals(ConnectionState.Disconnected, serviceRepository.connectionState.value)
    }

    @Test
    fun `a firmware-maintenance operation closes the link and its end reopens it`() = runTest(testDispatcher) {
        val service = service(SERIAL)
        service.connect()
        val first = links.opened.single()

        val lease = radioOperationLock.acquire(RadioOperation.FirmwareMaintenance)
        assertTrue(first.closed, "the maintenance flow owns the port")
        service.connect()
        assertEquals(1, links.opened.size, "nothing reopens the link while the port is held")

        radioOperationLock.release(lease)
        assertEquals(2, links.opened.size)
        assertEquals(1, links.opened.last().connects)
    }

    @Test
    fun `a GATT refresh request reaches the next Bluetooth link and a disconnect drops it`() = runTest(testDispatcher) {
        val service = service()
        // A firmware update deselects the radio, asks for the refresh, then selects the radio again.
        service.setDeviceAddress(null)
        service.requestGattCacheInvalidationOnNextConnect()
        service.setDeviceAddress(BLE)
        assertEquals(1, links.opened.last().gattRefreshes)
        service.setDeviceAddress(TCP)
        service.setDeviceAddress(BLE)
        assertEquals(0, links.opened.last().gattRefreshes, "one request refreshes one link")

        service.requestGattCacheInvalidationOnNextConnect()
        assertEquals(1, links.opened.last().gattRefreshes, "the open Bluetooth link takes it at once")

        service.setDeviceAddress(null)
        service.requestGattCacheInvalidationOnNextConnect()
        service.disconnect()
        service.setDeviceAddress(BLE)
        assertEquals(0, links.opened.last().gattRefreshes, "a disconnect drops a pending request")
    }

    @Test
    fun `a failure is shown once and a reconnect after a working connection says nothing`() = runTest(testDispatcher) {
        val service = service(TCP)
        val errors = mutableListOf<String>()
        backgroundScope.launch { service.connectionError.collect { errors += it } }
        service.connect()
        val link = links.opened.single()
        val refused = MeshtasticException.Transport("refused")
        link.reasons[refused] = TransportDisconnectReason.WebSocketConnectFailed

        link.connection.value = ConnectionState.Reconnecting(refused, attempt = 1)
        link.connection.value = ConnectionState.Reconnecting(refused, attempt = 2)
        link.connection.value = ConnectionState.Connected
        link.connection.value = ConnectionState.Reconnecting(refused, attempt = 1)
        link.connection.value =
            ConnectionState.Failed(MeshtasticException.FirmwareTooOld(required = 30, present = 20))

        assertEquals(
            listOf(
                connectionErrorText(TransportDisconnectReason.WebSocketConnectFailed),
                connectionErrorText(TransportDisconnectReason.FirmwareTooOld),
            ),
            errors,
        )
    }

    /** Persists like DataStore: a write lands only when the test commits it, in order. */
    private class DeferredRadioPrefs(saved: String?) : RadioPrefs {
        override val devAddr = MutableStateFlow(saved)
        override val devName = MutableStateFlow<String?>(null)

        override suspend fun awaitDevAddr(): String? = devAddr.value

        private val pending = ArrayDeque<String?>()

        override fun setDevAddr(address: String?) {
            pending.addLast(address)
        }

        override fun setDevName(name: String?) {
            devName.value = name
        }

        fun commitThrough(address: String) {
            do {
                val next = pending.removeFirst()
                devAddr.value = next
            } while (next != address)
        }
    }

    @Test
    fun `a late commit of an older selection does not rewind the selected address`() = runTest(testDispatcher) {
        val prefs = DeferredRadioPrefs(saved = "xAA:AA:AA:AA:AA:AA")
        val service = service(prefs = prefs)

        service.setDeviceAddress("xBB:BB:BB:BB:BB:BB")
        service.setDeviceAddress("xCC:CC:CC:CC:CC:CC")
        prefs.commitThrough("xBB:BB:BB:BB:BB:BB")

        assertEquals("xCC:CC:CC:CC:CC:CC", service.currentDeviceAddressFlow.value)
    }

    @Test
    fun `the saved address still reaches the flow when it loads after construction`() = runTest(testDispatcher) {
        val prefs = DeferredRadioPrefs(saved = null)
        val service = service(prefs = prefs)

        prefs.devAddr.value = "xAA:AA:AA:AA:AA:AA"

        assertEquals("xAA:AA:AA:AA:AA:AA", service.currentDeviceAddressFlow.value)
    }

    @Test
    fun `ordered session lane serializes handshake work while independent leases remain concurrent`() =
        runTest(testDispatcher) {
            val (service, _) = connectedService()
            val session = assertNotNull(service.activeSession.value)
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()
            val secondStarted = CompletableDeferred<Unit>()
            val independentStarted = CompletableDeferred<Unit>()

            val first = launch {
                service.runWhileSessionActive(session, "first") {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
            }
            firstStarted.await()
            val second = launch { service.runWhileSessionActive(session, "second") { secondStarted.complete(Unit) } }
            val independent = launch { service.runWithSessionLease(session) { independentStarted.complete(Unit) } }

            assertFalse(secondStarted.isCompleted, "ordered work must wait for the current lane owner")
            assertTrue(independentStarted.isCompleted, "independent deferred work must acquire its own lease")

            releaseFirst.complete(Unit)
            first.join()
            second.join()
            independent.join()
            assertTrue(secondStarted.isCompleted)
        }

    @Test
    fun `wedged session handler is cancelled at the timeout and the pipeline continues`() = runTest(testDispatcher) {
        val (service, _) = connectedService()
        val session = assertNotNull(service.activeSession.value)
        val wedgeStarted = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        var wedgeRanToCompletion = false

        val logs = CapturingLogWriter.install()
        try {
            val wedged = launch {
                service.runWhileSessionActive(session, "FromRadio packet TEXT_MESSAGE_APP") {
                    wedgeStarted.complete(Unit)
                    neverReleased.await()
                    wedgeRanToCompletion = true
                }
            }
            wedgeStarted.await()
            val nextStarted = CompletableDeferred<Unit>()
            val next = launch { service.runWhileSessionActive(session, "next") { nextStarted.complete(Unit) } }
            assertFalse(nextStarted.isCompleted, "ordered work is serialized behind the wedged handler")

            advanceTimeBy(121_000L)
            wedged.join()
            next.join()

            assertFalse(wedgeRanToCompletion, "the wedged handler must have been cancelled, not completed")
            assertTrue(nextStarted.isCompleted, "the handler timeout must release the pipeline for queued work")
            val timeoutLines = logs.messages(Severity.Error).filter { "Session handler exceeded" in it }
            assertEquals(1, timeoutLines.size, "one timeout line: $timeoutLines")
            assertTrue("(handler=FromRadio packet TEXT_MESSAGE_APP)" in timeoutLines.single())
        } finally {
            CapturingLogWriter.uninstall()
            neverReleased.complete(Unit)
        }
    }

    @Test
    fun `disconnect completes after a wedged handler is timed out`() = runTest(testDispatcher) {
        val (service, _) = connectedService()
        val session = assertNotNull(service.activeSession.value)
        val wedgeStarted = CompletableDeferred<Unit>()
        val neverReleased = CompletableDeferred<Unit>()
        val wedged = launch {
            service.runWhileSessionActive(session, "wedged") {
                wedgeStarted.complete(Unit)
                neverReleased.await()
            }
        }
        wedgeStarted.await()

        val disconnect = launch { service.disconnect() }
        assertFalse(disconnect.isCompleted, "disconnect must wait while the lease is admitted")

        advanceTimeBy(121_000L)
        disconnect.join()
        wedged.join()

        assertNull(service.activeSession.value, "teardown must complete once the wedged lease is released")
        neverReleased.complete(Unit)
    }

    /** A lifecycle without LifecycleRegistry's main-thread checks, which fail under Robolectric host tests. */
    private class TestLifecycleOwner : LifecycleOwner {
        private val observers = mutableListOf<LifecycleObserver>()
        private var state = Lifecycle.State.RESUMED

        override val lifecycle: Lifecycle =
            object : Lifecycle() {
                override fun addObserver(observer: LifecycleObserver) {
                    observers.add(observer)
                }

                override fun removeObserver(observer: LifecycleObserver) {
                    observers.remove(observer)
                }

                override val currentState: Lifecycle.State
                    get() = state
            }

        fun destroy() {
            state = Lifecycle.State.DESTROYED
            observers.toList().forEach { observer ->
                (observer as? LifecycleEventObserver)?.onStateChanged(this, Lifecycle.Event.ON_DESTROY)
            }
        }
    }

    private companion object {
        const val BLE = "xAA:BB:CC:DD:EE:FF"
        const val TCP = "t192.0.2.1"
        const val SERIAL = "s1027:29987:0"
    }
}
