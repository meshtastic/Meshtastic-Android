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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.model.ConnectionLifecycle
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Config
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.time.Duration.Companion.seconds

/** Where the user's request to turn transparent mode off stands. */
enum class TransparentModeTurnOff {
    IN_PROGRESS,

    /** The node's LoRa config read back with transparent mode off. */
    TURNED_OFF,

    /** The node answered the read-back and still reports transparent mode on. */
    STILL_ON,

    /** The node did not answer, so its value is unknown. */
    UNCONFIRMED,
}

/**
 * What the app shows about transparent mode for the current connection.
 *
 * @property active the connected node is in transparent mode as far as it has said: it reported the mode on, or a
 *   turn-off has not been confirmed yet. Sends are refused and the indicator shows while this holds.
 * @property noticeVisible the notice is up: the user has not chosen yet, reopened it, or a turn-off is being reported.
 * @property turnOff the turn-off the notice is reporting, or null while it offers the choice.
 */
data class TransparentModeState(
    val active: Boolean = false,
    val noticeVisible: Boolean = false,
    val turnOff: TransparentModeTurnOff? = null,
)

/**
 * One completed handshake with one node. A choice or a turn-off belongs to the connection it was made on:
 * [completedHandshakes] moves on a reconnect inside one transport instance, which keeps [sessionGeneration].
 * [connectionVersion] is the lifecycle `version` a write on this connection must still find at queue admission.
 */
internal data class TransparentModeSession(
    val sessionGeneration: Long,
    val nodeNum: Int,
    val completedHandshakes: Long,
    val connectionVersion: Long,
)

internal data class TransparentModeAttempt(val session: TransparentModeSession, val progress: TransparentModeTurnOff)

internal fun transparentModeSessionFor(
    lifecycle: ConnectionLifecycle,
    identity: ConnectionIdentity?,
): TransparentModeSession? = identity
    ?.takeIf { lifecycle.state == ConnectionState.Connected }
    ?.let {
        TransparentModeSession(
            it.sessionGeneration,
            it.nodeNum,
            lifecycle.epochs.completedHandshakes,
            lifecycle.version,
        )
    }

internal fun transparentModeStateFor(
    session: TransparentModeSession?,
    transparent: Boolean,
    leftOnSession: TransparentModeSession?,
    attempt: TransparentModeAttempt?,
    turnOnReadBack: TransparentModeSession?,
): TransparentModeState {
    if (session == null) return TransparentModeState()
    val turnOff = attempt?.takeIf { it.session == session }?.progress
    val asks = transparent && leftOnSession != session && turnOnReadBack != session
    return TransparentModeState(
        active = transparent || turnOff == TransparentModeTurnOff.IN_PROGRESS,
        noticeVisible = turnOff != null || asks,
        turnOff = turnOff,
    )
}

/** The reported LoRa config with only `transparent_mode` cleared, as the `set_config` that turns it off carries it. */
internal fun Config.LoRaConfig.withTransparentModeOff(): Config =
    Config.Builder().also { wb -> wb.lora = newBuilder().also { lb -> lb.transparent_mode = false }.build() }.build()

/** The LoRa config in the node's answer to admin request [requestId], or null for any other packet. */
internal fun MeshPacket.loraConfigResponseTo(requestId: Int, nodeNum: Int): Config.LoRaConfig? {
    val data = decoded?.takeIf { from == nodeNum && it.portnum == PortNum.ADMIN_APP && it.request_id == requestId }
    return data?.let { safeCatching { AdminMessage.ADAPTER.decode(it.payload) }.getOrNull() }?.get_config_response?.lora
}

