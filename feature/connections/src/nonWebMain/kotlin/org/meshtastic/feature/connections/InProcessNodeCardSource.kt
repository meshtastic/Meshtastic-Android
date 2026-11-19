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
package org.meshtastic.feature.connections

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.radio.NodeGpsState
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.feature.connections.model.GpsNotice
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.host.Bearer
import org.meshtastic.feature.connections.model.toNode
import org.meshtastic.feature.connections.model.toSensorsUi
import org.meshtastic.feature.connections.model.toUi

/** This device running as the node: the in-process node's status, mirrored by [repository] while it runs. */
@Single(binds = [NodeCardSource::class])
class InProcessNodeCardSource(private val repository: NodeStatusRepository) : NodeCardSource {

    override fun claims(address: String): Boolean = address.firstOrNull() == InterfaceId.NODE.id

    override fun status(address: String): Flow<NodeStatusUi?> = combine(
        repository.status,
        repository.sensors,
        repository.extraRadiosOff,
        repository.primaryRadio,
        repository.gps,
    ) { status, bus, extraRadiosOff, primary, gps ->
        status?.toUi(extraRadiosOff, primary)?.copy(sensors = bus?.toSensorsUi(), gps = gps?.toNotice())
    }

    override suspend fun setBearer(address: String, bearer: Bearer, on: Boolean) {
        repository.setHostBearer(bearer.toNode(), on)
    }

    override suspend fun setExtraRadio(address: String, name: String, on: Boolean): ExtraRadioSwitch = try {
        repository.setExtraRadio(name, on)
        ExtraRadioSwitch.KEPT
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        Logger.w(e) { "Extra radio $name switched, choice not kept" }
        ExtraRadioSwitch.NOT_KEPT
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "No extra radio $name to switch" }
        ExtraRadioSwitch.FAILED
    }

    override fun refreshRequirements() = repository.refreshRequirements()

    override fun requestUsbPermission() = repository.requestUsbPermission()
}

/** The card's notice for the node's GPS: none while it runs. */
internal fun NodeGpsState.toNotice(): GpsNotice? = when (this) {
    NodeGpsState.READY -> null
    NodeGpsState.NO_PERMISSION -> GpsNotice.NO_PERMISSION
    NodeGpsState.APPROXIMATE_ONLY -> GpsNotice.APPROXIMATE_ONLY
    NodeGpsState.LOCATION_OFF -> GpsNotice.LOCATION_OFF
    NodeGpsState.NO_GPS -> GpsNotice.NO_GPS
}
