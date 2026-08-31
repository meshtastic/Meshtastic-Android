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
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.handledLaunch
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.transport.StreamFrameCodec
import org.meshtastic.core.network.transport.TcpTransport
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportCallback
import kotlin.time.Duration.Companion.seconds

/** Minimal TCP connection surface used by [TcpRadioTransport] to coordinate admission and teardown. */
internal interface TcpRadioConnection {
    val isConnected: Boolean

    fun start(address: String)

    fun stop()

    suspend fun sendPacket(payload: ByteArray)

    suspend fun sendHeartbeat()
}

private class TcpRadioConnectionImpl(private val delegate: TcpTransport) : TcpRadioConnection {
    override val isConnected: Boolean
        get() = delegate.isConnected

    override fun start(address: String) = delegate.start(address)

    override fun stop() = delegate.stop()

    override suspend fun sendPacket(payload: ByteArray) = delegate.sendPacket(payload)

    override suspend fun sendHeartbeat() = delegate.sendHeartbeat()
}

/**
 * TCP radio transport — thin adapter over the shared [TcpTransport] from `core:network`.
 *
 * Implements [RadioTransport] directly via composition over [TcpTransport], delegating send/receive to the transport
 * and calling [RadioTransportCallback] for lifecycle events. This avoids the previous inheritance from
 * [StreamTransport] which created a dead [StreamFrameCodec] and required overriding `sendBytes` as a no-op.
 */
open class TcpRadioTransport
internal constructor(
    private val callback: RadioTransportCallback,
    private val scope: CoroutineScope,
    private val address: String,
    connectionFactory: (TcpTransport.Listener) -> TcpRadioConnection,
) : RadioTransport {

    constructor(
        callback: RadioTransportCallback,
        scope: CoroutineScope,
        dispatchers: CoroutineDispatchers,
        address: String,
    ) : this(
        callback = callback,
        scope = scope,
        address = address,
        connectionFactory = { listener ->
            TcpRadioConnectionImpl(
                TcpTransport(
                    dispatchers = dispatchers,
                    scope = scope,
                    listener = listener,
                    logTag = "TcpRadioTransport[$address]",
                ),
            )
        },
    )

    companion object {
        const val SERVICE_PORT = StreamFrameCodec.DEFAULT_TCP_PORT
        internal val OPERATION_TIMEOUT = 10.seconds
    }

    private val lifecycle = TransportLifecycleGate("TCP", operationDrainTimeout = OPERATION_TIMEOUT * 2)
    private val transportStopped = atomic(false)
    private val operationJobsLock = SynchronizedObject()
    private val operationJobs = mutableSetOf<Job>()

    private val transport =
        connectionFactory(
            object : TcpTransport.Listener {
                override fun onConnected() {
                    lifecycle.runIfOpen { callback.onConnect() }
                }

                override fun onDisconnected() {
                    // close() first closes admission, suppressing the transient callback caused by transport.stop().
                    lifecycle.runIfOpen { callback.onDisconnect(isPermanent = false) }
                }

                override fun onPacketReceived(bytes: ByteArray) {
                    lifecycle.runIfOpen { callback.handleFromRadio(bytes) }
                }
            },
        )

    override fun start() {
        lifecycle.runIfOpen {
            if (transportStopped.value) {
                Logger.w { "[$address] Ignoring start on a stopped TCP transport; a fresh transport is required" }
            } else {
                transport.start(address)
            }
        }
    }

    override suspend fun close() {
        Logger.d { "[$address] Closing TCP transport" }
        val completed =
            lifecycle.close(
                teardown = {
                    stopTransport()
                    cancelOutstandingOperations()
                },
            )
        if (!completed) Logger.w { "[$address] TCP teardown did not complete within its lifecycle bounds" }
        // Do NOT emit onDisconnect(isPermanent = true) here. The explicit-disconnect signal is the service layer's
        // responsibility (SharedRadioInterfaceService.stopTransportLocked); emitting it here causes a double-disconnect
        // and prevents the auto-reconnect loop from owning its transient lifecycle.
    }

    override fun keepAlive() {
        Logger.d { "[$address] TCP keepAlive" }
        launchConnectionOperation("heartbeat") { transport.sendHeartbeat() }
    }

    override fun handleSendToRadio(p: ByteArray): Boolean =
        launchConnectionOperation("send") { transport.sendPacket(p) }

    /**
     * Schedules one operation while the current transport is connected.
     *
     * The Boolean reports lifecycle admission and successful scheduling only; the operation runs asynchronously and may
     * still fail or time out after this method returns `true`.
     */
    private fun launchConnectionOperation(operation: String, block: suspend () -> Unit): Boolean {
        val lease = lifecycle.tryAcquire() ?: return false
        return if (!transport.isConnected) {
            lease.release()
            false
        } else {
            var completionRegistered = false
            try {
                val job =
                    scope.handledLaunch {
                        val completed = withTimeoutOrNull(OPERATION_TIMEOUT) { block() } != null
                        if (!completed) {
                            Logger.w { "[$address] TCP $operation timed out after $OPERATION_TIMEOUT" }
                            // Cancellation may leave a framed write partially emitted. Stopping this one-shot
                            // transport forces the service reconnect path to create a fresh transport before another
                            // send.
                            stopTransport()
                        }
                    }
                synchronized(operationJobsLock) { operationJobs += job }
                job.invokeOnCompletion {
                    synchronized(operationJobsLock) { operationJobs -= job }
                    lease.release()
                }
                completionRegistered = true
                !job.isCancelled
            } finally {
                if (!completionRegistered) lease.release()
            }
        }
    }

    private suspend fun cancelOutstandingOperations() {
        val jobs = synchronized(operationJobsLock) { operationJobs.toList() }
        jobs.forEach { it.cancel() }
        val joined = withTimeoutOrNull(OPERATION_TIMEOUT) { jobs.joinAll() } != null
        if (!joined) Logger.w { "[$address] TCP operation jobs did not stop after transport teardown" }
    }

    private fun stopTransport() {
        if (transportStopped.compareAndSet(expect = false, update = true)) transport.stop()
    }
}