/**
 * Tracks whether the connected node is in transparent mode (a raw LoRa modem for a host such as meshtasticd), what the
 * user chose to do about it for this connection, and turns it off on request.
 *
 * It never writes the local config cache. The cache follows the node's own answers, which the admin handler stores
 * bound to the session they arrived on, so a turn-off that outlives its connection cannot put one node's config in
 * another's cache.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransparentModeController(
    radioConfigRepository: RadioConfigRepository,
    private val serviceRepository: ServiceRepository,
    private val radioController: RadioController,
    nodeManager: NodeManager,
    private val scope: CoroutineScope,
) {
    private val connectionIdentity: StateFlow<ConnectionIdentity?> = nodeManager.connectionIdentity
    private val leftOnSession = MutableStateFlow<TransparentModeSession?>(null)
    private val attempt = MutableStateFlow<TransparentModeAttempt?>(null)
    private val turnOnReadBack = MutableStateFlow<TransparentModeSession?>(null)

    private val session: Flow<TransparentModeSession?> =
        combine(serviceRepository.connectionLifecycle, connectionIdentity, ::transparentModeSessionFor)
            .distinctUntilChanged()

    private fun currentSession(): TransparentModeSession? =
        transparentModeSessionFor(serviceRepository.connectionLifecycle.value, connectionIdentity.value)

    private fun currentSessionOn(connection: LocalNodeConnection?): TransparentModeSession? = currentSession()?.takeIf {
        connection == null ||
            (it.nodeNum == connection.nodeNum && it.connectionVersion == connection.connectionVersion)
    }

    val state: Flow<TransparentModeState> =
        combine(
            session,
            radioConfigRepository.localConfigFlow.map { it.lora?.transparent_mode == true }.distinctUntilChanged(),
            leftOnSession,
            attempt,
            turnOnReadBack,
            ::transparentModeStateFor,
        )
            .distinctUntilChanged()

    /** Whether the connected node would drop a packet sent through it right now. */
    val active: Flow<Boolean> = state.map { it.active }.distinctUntilChanged()

    /** Closes the notice for this connection, leaving the node as it is. */
    fun closeNotice() {
        val current = currentSession() ?: return
        if (attempt.value == TransparentModeAttempt(current, TransparentModeTurnOff.IN_PROGRESS)) return
        leftOnSession.value = current
        attempt.value = null
    }

    fun reopenNotice() {
        leftOnSession.value = null
    }

    /**
     * Reads `transparent_mode` back after the user saved it on from settings over [savedOn], and returns what the node
     * reports, or null when it did not answer on that connection or the connection has changed since. The notice stays
     * back while the read is out, and afterwards unless the node reports the mode off: firmware that does not know the
     * field accepts the write and drops it, so that is not the user's choice taking effect.
     */
    suspend fun confirmTurnedOnByUser(savedOn: LocalNodeConnection): Boolean? {
        val started = currentSessionOn(savedOn) ?: return null
        turnOnReadBack.value = started
        return try {
            val reported = safeCatching { readLoraConfig(started.nodeNum) }.getOrNull()?.transparent_mode
            val sameSession = currentSession() == started
            if (sameSession && reported != false) leftOnSession.value = started
            reported.takeIf { sameSession }
        } finally {
            turnOnReadBack.compareAndSet(started, null)
        }
    }

    /**
     * Reads the node's LoRa config, sends it back with only `transparent_mode` cleared, and reads it again. The outcome
     * is [TransparentModeTurnOff.TURNED_OFF] only when that second read says so. The attempt runs in the controller's
     * own scope and ends without an outcome when its connection does. With [savedOn] it starts only while that is still
     * the connection, so a settings save cannot hand its turn-off to another one.
     */
    fun turnOff(savedOn: LocalNodeConnection? = null) {
        val started = currentSessionOn(savedOn) ?: return
        val inProgress = TransparentModeAttempt(started, TransparentModeTurnOff.IN_PROGRESS)
        if (attempt.value == inProgress) return
        attempt.value = inProgress
        scope.launch {
            var outcome: TransparentModeTurnOff? = null
            try {
                outcome =
                    session
                        .map { it == started }
                        .distinctUntilChanged()
                        .mapLatest { same -> if (same) readWriteAndReadBack(started) else null }
                        .first()
            } finally {
                // Every exit clears the spinner: an attempt left IN_PROGRESS can be neither closed nor retried.
                attempt.compareAndSet(inProgress, outcome?.let { TransparentModeAttempt(started, it) })
            }
        }
    }

    private suspend fun readWriteAndReadBack(started: TransparentModeSession): TransparentModeTurnOff {
        val nodeNum = started.nodeNum
        val readBack = safeCatching {
            // What the node says now, not the cache: an earlier save the node dropped or adjusted is still cached.
            // The write is admitted only on the connection that was read, checked where it is queued.
            readLoraConfig(nodeNum)
                ?.takeIf { currentSession() == started }
                ?.let { reported ->
                    radioController.sendLocalConfig(
                        reported.withTransparentModeOff(),
                        expectedNodeNum = nodeNum,
                        expectedConnectionVersion = started.connectionVersion,
                    )
                    readLoraConfig(nodeNum)
                }
        }
            .getOrNull()
        return when {
            readBack == null -> TransparentModeTurnOff.UNCONFIRMED
            readBack.transparent_mode -> TransparentModeTurnOff.STILL_ON
            else -> TransparentModeTurnOff.TURNED_OFF
        }
    }

    private suspend fun readLoraConfig(nodeNum: Int): Config.LoRaConfig? = withTimeoutOrNull(RESPONSE_TIMEOUT) {
        coroutineScope {
            val requestId = radioController.generatePacketId()
            // Subscribed before the request goes out: the local node answers through a synchronous loopback.
            val response =
                async(start = CoroutineStart.UNDISPATCHED) {
                    serviceRepository.meshPacketFlow
                        .mapNotNull { it.loraConfigResponseTo(requestId, nodeNum) }
                        .first()
                }
            radioController.getConfig(nodeNum, AdminMessage.ConfigType.LORA_CONFIG.value, requestId)
            response.await()
        }
    }

    companion object {
        val RESPONSE_TIMEOUT = 10.seconds
    }
}
