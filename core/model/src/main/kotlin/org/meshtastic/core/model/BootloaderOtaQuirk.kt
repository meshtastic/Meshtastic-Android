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

package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BootloaderOtaQuirk(
    /** Hardware model id, matches DeviceHardware.hwModel. */
    @SerialName("hwModel") val hwModel: Int,
    /** Optional slug for readability / tooling. */
    @SerialName("hwModelSlug") val hwModelSlug: String? = null,
    /**
     * Indicates that devices usually ship with a bootloader that does not support OTA out of the box and require a
     * one-time bootloader upgrade (typically via USB) before DFU updates from the app work.
     */
    @SerialName("requiresBootloaderUpgradeForOta") val requiresBootloaderUpgradeForOta: Boolean = false,
    /** Optional URL pointing to documentation on how to update the bootloader. */
    @SerialName("infoUrl") val infoUrl: String? = null,
)
