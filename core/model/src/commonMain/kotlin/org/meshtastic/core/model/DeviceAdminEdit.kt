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

import org.meshtastic.proto.Channel
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.User

/**
 * Receiver interface for batched admin writes inside an [DeviceAdmin.editSettings] block.
 *
 * Methods queue writes without awaiting individual acknowledgements. The enclosing
 * `editSettings` call handles `begin_edit_settings` / `commit_edit_settings` framing so
 * the device applies all writes atomically.
 */
interface DeviceAdminEdit {
    suspend fun setConfig(config: Config)
    suspend fun setModuleConfig(config: ModuleConfig)
    suspend fun setOwner(user: User)
    suspend fun setChannel(channel: Channel)
}
