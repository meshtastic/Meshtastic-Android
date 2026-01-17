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
package org.meshtastic.core.model.util

import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User

val User.longName: String
    get() = long_name

val User.shortName: String
    get() = short_name

val User.isLicensed: Boolean
    get() = is_licensed

val User.hwModel: HardwareModel
    get() = hw_model

/**
 * a string version of the hardware model, converted into pretty lowercase and changing _ to -, and p to dot or null if
 * unset
 */
val User.hwModelString: String?
    get() =
        if (hw_model == HardwareModel.UNSET) {
            null
        } else {
            hw_model.name.replace('_', '-').replace('p', '.').lowercase()
        }
