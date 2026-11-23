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
@file:Suppress("EmptyFunctionBlock")

package org.meshtastic.desktop.stub

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.meshtastic.core.repository.Location
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.proto.Position as ProtoPosition

// region WorkManager / Location Stubs (Android-only)

class NoopMeshLocationManager : MeshLocationManager {
    override fun start(scope: CoroutineScope, sendPositionFn: suspend (ProtoPosition) -> Boolean) {}

    override fun restart() {}

    override fun stop() {}
}

class NoopLocationRepository : LocationRepository {
    override val receivingLocationUpdates = MutableStateFlow(false)

    override fun getLocations(): Flow<Location> = emptyFlow()
}

// endregion
