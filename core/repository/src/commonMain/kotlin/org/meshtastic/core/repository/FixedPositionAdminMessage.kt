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

import org.meshtastic.core.model.Position
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Position as ProtoPosition

/** Converts the model position to the exact protobuf payload used by fixed-position admin commands. */
fun Position.toFixedPositionProto(): ProtoPosition = ProtoPosition.Builder()
    .also { wb ->
        wb.latitude_i = Position.degI(latitude)
        wb.longitude_i = Position.degI(longitude)
        wb.altitude = altitude
    }
    .build()

/** Builds the device admin command for setting or removing a fixed position. */
fun Position.toFixedPositionAdminMessage(): AdminMessage = if (isFixedPositionRemoval()) {
    AdminMessage.Builder().also { wb -> wb.remove_fixed_position = true }.build()
} else {
    AdminMessage.Builder().also { wb -> wb.set_fixed_position = toFixedPositionProto() }.build()
}
