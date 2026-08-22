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
package org.meshtastic.core.network.transport

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.proto.Heartbeat
import org.meshtastic.proto.ToRadio
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Shared heartbeat sender for Meshtastic radio transports.
 *
 * Constructs and sends a `ToRadio(heartbeat = Heartbeat(nonce = ...))` message to keep the firmware's idle timer from
 * expiring. Each call uses a monotonically increasing nonce to prevent the firmware's per-connection duplicate-write
 * filter from silently dropping it.
 *
 * @param sendToRadio callback that reports whether the transport accepted the encoded heartbeat bytes
 * @param afterHeartbeat optional suspend callback invoked after sending (e.g. to schedule a drain)
 * @param logTag tag for log messages
 */
class HeartbeatSender
private constructor(
    private val sendToRadio: (ByteArray) -> Boolean,
    private val afterHeartbeat: (suspend () -> Unit)?,
    private val logTag: String,
    private val rejectionLogSink: HeartbeatRejectionLogSink,
) {
    constructor(
        sendToRadio: (ByteArray) -> Boolean,
        afterHeartbeat: (suspend () -> Unit)? = null,
        logTag: String = "HeartbeatSender",
    ) : this(sendToRadio, afterHeartbeat, logTag, HeartbeatRejectionLogSink(::logHeartbeatRejection))

    internal constructor(
        sendToRadio: (ByteArray) -> Boolean,
        afterHeartbeat: (suspend () -> Unit)? = null,
        logTag: String = "HeartbeatSender",
        rejectionLogger: (HeartbeatRejectionLogLevel, String) -> Unit,
    ) : this(sendToRadio, afterHeartbeat, logTag, HeartbeatRejectionLogSink(rejectionLogger))

    @OptIn(ExperimentalAtomicApi::class)
    private val nonce = AtomicInt(0)
    private val nonceMutex = Mutex()

    private val rejectionLogPolicy = HeartbeatRejectionLogPolicy()

    /**
     * Sends a heartbeat to the radio.
     *
     * The firmware responds to heartbeats by queuing a `queueStatus` FromRadio packet, proving the link is alive and
     * keeping the local node's lastHeard timestamp current.
     *
     * Repeated rejections while disconnected are expected (e.g. a radio that left BLE range), so only the first
     * [HeartbeatRejectionLogPolicy.DEFAULT_WARN_LIMIT] consecutive rejections log at warn severity; later ones are
     * demoted to debug until a successful send resets the streak.
     *
     * @return `true` when the transport accepted the heartbeat handoff.
     */
    @OptIn(ExperimentalAtomicApi::class)
    suspend fun sendHeartbeat(): Boolean {
        val (accepted, rejectionLogLevel) =
            nonceMutex.withLock {
                val n = nonce.load()
                Logger.v { "[$logTag] Sending ToRadio heartbeat (nonce=$n)" }
                val admitted = sendToRadio(ToRadio(heartbeat = Heartbeat(nonce = n)).encode())
                if (admitted) nonce.fetchAndAdd(1)
                admitted to rejectionLogPolicy.record(admitted)
            }
        if (rejectionLogLevel != null) {
            val message = "[$logTag] Heartbeat handoff was rejected by the transport"
            rejectionLogSink.log(rejectionLogLevel, message)
        }
        if (!accepted) return false
        afterHeartbeat?.invoke()
        return true
    }
}

private fun interface HeartbeatRejectionLogSink {
    fun log(level: HeartbeatRejectionLogLevel, message: String)
}

private fun logHeartbeatRejection(level: HeartbeatRejectionLogLevel, message: String) {
    when (level) {
        HeartbeatRejectionLogLevel.Warn -> Logger.w { message }
        HeartbeatRejectionLogLevel.Debug -> Logger.d { message }
    }
}

internal enum class HeartbeatRejectionLogLevel {
    Warn,
    Debug,
}

/**
 * Tracks the generic heartbeat rejection-log streak without coupling tests to Kermit process-wide logger configuration.
 * Cause-specific transport diagnostics stay independent; for example BLE backlog pressure continues to warn on every
 * rejected admission even after this generic heartbeat line has been demoted.
 */
internal class HeartbeatRejectionLogPolicy {
    private var consecutiveRejections = 0

    /** Returns the severity for a rejected handoff, or null for an accepted handoff. */
    fun record(admitted: Boolean): HeartbeatRejectionLogLevel? {
        if (admitted) {
            consecutiveRejections = 0
            return null
        }
        consecutiveRejections++
        return if (consecutiveRejections <= DEFAULT_WARN_LIMIT) {
            HeartbeatRejectionLogLevel.Warn
        } else {
            HeartbeatRejectionLogLevel.Debug
        }
    }

    companion object {
        const val DEFAULT_WARN_LIMIT = 2
    }
}
