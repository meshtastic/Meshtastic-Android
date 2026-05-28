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

import org.meshtastic.core.model.util.anonymize
import org.meshtastic.proto.HardwareModel

data class MeshUser(
    val id: String,
    val longName: String,
    val shortName: String,
    val hwModel: HardwareModel,
    val isLicensed: Boolean = false,
    val role: Int = 0,
) {

    override fun toString(): String = "MeshUser(id=${id.anonymize}, " +
        "longName=${longName.anonymize}, " +
        "shortName=${shortName.anonymize}, " +
        "hwModel=$hwModelString, " +
        "isLicensed=$isLicensed, " +
        "role=$role)"

    /** Create our model object from a protobuf. */
    constructor(
        p: org.meshtastic.proto.User,
    ) : this(p.id, p.long_name, p.short_name, p.hw_model, p.is_licensed, p.role.value)

    /**
     * A pretty lowercase rendering of the hardware model: underscores → dashes, version markers `<digit>p<digit>` →
     * `<digit>.<digit>` (e.g. `RAK4631_V1P0` → `rak4631-v1.0`). Returns null when the model is unset. The
     * version-marker substitution is constrained to digit-p-digit so model names containing a literal 'p' (e.g.
     * `HELTEC_WIRELESS_PAPER`) are preserved correctly.
     */
    val hwModelString: String?
        get() =
            if (hwModel == HardwareModel.UNSET) {
                null
            } else {
                hwModel.name.replace('_', '-').replace(VERSION_P_REGEX, "$1.$2").lowercase()
            }

    companion object {
        private val VERSION_P_REGEX = Regex("(\\d)p(\\d)", RegexOption.IGNORE_CASE)
    }
}
