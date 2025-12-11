/*
 * Copyright (c) 2025 Meshtastic LLC
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

import org.meshtastic.proto.MeshProtos

object NodeInfoFixtures {
    fun createNodeInfo(
        num: Int = 4,
        userId: String = "+zero",
        longName: String = "User Zero",
        shortName: String = "U0",
        hwModel: MeshProtos.HardwareModel = MeshProtos.HardwareModel.ANDROID_SIM,
        position: Position? = null,
    ): NodeInfo = NodeInfo(num = num, user = MeshUser(userId, longName, shortName, hwModel), position = position)
}
