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
package org.meshtastic.core.datastore.model

import kotlinx.serialization.Serializable

/**
 * A firmware update that was started but may not have finished, leaving the device stranded in nRF DFU/bootloader mode.
 *
 * Captured at DFU-trigger time (while still connected, when the hardware model and firmware channel are known) so the
 * device can later be re-flashed while disconnected — the bootloader advertises indefinitely at `MAC+1` but exposes no
 * mesh service, so there is otherwise no `address → hardware` link to reconstruct which firmware it needs.
 *
 * @property fullAddress The device address with its transport prefix (`radioPrefs.devAddr`), used to reconnect after
 *   recovery and to clear the record when the device returns on its own.
 * @property hwModel The numeric hardware model, for re-resolving the [org.meshtastic.core.model.DeviceHardware].
 * @property pioEnv The PlatformIO environment string, the second key for hardware resolution.
 * @property releaseType The firmware channel name (`STABLE`/`ALPHA`) the interrupted update was flashing.
 * @property deviceName The last-known device name, shown in the recovery prompt.
 */
@Serializable
data class PendingFirmwareRecovery(
    val fullAddress: String,
    val hwModel: Int,
    val pioEnv: String,
    val releaseType: String,
    val deviceName: String,
)
