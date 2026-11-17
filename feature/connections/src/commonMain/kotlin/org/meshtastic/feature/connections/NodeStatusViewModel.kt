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

import androidx.lifecycle.ViewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.network.radio.NodeStatusRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.node_extra_radio_not_kept
import org.meshtastic.core.resources.node_extra_radio_switch_failed
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.extraRadioNumber
import org.meshtastic.feature.connections.model.toSensorsUi
import org.meshtastic.feature.connections.model.toUi
import org.meshtastic.node.transport.Bearer

/** The node status card's state: null while this device is not running as the node. */
@KoinViewModel
class NodeStatusViewModel(
    private val repository: NodeStatusRepository,
    private val snackbarManager: SnackbarManager,
    private val resolveUiText: suspend (UiText) -> String = { it.resolve() },
) : ViewModel() {

    val status: StateFlow<NodeStatusUi?> =
        combine(repository.status, repository.sensors, repository.extraRadiosOff, repository.primaryRadio) {
                status,
                bus,
                extraRadiosOff,
                primary,
            ->
            status?.toUi(extraRadiosOff, primary)?.copy(sensors = bus?.toSensorsUi())
        }
            .stateInWhileSubscribed(initialValue = null)

    /**
     * Every bearer but MQTT is one entry in the node's stored transport section, which the node writes itself. MQTT
     * follows its module config and has no entry to write.
     */
    fun setBearer(bearer: Bearer, on: Boolean) {
        if (!bearer.switchable) return
        safeLaunch(tag = "setBearer") { repository.setHostBearer(bearer, on) }
    }

    /**
     * Switch one extra LoRa radio by itself. A switch the node made and could not keep is still made, so the user is
     * told only that a restart will forget it; a name the host does not have is told as a failure.
     */
    fun setExtraRadio(name: String, on: Boolean) {
        safeLaunch(tag = "setExtraRadio") {
            try {
                repository.setExtraRadio(name, on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                Logger.w(e) { "Extra radio $name switched, choice not kept" }
                val radio = extraRadioNumber(name)
                showMessage(
                    if (radio != null) {
                        UiText.Resource(Res.string.node_extra_radio_not_kept, radio)
                    } else {
                        UiText.Resource(Res.string.node_extra_radio_switch_failed)
                    },
                )
            } catch (e: IllegalArgumentException) {
                Logger.w(e) { "No extra radio $name to switch" }
                showMessage(UiText.Resource(Res.string.node_extra_radio_switch_failed))
            }
        }
    }

    private suspend fun showMessage(text: UiText) {
        snackbarManager.showSnackbar(message = resolveUiText(text))
    }

    fun refreshRequirements() = repository.refreshRequirements()

    fun requestUsbPermission() = repository.requestUsbPermission()
}
