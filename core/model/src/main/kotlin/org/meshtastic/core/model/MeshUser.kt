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
package org.meshtastic.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User as ProtoUser

@Parcelize
data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: HardwareModel,
    val isLicensed: Boolean = false,
    val role: Int = 0,
) : Parcelable {

    override fun toString(): String = "MeshUser(id=${id.anonymize}, " +
        "longName=${longName.anonymize}, " +
        "shortName=${shortName.anonymize}, " +
        "hwModel=$hwModelString, " +
        "isLicensed=$isLicensed, " +
        "role=$role)"

    /** Create our model object from a protobuf. */
    constructor(p: ProtoUser) : this(p.id, p.long_name, p.short_name, p.hw_model, p.is_licensed, p.role.value)

    fun toProto(): ProtoUser = ProtoUser(
        id = id,
        long_name = longName,
        short_name = shortName,
        hw_model = hwModel,
        is_licensed = isLicensed,
        role =
        org.meshtastic.proto.Config.DeviceConfig.Role.fromValue(role)
            ?: org.meshtastic.proto.Config.DeviceConfig.Role.CLIENT,
    )

    /**
     * a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot or null
     * if unset
     */
    val hwModelString: String?
        get() =
            if (hwModel == HardwareModel.UNSET) {
                null
            } else {
                hwModel.name.replace('_', '-').replace('p', '.').lowercase()
            }
}
