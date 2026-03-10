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
package org.meshtastic.app.settings

import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.MeshLogPrefs
import org.meshtastic.core.repository.MeshLogRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.ui.util.AlertManager
import org.meshtastic.feature.settings.debugging.DebugViewModel
import java.util.Locale

@KoinViewModel
class AndroidDebugViewModel(
    meshLogRepository: MeshLogRepository,
    nodeRepository: NodeRepository,
    meshLogPrefs: MeshLogPrefs,
    alertManager: AlertManager,
) : DebugViewModel(meshLogRepository, nodeRepository, meshLogPrefs, alertManager) {

    override fun Int.toHex(length: Int): String = "!%0${length}x".format(Locale.getDefault(), this)

    override fun Byte.toHex(): String = "%02x".format(Locale.getDefault(), this)
}
