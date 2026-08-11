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
package org.meshtastic.core.testing

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.repository.RadioSessionLease
import org.meshtastic.core.repository.ReceivedRadioFrame
import org.meshtastic.core.repository.TransportDisconnectReason

/**
 * A test double for [RadioInterfaceService] that provides an in-memory implementation.
 *
 * The [connectionState] here mirrors the transport-level semantics of the real implementation. In production, only
 * [MeshConnectionManager][org.meshtastic.core.repository.MeshConnectionManager] observes this flow; tests should verify
 * that bridging behavior rather than consuming it directly from UI/feature test code (use
 * [FakeServiceRepository.connectionState] instead). Address selection remains separate from transport admission:
 * [connect] and an in-flight [restartTransport] publish a fresh monotonically increasing session generation.
 */
@Suppress("TooManyFunctions")
class FakeRadioInterfaceService(override val serviceScope: CoroutineScope = MainScope()) : RadioInterfaceService {

    override val supportedDeviceTypes: List<DeviceType> = emptyList()

    /** Transport-level connection state (raw hardware link status). */
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _currentDeviceAddressFlow = MutableStateFlow<String?>(null)
    override val currentDeviceAddressFlow: StateFlow<String?> = _currentDeviceAddressFlow

    private val _sessionGeneration = MutableStateFlow(0L)
    override val sessionGeneration: StateFlow<Long> = _sessionGeneration

    private val _activeSession = MutableStateFlow<RadioSessionContext?>(null)
    override val activeSession: StateFlow<RadioSessionContext?> = _activeSession

    private val sessionAdmissionLock = SynchronizedObject()
    private var sessionAdmissionOpen = false
    private var admittedSessionOperations = 0
    private var sessionDrainWaiter: CompletableDeferred<Unit>? = null
    private val sessionOperationMutex = Mutex()

    /** Number of lease-release invariant violations observed by this fake. Finally paths record rather than throw. */
    private var sessionLeaseInvariantViolationsState: Int = 0
    val sessionLeaseInvariantViolations: Int
        get() = synchronized(sessionAdmissionLock) { sessionLeaseInvariantViolationsState }

    override fun isSessionActive(session: RadioSessionContext): Boolean =
        synchronized(sessionAdmissionLock) { sessionAdmissionOpen && _activeSession.value == session }

    override fun runIfSessionActive(session: RadioSessionContext, block: () -> Unit): Boolean =
        synchronized(sessionAdmissionLock) {
            if (!sessionAdmissionOpen || _activeSession.value != session) return@synchronized false
            block()
            true
        }

    override suspend fun runWithSessionLease(
        session: RadioSessionContext,
        block: suspend (RadioSessionLease) -> Unit,
    ): Boolean {
        val admittedSession = admitSessionOperation(session) ?: return false

        val lease =
            object : RadioSessionLease {
                override val session: RadioSessionContext = session

                override fun isCurrent(): Boolean =
                    synchronized(sessionAdmissionLock) { _activeSession.value == admittedSession }
            }

        try {
            block(lease)
            return true
        } finally {
            releaseSessionOperation(admittedSession)
        }
    }

    override suspend fun runWhileSessionActive(session: RadioSessionContext, block: suspend () -> Unit): Boolean =
        sessionOperationMutex.withLock { runWithSessionLease(session) { block() } }

    // Use an unbounded Channel to mirror SharedRadioInterfaceService semantics. A MutableSharedFlow would
    // hide the stop/start backlog bug that motivated the resetReceivedBuffer() API.
    private val _receivedData = Channel<ReceivedRadioFrame>(Channel.UNLIMITED)
    override val receivedData: Flow<ReceivedRadioFrame> = _receivedData.receiveAsFlow()

    private val _meshActivity = MutableSharedFlow<MeshActivity>()
    override val meshActivity: Flow<MeshActivity> = _meshActivity.asFlow()

    private val _connectionError = MutableSharedFlow<String>()
    override val connectionError: Flow<String> = _connectionError.asFlow()

    private val _sentToRadio = mutableListOf<ByteArray>()

    /** Thread-safe lifetime history of writes accepted across all admitted fake transport sessions. */
    val sentToRadio: List<ByteArray>
        get() = synchronized(sessionAdmissionLock) { _sentToRadio.map { it.copyOf() } }

    /** Set to true to simulate an admitted transport rejecting the byte handoff. */
    var rejectAdmittedSends: Boolean = false

    var connectCalled = false
    var restartTransportCalled: Boolean = false
        private set

    override val mockTransportEnabled: MutableStateFlow<Boolean> = MutableStateFlow(true)

    /** No capture asset in tests; flip per-test when exercising replay-gated behaviour. */
    override var isReplayTransportAvailable: Boolean = false

