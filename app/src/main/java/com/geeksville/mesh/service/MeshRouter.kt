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

package com.geeksville.mesh.service

import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the specialized packet handlers for the [MeshService]. This class serves as a central registry and
 * lifecycle manager for all routing sub-components.
 */
@Suppress("LongParameterList")
@Singleton
class MeshRouter
@Inject
constructor(
    val dataHandler: MeshDataHandler,
    val configHandler: MeshConfigHandler,
    val tracerouteHandler: MeshTracerouteHandler,
    val neighborInfoHandler: MeshNeighborInfoHandler,
    val configFlowManager: MeshConfigFlowManager,
    val mqttManager: MeshMqttManager,
    val actionHandler: MeshActionHandler,
) {
    fun start(scope: CoroutineScope) {
        dataHandler.start(scope)
        configHandler.start(scope)
        tracerouteHandler.start(scope)
        neighborInfoHandler.start(scope)
        configFlowManager.start(scope)
        actionHandler.start(scope)
    }
}
