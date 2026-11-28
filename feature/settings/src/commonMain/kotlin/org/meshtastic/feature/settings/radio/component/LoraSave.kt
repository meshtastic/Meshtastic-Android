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
package org.meshtastic.feature.settings.radio.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import org.meshtastic.core.model.util.keepingTransparentModeOf
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.transparent_mode_turn_on_unconfirmed
import org.meshtastic.core.resources.transparent_mode_unsupported
import org.meshtastic.core.ui.component.TransparentModeSetting
import org.meshtastic.core.ui.viewmodel.TransparentModeViewModel
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.proto.Config

/**
 * How a LoRa save reaches the radio.
 *
 * @property config the `set_config` the settings save sends.
 * @property turnsOn the save turns transparent mode on for the node this app is connected to.
 * @property turnOffAfter the save turns it off for that node. [config] then still carries the flag on, and the turn-off
 *   follows through the controller, which reads the node back and reports the result.
 */
internal data class LoraSave(val config: Config, val turnsOn: Boolean, val turnOffAfter: Boolean)

internal fun loraSaveFor(isLocal: Boolean, reported: Config.LoRaConfig, saved: Config.LoRaConfig): LoraSave {
    val turnOffAfter = isLocal && reported.transparent_mode && !saved.transparent_mode
    val lora = if (turnOffAfter) saved.keepingTransparentModeOf(reported) else saved
    return LoraSave(
        config = Config.Builder().also { wb -> wb.lora = lora }.build(),
        turnsOn = isLocal && saved.transparent_mode && !reported.transparent_mode,
        turnOffAfter = turnOffAfter,
    )
}

/** The transparent mode controller as a LoRa save reaches it. */
internal interface LoraSaveTransparentMode {
    fun turnOff()

    suspend fun confirmTurnedOnByUser(): Boolean?
}

internal fun TransparentModeViewModel.asLoraSaveTransparentMode() = object : LoraSaveTransparentMode {
    override fun turnOff() = this@asLoraSaveTransparentMode.turnOff()

    override suspend fun confirmTurnedOnByUser() = this@asLoraSaveTransparentMode.confirmTurnedOnByUser()
}

/**
 * Sends a LoRa save. A turn-off is reported by the transparent mode notice alone, so the save hands its result over and
 * shows no dialog of its own. A turn-on is reported by the save dialog once the node reads the flag back.
 */
internal fun saveLora(
    viewModel: RadioConfigViewModel,
    transparentMode: LoraSaveTransparentMode,
    isLocal: Boolean,
    reported: Config.LoRaConfig,
    saved: Config.LoRaConfig,
) {
    val save = loraSaveFor(isLocal, reported, saved)
    viewModel.setConfig(
        save.config,
        onSent = {
            when {
                save.turnOffAfter -> {
                    viewModel.clearPacketResponse()
                    transparentMode.turnOff()
                }

                save.turnsOn ->
                    viewModel.settleSave {
                        when (transparentMode.confirmTurnedOnByUser()) {
                            true -> null
                            false -> Res.string.transparent_mode_unsupported
                            null -> Res.string.transparent_mode_turn_on_unconfirmed
                        }
                    }
            }
        },
    )
}

/**
 * Transparent mode drops remote admin, so a remote node that had it turned on could not be brought back: only the local
 * node gets the control, and a remote node that has it on shows that read-only.
 */
@Composable
internal fun TransparentModeRow(isLocal: Boolean, connected: Boolean, formState: ConfigState<Config.LoRaConfig>) {
    if (isLocal || formState.value.transparent_mode) {
        HorizontalDivider()
        TransparentModeSetting(
            checked = formState.value.transparent_mode,
            enabled = connected && isLocal,
            onCheckedChange = {
                formState.value = formState.value.newBuilder().also { wb -> wb.transparent_mode = it }.build()
            },
        )
    }
}
