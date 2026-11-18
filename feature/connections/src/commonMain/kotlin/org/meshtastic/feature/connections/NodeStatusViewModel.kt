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
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.node_extra_radio_not_kept
import org.meshtastic.core.resources.node_extra_radio_switch_failed
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.model.extraRadioNumber
import org.meshtastic.feature.connections.model.host.Bearer

/** The node status card's state: null while the current device is no node any [NodeCardSource] reads. */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class NodeStatusViewModel(
    radioInterfaceService: RadioInterfaceService,
    private val sources: List<NodeCardSource>,
    private val snackbarManager: SnackbarManager,
    private val resolveUiText: suspend (UiText) -> String = { it.resolve() },
) : ViewModel() {

    private data class Claimed(val source: NodeCardSource, val address: String)

    private val claimed: StateFlow<Claimed?> =
        radioInterfaceService.currentDeviceAddressFlow
            .map { address -> address?.let(::claim) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val status: StateFlow<NodeStatusUi?> =
        claimed
            .flatMapLatest { it?.source?.status(it.address) ?: flowOf(null) }
            .stateInWhileSubscribed(initialValue = null)

    /**
     * Every bearer but MQTT is one entry in the node's stored transport section, which the node writes itself. MQTT
     * follows its module config and has no entry to write.
     */
    fun setBearer(bearer: Bearer, on: Boolean) {
        if (!bearer.switchable) return
        val current = claimed.value ?: return
        safeLaunch(tag = "setBearer") { current.source.setBearer(current.address, bearer, on) }
    }

    /**
     * Switch one extra LoRa radio by itself. A switch the node made and could not keep is still made, so the user is
     * told only that a restart will forget it; a switch not made is told as a failure.
     */
    fun setExtraRadio(name: String, on: Boolean) {
        val current = claimed.value ?: return
        safeLaunch(tag = "setExtraRadio") {
            val radio = extraRadioNumber(name)
            when (current.source.setExtraRadio(current.address, name, on)) {
                ExtraRadioSwitch.KEPT -> Unit

                ExtraRadioSwitch.NOT_KEPT ->
                    showMessage(
                        if (radio != null) {
                            UiText.Resource(Res.string.node_extra_radio_not_kept, radio)
                        } else {
                            UiText.Resource(Res.string.node_extra_radio_switch_failed)
                        },
                    )

                ExtraRadioSwitch.FAILED -> showMessage(UiText.Resource(Res.string.node_extra_radio_switch_failed))
            }
        }
    }

    private fun claim(address: String): Claimed? =
        sources.firstOrNull { it.claims(address) }?.let { source -> Claimed(source, address) }

    private suspend fun showMessage(text: UiText) {
        snackbarManager.showSnackbar(message = resolveUiText(text))
    }

    fun refreshRequirements() {
        claimed.value?.source?.refreshRequirements()
    }

    fun requestUsbPermission() {
        claimed.value?.source?.requestUsbPermission()
    }
}
