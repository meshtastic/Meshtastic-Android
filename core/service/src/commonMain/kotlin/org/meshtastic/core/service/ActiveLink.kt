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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import kotlin.concurrent.Volatile

/** The selected radio's open link. Each one gets its own scope, cancelled when the link closes. */
internal class ActiveLink(val address: String, val link: RadioLink, val scope: CoroutineScope) {
    /** Set when teardown starts, so the link's pipeline cannot open another session. */
    @Volatile var closing = false

    var connect: Job? = null

    /** The link's session pipeline; teardown waits for it so the last session's end is written before it returns. */
    var pipeline: Job? = null

    /** Whether this link has reached [ConnectionState.Connected]; failures before that name a connect problem. */
    @Volatile var connected = false

    /** The reason last shown for this link, so an attempt that fails the same way stays quiet. */
    @Volatile var shownReason: TransportDisconnectReason? = null
}

/**
 * What a connection state's failure means to the user. A reconnect that follows a working connection says nothing: the
 * state itself shows the retry.
 */
internal fun ActiveLink.failureReason(state: ConnectionState): TransportDisconnectReason? = when (state) {
    is ConnectionState.Failed ->
        when (state.cause) {
            is MeshtasticException.FirmwareTooOld -> TransportDisconnectReason.FirmwareTooOld

            is MeshtasticException.HandshakeTimeout,
            is MeshtasticException.Protocol,
            -> TransportDisconnectReason.HandshakeRecoveryExhausted

            else -> link.failureReason(state.cause)
        }

    is ConnectionState.Reconnecting -> if (connected) null else link.failureReason(state.cause)

    else -> null
}
