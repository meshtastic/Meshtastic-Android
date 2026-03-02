/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package com.geeksville.mesh.service

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.repository.NodeManager
import org.meshtastic.proto.MeshPacket
import javax.inject.Inject
import javax.inject.Singleton
import org.meshtastic.core.model.util.MeshDataMapper as CommonMeshDataMapper

@Singleton
class MeshDataMapper @Inject constructor(private val nodeManager: NodeManager) {
    private val commonMapper = CommonMeshDataMapper(nodeManager)

    fun toNodeID(n: Int): String = nodeManager.toNodeID(n)

    fun toDataPacket(packet: MeshPacket): DataPacket? = commonMapper.toDataPacket(packet)
}
