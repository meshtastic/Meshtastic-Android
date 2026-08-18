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
package org.meshtastic.feature.connections.ui

import org.meshtastic.core.ui.util.PermissionStatus

/**
 * What the Connections UI should do when the user takes a TCP-connect action that may need `ACCESS_LOCAL_NETWORK`.
 *
 * The policy is prompt-when-possible, warn-when-not, and NEVER block: on Android 17 (API 37) the permission gates the
 * socket itself — an ungranted local connect times out rather than failing fast — but a TCP address says nothing about
 * locality. A radio reached over a public IP, a port-forward, or a VPN needs no local-network permission at all, so a
 * hard block on the permission would break connections that were never subject to it.
 */
enum class LocalNetworkGateAction {
    /** The permission is held; run the connect. */
    PROCEED,

    /** The system will still show a prompt; request in-context and run the connect once the request resolves. */
    REQUEST_PERMISSION,

    /**
     * The system will no longer prompt. Surface the warning that names the fix (system settings), then run the connect
     * anyway — the target may not be local, and the OS enforces the permission at the socket regardless.
     */
    PROCEED_WITH_WARNING,
}

/**
 * Pure classifier for gating a TCP-connect action on `ACCESS_LOCAL_NETWORK`. Kept side-effect-free and
 * platform-agnostic so it can be unit-tested in `commonTest` without an Android `Activity`.
 *
 * Inert everywhere else: `rememberLocalNetworkPermissionState()` reports [PermissionStatus.GRANTED] below API 37 and on
 * desktop/iOS, so this always returns [LocalNetworkGateAction.PROCEED] there.
 */
fun localNetworkGateAction(status: PermissionStatus): LocalNetworkGateAction = when (status) {
    PermissionStatus.GRANTED -> LocalNetworkGateAction.PROCEED
    PermissionStatus.PERMANENTLY_DENIED -> LocalNetworkGateAction.PROCEED_WITH_WARNING
    PermissionStatus.NOT_REQUESTED -> LocalNetworkGateAction.REQUEST_PERMISSION
    PermissionStatus.DENIED_CAN_RETRY -> LocalNetworkGateAction.REQUEST_PERMISSION
}

/** How a connect stashed behind an in-flight permission request should be resolved once the status moves. */
enum class PendingTcpConnectResolution {
    /** The grant landed; run the stashed connect. */
    CONNECT,

    /** The request resolved as a denial; warn, then run the stashed connect anyway (see the policy above). */
    CONNECT_WITH_WARNING,

    /** The status has not moved since the stash; the request is still unresolved. */
    KEEP_WAITING,
}

/**
 * Resolves a TCP connect stashed while a permission request was in flight, given the status at stash time and now.
 *
 * A status *transition* is the only observable signal that the request resolved: the result callback recomputes the
 * status, but a same-value recomputation is invisible to composition. Denials do transition on modern Android
 * (NOT_REQUESTED → DENIED_CAN_RETRY, and a second denial → PERMANENTLY_DENIED), so the practical dangling case is
 * limited to OEM flows that re-answer with an identical status — where the stash simply waits to be overwritten by the
 * next tap.
 */
fun resolvePendingTcpConnect(
    stashedStatus: PermissionStatus,
    currentStatus: PermissionStatus,
): PendingTcpConnectResolution = when {
    currentStatus == PermissionStatus.GRANTED -> PendingTcpConnectResolution.CONNECT
    currentStatus != stashedStatus -> PendingTcpConnectResolution.CONNECT_WITH_WARNING
    else -> PendingTcpConnectResolution.KEEP_WAITING
}
