/*
 * NTsocial MeshLink original work and modifications:
 * Copyright (c) 2026 LiberaNt LLC
 *
 * Meshtastic Android-derived portions, where present:
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Developed and/or modified for NTsocial MeshLink in 2026.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
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
package com.ntsocial.meshlink.core.gateway.apple

/** Language-neutral constants for the Apple App Group Gateway boundary. */
object AppleGatewayContract {
    const val SCHEMA_VERSION = 1
    const val APP_GROUP_IDENTIFIER = "group.com.ntsocial.gateway"
    const val KEYCHAIN_ACCESS_GROUP_SUFFIX = "com.ntsocial.meshlink.gateway"
    const val PARENT_CALLER_ID = "com.ntsocial.ios"
    const val COMPANION_BUNDLE_IDENTIFIER = "com.ntsocial.meshlink.ios"
    const val COMPANION_URL_SCHEME = "ntsocial-meshlink"
    const val PROCESS_DEEP_LINK = "ntsocial-meshlink://process"
    const val COMMAND_AVAILABLE_NOTIFICATION = "com.ntsocial.meshlink.gateway.command-available"
    const val STATE_CHANGED_NOTIFICATION = "com.ntsocial.meshlink.gateway.state-changed"

    const val ROUTE_TOKEN_SIZE_BYTES = 32
    const val ROUTE_TTL_MILLIS = 120_000L
    const val COMMAND_MAX_LIFETIME_MILLIS = 120_000L
    const val COMMAND_CLOCK_SKEW_MILLIS = 30_000L
    const val COMMAND_CLAIM_RECLAIM_MILLIS = 30_000L
    const val COMMAND_NONCE_SIZE_BYTES = 16
    const val AUTHENTICATION_KEY_SIZE_BYTES = 32
    const val AUTHENTICATION_TAG_SIZE_BYTES = 32
    const val CLIENT_MESSAGE_ID_HEX_LENGTH = 32
    const val REQUEST_FINGERPRINT_HEX_LENGTH = 64
    const val MAX_LEDGER_RECORDS_PER_CALLER = 256
    const val MAX_OVERLAY_INGRESS_RECORDS = 128
    const val DEFAULT_NATIVE_MESSAGE_CHANGE_PAGE_SIZE = 100
    const val MAX_NATIVE_MESSAGE_CHANGE_PAGE_SIZE = 200
    const val MAX_REQUEST_ID_LENGTH = 128
    const val MAX_CALLER_ID_LENGTH = 128
    const val MAX_SOURCE_CHANNEL_ID_LENGTH = 128
    const val MAX_GENERATION_LENGTH = 128
}
