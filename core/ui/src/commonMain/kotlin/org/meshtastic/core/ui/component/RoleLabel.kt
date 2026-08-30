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
package org.meshtastic.core.ui.component

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.role_client
import org.meshtastic.core.resources.role_client_base
import org.meshtastic.core.resources.role_client_hidden
import org.meshtastic.core.resources.role_client_mute
import org.meshtastic.core.resources.role_lost_and_found
import org.meshtastic.core.resources.role_repeater
import org.meshtastic.core.resources.role_router
import org.meshtastic.core.resources.role_router_client
import org.meshtastic.core.resources.role_router_late
import org.meshtastic.core.resources.role_sensor
import org.meshtastic.core.resources.role_tak
import org.meshtastic.core.resources.role_tak_tracker
import org.meshtastic.core.resources.role_tracker
import org.meshtastic.proto.Config

/**
 * The human-readable name of a device role.
 *
 * Every surface that shows a role to a user goes through this. They used to render `Role.name` — the protobuf
 * identifier — so the node list, the node detail screen and the map all said `CLIENT_HIDDEN` at people, untranslated.
 * The device settings screen already had the matching `role_*_desc` sentences; this is the short label to go with them.
 *
 * Exhaustive with no `else`, matching that screen's `description`: a role added to the protos should be a compile error
 * here, not a silent fallback to some other role's name.
 *
 * Only for display. Anything serialized — the node database export, the AI function payloads — keeps `Role.name`, which
 * is a wire identifier and must not be translated.
 */
@Suppress("DEPRECATION") // ROUTER_CLIENT is deprecated but still a value a node can report.
val Config.DeviceConfig.Role.label: StringResource
    get() =
        when (this) {
            Config.DeviceConfig.Role.CLIENT -> Res.string.role_client
            Config.DeviceConfig.Role.CLIENT_MUTE -> Res.string.role_client_mute
            Config.DeviceConfig.Role.ROUTER -> Res.string.role_router
            Config.DeviceConfig.Role.ROUTER_CLIENT -> Res.string.role_router_client
            Config.DeviceConfig.Role.REPEATER -> Res.string.role_repeater
            Config.DeviceConfig.Role.TRACKER -> Res.string.role_tracker
            Config.DeviceConfig.Role.SENSOR -> Res.string.role_sensor
            Config.DeviceConfig.Role.TAK -> Res.string.role_tak
            Config.DeviceConfig.Role.CLIENT_HIDDEN -> Res.string.role_client_hidden
            Config.DeviceConfig.Role.LOST_AND_FOUND -> Res.string.role_lost_and_found
            Config.DeviceConfig.Role.TAK_TRACKER -> Res.string.role_tak_tracker
            Config.DeviceConfig.Role.ROUTER_LATE -> Res.string.role_router_late
            Config.DeviceConfig.Role.CLIENT_BASE -> Res.string.role_client_base
        }
