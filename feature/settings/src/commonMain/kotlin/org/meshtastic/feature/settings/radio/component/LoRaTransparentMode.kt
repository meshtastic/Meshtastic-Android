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
import org.meshtastic.core.ui.component.TransparentModeSetting
import org.meshtastic.proto.Config

/** Whether a LoRa save turns transparent mode on for the node this app is connected to. */
internal fun turnsOwnTransparentModeOn(isLocal: Boolean, reported: Config.LoRaConfig, saved: Config.LoRaConfig) =
    isLocal && saved.transparent_mode && !reported.transparent_mode

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
