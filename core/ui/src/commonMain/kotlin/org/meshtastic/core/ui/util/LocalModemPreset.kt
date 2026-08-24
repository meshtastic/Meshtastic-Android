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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.compositionLocalOf
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset

/**
 * The connected device's active LoRa modem preset, provided once at the app root (see MeshtasticAppShell) so signal
 * quality can be rated relative to the preset's demodulation floor without threading it through every node/message
 * composable. Null — the default before a device connects, and in previews/tests — falls back to the LongFast limit in
 * `ModemPreset?.snrLimit`.
 */
@Suppress("CompositionLocalAllowlist")
val LocalModemPreset = compositionLocalOf<ModemPreset?> { null }