    /**
     * Records [bytes] only while a transport session is admitted. Tests must select a non-null device address and call
     * [connect] before sending; attempts outside that lifecycle return false and leave [sentToRadio] unchanged.
     */
    override fun trySendToRadio(bytes: ByteArray): Boolean {
        val admittedSession = admitSessionOperation() ?: return false
        return try {
            val accepted = !rejectAdmittedSends
            if (accepted) synchronized(sessionAdmissionLock) { _sentToRadio.add(bytes.copyOf()) }
            accepted
        } finally {
            releaseSessionOperation(admittedSession)
        }
    }

    override fun connect() {
        connectCalled = true
        if (_activeSession.value == null) admitSelectedSession()
    }

    override suspend fun disconnect() {
        connectCalled = false
        revokeActiveSession()
    }

    override suspend fun restartTransport() {
        restartTransportCalled = true
        if (connectCalled && _activeSession.value != null) {
            revokeActiveSession()
            admitSelectedSession()
        }
    }

    override fun getDeviceAddress(): String? = _currentDeviceAddressFlow.value

    override fun setDeviceAddress(deviceAddr: String?): Boolean {
        _currentDeviceAddressFlow.value = deviceAddr
        return true
    }

    private fun admitSessionOperation(expectedSession: RadioSessionContext? = null): RadioSessionContext? =
        synchronized(sessionAdmissionLock) {
            val active = _activeSession.value
            val matchesExpectedSession = expectedSession == null || active == expectedSession
            if (!sessionAdmissionOpen || active == null || !matchesExpectedSession) {
                null
            } else {
                admittedSessionOperations++
                active
            }
        }

    private fun releaseSessionOperation(admittedSession: RadioSessionContext) {
        val waiter =
            synchronized(sessionAdmissionLock) {
                if (_activeSession.value != admittedSession) {
                    sessionLeaseInvariantViolationsState++
                    Logger.e { "Fake session changed before an admitted operation released its lease" }
                    return@synchronized null
                }
                if (admittedSessionOperations <= 0) {
                    sessionLeaseInvariantViolationsState++
                    Logger.e { "Fake session operation count underflow" }
                    sessionDrainWaiter.also { sessionDrainWaiter = null }
                } else {
                    admittedSessionOperations--
                    if (admittedSessionOperations == 0) {
                        sessionDrainWaiter.also { sessionDrainWaiter = null }
                    } else {
                        null
                    }
                }
            }
        waiter?.complete(Unit)
    }

    private fun admitSelectedSession() {
        val address = _currentDeviceAddressFlow.value
        synchronized(sessionAdmissionLock) {
            check(_activeSession.value == null && admittedSessionOperations == 0) {
                "Cannot admit a fake transport while the previous session is still draining"
            }
            if (address == null) {
                sessionAdmissionOpen = false
                return
            }
            val generation = _sessionGeneration.value + 1
            _sessionGeneration.value = generation
            _activeSession.value = RadioSessionContext(generation, address)
            sessionAdmissionOpen = true
        }
    }

    private suspend fun revokeActiveSession() {
        val session = synchronized(sessionAdmissionLock) { _activeSession.value } ?: return
        withContext(NonCancellable) {
            val waiter =
                synchronized(sessionAdmissionLock) {
                    if (_activeSession.value != session) return@synchronized null
                    sessionAdmissionOpen = false
                    if (admittedSessionOperations == 0) {
                        null
                    } else {
                        sessionDrainWaiter ?: CompletableDeferred<Unit>().also { sessionDrainWaiter = it }
                    }
                }
            waiter?.await()
            synchronized(sessionAdmissionLock) {
                if (_activeSession.value == session) {
                    check(admittedSessionOperations == 0) { "Fake session revoked before admitted operations drained" }
                    _activeSession.value = null
                    sessionDrainWaiter = null
                }
            }
        }
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "$interfaceId:$rest"

    override fun onConnect() {
        _connectionState.value = ConnectionState.Connected
    }

    override fun onDisconnect(isPermanent: Boolean, errorMessage: String?, reason: TransportDisconnectReason?) {
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun handleFromRadio(bytes: ByteArray) {
        val session = _activeSession.value ?: return
        runIfSessionActive(session) { emitFromRadio(bytes, session) }
    }

    override fun resetReceivedBuffer() {
        @Suppress("EmptyWhileBlock", "ControlFlowWithEmptyBody")
        while (_receivedData.tryReceive().isSuccess) {}
    }

    // --- Helper methods for testing ---

    fun emitFromRadio(bytes: ByteArray, session: RadioSessionContext) {
        _receivedData.trySend(ReceivedRadioFrame(bytes.toByteString(), session))
    }

    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
    }

    private val gattCacheInvalidationRequested = atomic(false)

    override fun requestGattCacheInvalidationOnNextConnect() {
        gattCacheInvalidationRequested.value = true
    }

    override fun consumeGattCacheInvalidationRequest(): Boolean = gattCacheInvalidationRequested.getAndSet(false)
}
