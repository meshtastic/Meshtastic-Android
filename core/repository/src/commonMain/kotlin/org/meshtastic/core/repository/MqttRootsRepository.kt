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

import org.meshtastic.core.model.MqttRootsResponse

/**
 * The MQTT root directory the topic finder places a user with, seeded from a bundled asset and refreshed from
 * `resource/mqttRoots`.
 */
interface MqttRootsRepository {
    /** Current best-known directory: bundled seed until a fetch has landed, then that fetch. */
    suspend fun getSnapshot(): MqttRootsResponse

    /** Best-effort network refresh. Never throws; a failed or empty fetch leaves the cache untouched. */
    suspend fun reconcile()
}
