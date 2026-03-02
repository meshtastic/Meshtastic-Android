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
package org.meshtastic.core.data.manager

import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import org.meshtastic.core.repository.MeshActionHandler
import org.meshtastic.core.repository.MeshConfigFlowManager
import org.meshtastic.core.repository.MeshConfigHandler
import org.meshtastic.core.repository.MeshDataHandler
import org.meshtastic.core.repository.MeshRouter
import org.meshtastic.core.repository.MqttManager
import org.meshtastic.core.repository.NeighborInfoHandler
import org.meshtastic.core.repository.TracerouteHandler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of [MeshRouter] that orchestrates specialized mesh packet handlers.
 */
@Suppress("LongParameterList")
@Singleton
class MeshRouterImpl @Inject constructor(
    private val _dataHandler: Lazy<MeshDataHandler>,
    private val _configHandler: Lazy<MeshConfigHandler>,
    private val _tracerouteHandler: Lazy<TracerouteHandler>,
    private val _neighborInfoHandler: Lazy<NeighborInfoHandler>,
    private val _configFlowManager: Lazy<MeshConfigFlowManager>,
    private val _mqttManager: Lazy<MqttManager>,
    private val _actionHandler: Lazy<MeshActionHandler>,
) : MeshRouter {
    override val dataHandler: MeshDataHandler get() = _dataHandler.get()
    override val configHandler: MeshConfigHandler get() = _configHandler.get()
    override val tracerouteHandler: TracerouteHandler get() = _tracerouteHandler.get()
    override val neighborInfoHandler: NeighborInfoHandler get() = _neighborInfoHandler.get()
    override val configFlowManager: MeshConfigFlowManager get() = _configFlowManager.get()
    override val mqttManager: MqttManager get() = _mqttManager.get()
    override val actionHandler: MeshActionHandler get() = _actionHandler.get()

    override fun start(scope: CoroutineScope) {
        dataHandler.start(scope)
        configHandler.start(scope)
        tracerouteHandler.start(scope)
        neighborInfoHandler.start(scope)
        configFlowManager.start(scope)
        actionHandler.start(scope)
    }
}
