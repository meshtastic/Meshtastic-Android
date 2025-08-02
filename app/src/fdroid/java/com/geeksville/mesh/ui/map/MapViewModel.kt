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

package com.geeksville.mesh.ui.map

import android.content.SharedPreferences
import androidx.core.content.edit
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MapViewModel
@Inject
constructor(
    preferences: SharedPreferences,
    packetRepository: PacketRepository,
    nodeRepository: NodeRepository,
) : BaseMapViewModel(preferences, nodeRepository, packetRepository) {

    var mapStyleId: Int
        get() = preferences.getInt(MAP_STYLE_ID, 0)
        set(value) = preferences.edit { putInt(MAP_STYLE_ID, value) }
}
