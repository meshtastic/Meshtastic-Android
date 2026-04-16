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
package org.meshtastic.core.takserver

import org.meshtastic.proto.MemberRole
import org.meshtastic.proto.Team
import org.meshtastic.proto.User

// Port 8089 is the standard TAK TLS port. Matches the iOS implementation so that
// a single exported data package (containing truststore.p12 + client.p12) works for
// both Meshtastic-iOS and Meshtastic-Android without reconfiguration in ATAK/iTAK.
internal const val DEFAULT_TAK_PORT = 8089
internal const val DEFAULT_TAK_ENDPOINT = "0.0.0.0:4242:tcp"

// Bundled certificate password — matches iOS (`"meshtastic"`). Used for the
// server.p12 / client.p12 PKCS#12 files shipped under `tak_certs/` on the classpath.
internal const val TAK_BUNDLED_CERT_PASSWORD = "meshtastic"
internal const val DEFAULT_TAK_TEAM_NAME = "Cyan"
internal const val DEFAULT_TAK_ROLE_NAME = "Team Member"
internal const val DEFAULT_TAK_BATTERY = 100
internal const val DEFAULT_TAK_STALE_MINUTES = 10
internal const val TAK_HEX_RADIX = 16
internal const val TAK_XML_READ_BUFFER_SIZE = 4_096
// ATAK's native commo library declares the connection dead after 25 seconds of
// silence (RX_TIMEOUT_SECONDS in streamingsocketmanagement.cpp) and starts
// sending t-x-c-t pings at 15 seconds (RX_STALE_SECONDS). Send keepalives
// well under the 15-second threshold so ATAK never enters its stale phase.
internal const val TAK_KEEPALIVE_INTERVAL_MS = 10_000L
internal const val TAK_ACCEPT_LOOP_DELAY_MS = 100L
internal const val TAK_COORDINATE_SCALE = 1e7
internal const val TAK_UNKNOWN_POINT_VALUE = 9_999_999.0
internal const val TAK_DIRECT_MESSAGE_PARTS_MIN = 3

/**
 * Hard cap on the size of a TAK v2 wire payload we will hand to the mesh layer.
 *
 * `CommandSenderImpl.sendData` checks `Data.ADAPTER.isWithinSizeLimit(data,
 * Constants.DATA_PAYLOAD_LEN.value)` where `DATA_PAYLOAD_LEN = 233`. That 233 applies
 * to the ENTIRE encoded `Data` proto (portnum tag + payload length-delim + reply_id +
 * emoji), not just the `payload` bytes. The wrapper for a port-78 (`ATAK_PLUGIN_V2`)
 * message costs roughly:
 *   * portnum varint + tag: 2 bytes
 *   * payload length prefix + tag: 2–3 bytes (depending on size)
 *   * reply_id / emoji: 0 bytes when unset
 *
 * That leaves ~228 bytes for the `payload` field alone. We use 225 to keep a small
 * margin for future proto evolution. Anything larger than this is dropped in
 * [TAKMeshIntegration.sendCoTToMesh] rather than being handed to the mesh layer,
 * because the mesh layer would throw and the outer `SharedFlow` collector would eat
 * the crash on every subsequent emission.
 */
internal const val MAX_TAK_WIRE_PAYLOAD_BYTES = 225

/**
 * Max characters of raw CoT XML we'll write to logcat when dropping an oversized
 * packet. ATAK can emit events several KB long; logging the whole thing floods
 * logcat and buries the signal. 1024 chars is enough to see the event type, point,
 * and the first few detail elements.
 */
internal const val TAK_LOG_XML_MAX_CHARS = 1_024

internal fun Team?.toTakTeamName(): String = when (this) {
    null,
    Team.Unspecifed_Color,
    -> DEFAULT_TAK_TEAM_NAME
    else -> name.replace('_', ' ')
}

internal fun MemberRole?.toTakRoleName(): String = when (this) {
    null,
    MemberRole.Unspecifed,
    -> DEFAULT_TAK_ROLE_NAME
    MemberRole.TeamMember -> DEFAULT_TAK_ROLE_NAME
    MemberRole.TeamLead -> "Team Lead"
    MemberRole.ForwardObserver -> "Forward Observer"
    else -> name
}

internal fun User.toTakCallsign(): String = when {
    short_name.isNotBlank() -> short_name
    long_name.isNotBlank() -> long_name
    else -> id
}
