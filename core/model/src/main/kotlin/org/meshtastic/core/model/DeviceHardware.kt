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

import kotlinx.serialization.Serializable

@Serializable
data class DeviceHardware(
    val activelySupported: Boolean = false,
    val architecture: String = "",
    val displayName: String = "",
    val hasInkHud: Boolean? = null,
    val hasMui: Boolean? = null,
    val hwModel: Int = 0,
    val hwModelSlug: String = "",
    val images: List<String>? = null,
    val partitionScheme: String? = null,
    val platformioTarget: String = "",
    val requiresDfu: Boolean? = null,
    /**
     * Indicates that the device typically ships with a bootloader that does not support OTA DFU, and that a one-time
     * bootloader upgrade (usually over USB) is recommended before attempting firmware updates from the app.
     */
    val requiresBootloaderUpgradeForOta: Boolean? = null,
    /** Optional URL pointing to documentation for upgrading the bootloader. */
    val bootloaderInfoUrl: String? = null,
    val supportLevel: Int? = null,
    val tags: List<String>? = null,
)
