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
package org.meshtastic.feature.connections.model

import org.meshtastic.node.host.Bearer
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.NetworkConfig.ProtocolFlags

/** The `network.enabled_protocols` bit that drives this bearer, or null when no bit does. */
internal val Bearer.protocolFlag: ProtocolFlags?
    get() =
        when (this) {
            Bearer.UDP -> ProtocolFlags.UDP_BROADCAST
            Bearer.BLE_ADV -> ProtocolFlags.BLE_BROADCAST
            Bearer.GATT -> ProtocolFlags.BLE_GATT_PEER
            else -> null
        }

/** MQTT is set up on its own module screen, so the card leaves it out. */
internal val Bearer.onCard: Boolean
    get() = this != Bearer.MQTT

/**
 * A card switch either persists a host bearer in the node or flips the bearer's `enabled_protocols` bit. LoRa follows
 * `lora.region`, which has no off position that keeps the region, so it has no switch.
 */
internal val Bearer.switchable: Boolean
    get() = !fromConfig || protocolFlag != null

/** This network section with [flag] set or cleared and every other bit and field untouched. */
internal fun Config.NetworkConfig.withProtocol(flag: ProtocolFlags, on: Boolean): Config.NetworkConfig = newBuilder()
    .also { wb ->
        wb.enabled_protocols = if (on) enabled_protocols or flag.value else enabled_protocols and flag.value.inv()
    }
    .build()
