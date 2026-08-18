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

/** What the Connections UI should do when the user takes an action that needs local-network access. */
enum class LocalNetworkGateAction {
    /** The permission is held; run the action. */
    PROCEED,

    /** The system will still show a prompt; request in-context. */
    REQUEST_PERMISSION,

    /** The system will no longer prompt; the only route left is the app's settings page. */
    OPEN_APP_SETTINGS,
}

/**
 * Pure classifier for gating a local-network action on `ACCESS_LOCAL_NETWORK`. Kept side-effect-free and
 * platform-agnostic so it can be unit-tested in `commonTest` without an Android `Activity`.
 *
 * On Android 17 (API 37) the permission gates the socket, not merely discovery: an outgoing TCP connection to a LAN
 * address without it does not fail fast, it *times out*. Every path that opens a socket to a radio therefore has to be
 * gated, not just NSD/mDNS scanning — see `DeviceList`'s manual-address entry and the recent-address list, both of
 * which render without a scan ever having run.
 *
 * Inert everywhere else: `rememberLocalNetworkPermissionState()` reports [PermissionStatus.GRANTED] below API 37 and on
 * desktop/iOS, so this always returns [LocalNetworkGateAction.PROCEED] there.
 */
fun localNetworkGateAction(status: PermissionStatus): LocalNetworkGateAction = when (status) {
    PermissionStatus.GRANTED -> LocalNetworkGateAction.PROCEED
    PermissionStatus.PERMANENTLY_DENIED -> LocalNetworkGateAction.OPEN_APP_SETTINGS
    PermissionStatus.NOT_REQUESTED -> LocalNetworkGateAction.REQUEST_PERMISSION
    PermissionStatus.DENIED_CAN_RETRY -> LocalNetworkGateAction.REQUEST_PERMISSION
}
