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
package org.meshtastic.core.network.radio

import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.UsbSerialDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.network.repository.SerialConnection
import org.meshtastic.core.network.repository.SerialConnectionListener
import org.meshtastic.core.network.transport.HeartbeatSender
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.repository.RadioTransportCallback
import org.meshtastic.core.repository.TransportDisconnectReason
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

// The outer teardown can await one nested connection-gate close (15 s drain + 15 s teardown) and its connect wait.
private val SERIAL_TRANSPORT_TEARDOWN_TIMEOUT =
    TransportLifecycleGate.OPERATION_DRAIN_TIMEOUT + TransportLifecycleGate.TEARDOWN_TIMEOUT + 15.seconds

/** Resolves a selected serial address, retaining the legacy single-device fallback used by older installs. */
internal fun resolveSerialDevice(devices: Map<String, UsbSerialDriver>, address: String): UsbSerialDriver? =
    devices[address] ?: devices.values.singleOrNull()

/** An Android USB/serial [RadioTransport] implementation. */
@Suppress("TooManyFunctions")
class SerialRadioTransport(
    callback: RadioTransportCallback,
    scope: CoroutineScope,
    private val serialDevices: StateFlow<Map<String, UsbSerialDriver>>,
    private val createSerialConnection: (UsbSerialDriver, SerialConnectionListener) -> SerialConnection,
    private val address: String,
) : StreamTransport(callback, scope) {
    private val connRef = AtomicReference<SerialConnection?>()
    private val connectionAdmissionLock = Any()

    private class ConnectionToken {
        var connection: SerialConnection? = null
        val lifecycle = TransportLifecycleGate("Android serial connection")
        val connectCompletion = CompletableDeferred<Unit>()
    }

    private data class ConnectionOperation(
        val token: ConnectionToken,
        val connection: SerialConnection,
        val lease: TransportLifecycleGate.OperationLease,
    )

    private data class ClaimedConnection(
        val token: ConnectionToken,
        val connection: SerialConnection,
        val cleanupCompletion: CompletableDeferred<Unit>,
    )

    private data class ConnectionStats(
        val connectStartedAt: Long,
        var connectedAt: Long = 0L,
        var packetsReceived: Int = 0,
        var bytesReceived: Long = 0L,
    )

    private var activeConnectionToken: ConnectionToken? = null

    /** Guarded by [connectionAdmissionLock]; replacement waits until old physical cleanup and notification finish. */
    private var connectionCleanupInProgress = false
    private var connectionCleanupCompletion: CompletableDeferred<Unit>? = null
    private var connectAfterCleanupScheduled = false
    private val connectionReady = AtomicBoolean(false)
    private val lifecycle =
        TransportLifecycleGate("Android serial", teardownTimeout = SERIAL_TRANSPORT_TEARDOWN_TIMEOUT)

    // Detached from the service scope so a claimed generation always publishes cleanup completion.
    private val cleanupScope = CoroutineScope(SupervisorJob() + scope.coroutineContext.minusKey(Job))

    private val heartbeatSender = HeartbeatSender(sendToRadio = { handleSendToRadio(it) }, logTag = "Serial[$address]")

    override fun start() = connect()

    override suspend fun close() {
        var completed = false
        try {
            completed =
                lifecycle.close(
                    beforeDrain = {
                        // Closing admission first suppresses a deferred disconnect while stopping this worker releases
                        // its transport and connection leases.
                        super.close()
                    },
                    teardown = {
                        Logger.d { "[$address] Closing serial transport" }
                        closeConnectionAndAwaitConnect(waitForStopped = true)
                    },
                )
        } finally {
            cleanupScope.cancel()
        }
        if (!completed) Logger.w { "[$address] Serial teardown did not complete within its lifecycle bounds" }
    }

    override fun onDeviceDisconnect(
        waitForStopped: Boolean,
        isPermanent: Boolean,
        errorMessage: String?,
        reason: TransportDisconnectReason?,
    ) {
        disconnectConnection(
            connectionToken = null,
            waitForStopped = waitForStopped,
            isPermanent = isPermanent,
            errorMessage = errorMessage,
            reason = reason,
        )
    }

    private fun claimConnection(connectionToken: ConnectionToken? = null): ClaimedConnection? =
        synchronized(connectionAdmissionLock) {
            val token = activeConnectionToken ?: return@synchronized null
            val connection = connRef.get() ?: return@synchronized null
            if (connectionToken != null && !ownsConnectionLocked(connectionToken, connection)) return@synchronized null
            connRef.set(null)
            connectionReady.set(false)
            activeConnectionToken = null
            token.connection = null
            connectionCleanupInProgress = true
            val cleanupCompletion = CompletableDeferred<Unit>().also { connectionCleanupCompletion = it }
            ClaimedConnection(token, connection, cleanupCompletion)
        }

    /** Caller must hold [connectionAdmissionLock]. */
    private fun ownsConnectionLocked(connectionToken: ConnectionToken, connection: SerialConnection): Boolean =
        activeConnectionToken === connectionToken &&
            connectionToken.connection === connection &&
            connRef.get() === connection

    private suspend fun closeConnectionAndAwaitConnect(waitForStopped: Boolean): Boolean {
        val claimed = claimConnection()
        if (claimed == null) {
            awaitPendingConnectionCleanup()
            return false
        }
        try {
            closeClaimedConnection(claimed, waitForStopped)
        } finally {
            finishConnectionCleanup(claimed.cleanupCompletion)
        }
        return true
    }

    private suspend fun awaitPendingConnectionCleanup() {
        synchronized(connectionAdmissionLock) { connectionCleanupCompletion }?.await()
    }

    private suspend fun closeClaimedConnection(claimed: ClaimedConnection, waitForStopped: Boolean) {
        val completed =
            claimed.token.lifecycle.close {
                claimed.connection.close(waitForStopped)
                claimed.token.connectCompletion.awaitForClose("connection attempt")
            }
        if (!completed) {
            Logger.w { "[$address] Serial connection teardown did not complete within its lifecycle bounds" }
        }
    }

    private suspend fun Deferred<Unit>.awaitForClose(phase: String) {
        val timeout = TransportLifecycleGate.OPERATION_DRAIN_TIMEOUT
        val completed =
            withTimeoutOrNull(timeout) {
                await()
                true
            } == true
        if (!completed) Logger.w { "[$address] Serial close timed out after $timeout while waiting for $phase" }
    }

    private fun disconnectConnection(
        connectionToken: ConnectionToken?,
        waitForStopped: Boolean,
        isPermanent: Boolean,
        errorMessage: String? = null,
        reason: TransportDisconnectReason? = null,
    ): Boolean {
        val claimed = claimConnection(connectionToken) ?: return false
        launchConnectionCleanup {
            try {
                closeClaimedConnection(claimed, waitForStopped)
            } finally {
                try {
                    notifyDeviceDisconnect(waitForStopped, isPermanent, errorMessage, reason)
                } finally {
                    finishConnectionCleanup(claimed.cleanupCompletion)
                }
            }
        }
        return true
    }

    private fun launchConnectionCleanup(block: suspend () -> Unit) {
        cleanupScope.handledLaunch(start = CoroutineStart.UNDISPATCHED) { withContext(NonCancellable) { block() } }
    }

    private fun notifyDeviceDisconnect(
        waitForStopped: Boolean,
        isPermanent: Boolean,
        errorMessage: String?,
        reason: TransportDisconnectReason?,
    ) {
        // Explicit close owns the terminal service-layer notification. Admission at emission time linearizes this
        // callback with close even when physical cleanup was deferred behind an older operation.
        lifecycle.runIfOpen { super.onDeviceDisconnect(waitForStopped, isPermanent, errorMessage, reason) }
    }

    override fun connect() {
        when {
            lifecycle.isClosed -> Logger.d { "[$address] Ignoring start after serial transport close" }

            deferConnectUntilCleanup() -> Unit

            hasActiveConnection() -> Logger.d { "[$address] Ignoring start while serial generation is active" }

            else -> {
                val devices = serialDevices.value
                // Keep legacy path-based selections usable when a single serial device can be resolved unambiguously.
                // Generation ownership still binds all callbacks to this transport instance after the device is chosen.
                val device = resolveSerialDevice(devices, address)
                if (device == null) {
                    Logger.e { "[$address] Serial device not found at selected address" }
                } else {
                    openConnection(device)
                }
            }
        }
    }

    private fun hasActiveConnection(): Boolean =
        synchronized(connectionAdmissionLock) { activeConnectionToken != null || connRef.get() != null }

    private fun deferConnectUntilCleanup(): Boolean {
        var cleanup: Deferred<Unit>? = null
        val deferred =
            synchronized(connectionAdmissionLock) {
                if (!connectionCleanupInProgress) {
                    false
                } else {
                    if (!connectAfterCleanupScheduled) {
                        connectAfterCleanupScheduled = true
                        cleanup = checkNotNull(connectionCleanupCompletion)
                    }
                    true
                }
            }
        cleanup?.let { completion ->
            scope.handledLaunch {
                try {
                    completion.await()
                } finally {
                    synchronized(connectionAdmissionLock) { connectAfterCleanupScheduled = false }
                }
                connect()
            }
        }
        return deferred
    }

    private fun openConnection(device: UsbSerialDriver) {
        val startupLease = lifecycle.tryAcquire()
        if (startupLease == null) {
            Logger.d { "[$address] Ignoring serial open after transport close" }
            return
        }
        try {
            openAdmittedConnection(device)
        } finally {
            startupLease.release()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun openAdmittedConnection(device: UsbSerialDriver) {
        val stats = ConnectionStats(connectStartedAt = nowMillis)
        Logger.i { "[$address] Opening serial device: $device" }

        val connectionToken = ConnectionToken()
        val connection = createSerialConnection(device, createConnectionListener(device, connectionToken, stats))
        if (!publishConnection(connectionToken, connection)) {
            Logger.d { "[$address] Serial generation is active or still tearing down; closing unused connection" }
            connection.close(waitForStopped = false)
            return
        }

        try {
            connection.connect()
        } catch (e: CancellationException) {
            disconnectConnection(connectionToken, waitForStopped = false, isPermanent = false, errorMessage = e.message)
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "[$address] Serial connect failed" }
            disconnectConnection(
                connectionToken = connectionToken,
                waitForStopped = false,
                isPermanent = false,
                errorMessage = e.message,
            )
        } finally {
            connectionToken.connectCompletion.complete(Unit)
        }
    }

    private fun createConnectionListener(
        device: UsbSerialDriver,
        connectionToken: ConnectionToken,
        stats: ConnectionStats,
    ): SerialConnectionListener = object : SerialConnectionListener {
        override fun onMissingPermission() {
            if (
                disconnectConnection(
                    connectionToken = connectionToken,
                    waitForStopped = false,
                    isPermanent = true,
                    reason = TransportDisconnectReason.UsbPermissionDenied,
                )
            ) {
                Logger.w { "Serial connection unavailable: USB permission denied" }
            }
        }

        override fun onConnected() = handleConnected(connectionToken, stats)

        override fun onDataReceived(bytes: ByteArray) = handleDataReceived(connectionToken, stats, bytes)

        override fun onDisconnected(thrown: Exception?) = handleDisconnected(connectionToken, stats, device, thrown)
    }

    private fun handleConnected(connectionToken: ConnectionToken, stats: ConnectionStats) {
        val operation = admitConnectionOperation(connectionToken, requireReady = false) ?: return
        var wakeFailure: Exception? = null
        try {
            stats.connectedAt = nowMillis
            val connectionTime = stats.connectedAt - stats.connectStartedAt
            Logger.i { "[$address] Serial device connected in ${connectionTime}ms" }
            wakeFailure = sendWakeBytes(operation.connection)
            if (wakeFailure == null) {
                val readyToPublish =
                    synchronized(connectionAdmissionLock) {
                        ownsConnectionLocked(connectionToken, operation.connection).also { owns ->
                            if (owns) connectionReady.set(true)
                        }
                    }
                if (readyToPublish) {
                    lifecycle.runIfOpen {
                        val stillOwned =
                            synchronized(connectionAdmissionLock) {
                                ownsConnectionLocked(connectionToken, operation.connection) && connectionReady.get()
                            }
                        if (stillOwned) callback.onConnect()
                    }
                }
            }
        } finally {
            operation.lease.release()
        }
        wakeFailure?.let { handleWakeFailure(connectionToken, it) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendWakeBytes(connection: SerialConnection): Exception? = try {
        // SerialConnection.sendBytes is asynchronous. This lease guarantees handoff to this exact connection
        // generation's write queue; it does not claim physical wire delivery.
        connection.sendBytes(StreamFrameCodec.WAKE_BYTES)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    private fun handleWakeFailure(connectionToken: ConnectionToken, failure: Exception) {
        Logger.w(failure) { "[$address] Serial wake failed; ending connection generation" }
        disconnectConnection(connectionToken, waitForStopped = false, isPermanent = false)
    }

    private fun handleDataReceived(connectionToken: ConnectionToken, stats: ConnectionStats, bytes: ByteArray) {
        val operation = admitConnectionOperation(connectionToken, requireReady = false) ?: return
        try {
            stats.packetsReceived++
            stats.bytesReceived += bytes.size
            Logger.d {
                "[$address] Serial received packet #${stats.packetsReceived} - " +
                    "${bytes.size} byte(s) (Total RX: ${stats.bytesReceived} bytes)"
            }
            bytes.forEach(::readChar)
        } finally {
            operation.lease.release()
        }
    }

    private fun handleDisconnected(
        connectionToken: ConnectionToken,
        stats: ConnectionStats,
        device: UsbSerialDriver,
        thrown: Exception?,
    ) {
        if (lifecycle.isClosed) return
        if (!disconnectConnection(connectionToken, waitForStopped = false, isPermanent = false)) return

        val uptime = if (stats.connectedAt > 0) nowMillis - stats.connectedAt else 0
        thrown?.let { error -> Logger.w(error) { "[$address] Serial error after ${uptime}ms: ${error.message}" } }
        Logger.w {
            "[$address] Serial device disconnected - Device: $device, Uptime: ${uptime}ms, " +
                "Packets RX: ${stats.packetsReceived} (${stats.bytesReceived} bytes)"
        }
    }

    private fun publishConnection(connectionToken: ConnectionToken, connection: SerialConnection): Boolean =
        synchronized(connectionAdmissionLock) {
            val generationAvailable =
                !lifecycle.isClosed &&
                    !connectionCleanupInProgress &&
                    activeConnectionToken == null &&
                    connRef.get() == null
            if (generationAvailable) {
                connectionToken.connection = connection
                connRef.set(connection)
                activeConnectionToken = connectionToken
                connectionReady.set(false)
            }
            generationAvailable
        }

    private fun finishConnectionCleanup(completion: CompletableDeferred<Unit>) {
        synchronized(connectionAdmissionLock) {
            if (connectionCleanupCompletion === completion) {
                connectionCleanupCompletion = null
                connectionCleanupInProgress = false
            }
        }
        completion.complete(Unit)
    }

    override fun handleSendToRadio(p: ByteArray): Boolean {
        val transportLease = lifecycle.tryAcquire()
        val operation =
            transportLease?.let {
                val token = synchronized(connectionAdmissionLock) { activeConnectionToken }
                token?.let { active -> admitConnectionOperation(active, requireReady = true) }
            }
        return if (transportLease == null || operation == null) {
            transportLease?.release()
            Logger.w { "[$address] Serial connection not available, cannot send ${p.size} bytes" }
            false
        } else {
            queueFramedSend(
                payload = p,
                writer = { bytes -> operation.connection.sendBytes(bytes) },
                onCompletion = {
                    operation.lease.release()
                    transportLease.release()
                },
            )
        }
    }

    private fun admitConnectionOperation(token: ConnectionToken, requireReady: Boolean): ConnectionOperation? =
        synchronized(connectionAdmissionLock) {
            val connection = connRef.get()
            val ready = !requireReady || connectionReady.get()
            if (connection == null || !ownsConnectionLocked(token, connection) || !ready) {
                null
            } else {
                token.lifecycle.tryAcquire()?.let { lease -> ConnectionOperation(token, connection, lease) }
            }
        }

    override fun keepAlive() {
        scope.handledLaunch { heartbeatSender.sendHeartbeat() }
    }

    override fun sendBytes(p: ByteArray) {
        // Raw stream writes are part of the pre-handshake wake/framing path, so they may run before the connection
        // generation reports ready. Normal packet sends through handleSendToRadio require a ready generation.
        val transportLease = lifecycle.tryAcquire()
        val token = synchronized(connectionAdmissionLock) { activeConnectionToken }
        val operation =
            transportLease?.let { token?.let { active -> admitConnectionOperation(active, requireReady = false) } }
        if (transportLease == null || operation == null) {
            transportLease?.release()
            Logger.w { "[$address] Serial connection not available, cannot send ${p.size} bytes" }
            return
        }
        try {
            Logger.d { "[$address] Serial queueing ${p.size} bytes" }
            operation.connection.sendBytes(p)
        } finally {
            operation.lease.release()
            transportLease.release()
        }
    }
}
