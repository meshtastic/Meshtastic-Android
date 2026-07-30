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
package org.meshtastic.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Envelope of the bundled `device_bootloader_ota_quirks.json` asset. */
@Serializable
data class BootloaderOtaQuirksResponse(
    val devices: List<BootloaderOtaQuirk> = emptyList(),
    /**
     * Per-model SoftDevice variants. Kept as a separate array from [devices] on purpose: [BootloaderOtaQuirk] is an
     * advisory warning that may safely fail open, while this map gates a destructive flash and must fail closed. One
     * record carrying both postures would invite a future edit that relaxes the wrong one.
     */
    @SerialName("softDeviceVariants") val softDeviceVariants: List<SoftDeviceVariantEntry> = emptyList(),
)

/**
 * The Nordic SoftDevice an nRF52840 board's firmware is linked against. Static hardware fact, derived from the board's
 * `build.arduino.ldscript` in `meshtastic/firmware` (`nrf52840_s140_v6.ld` / `_v7.ld`).
 *
 * Load-bearing for factory erase: the erase images are linked for a specific application start address (0x26000 for
 * 6.1.1, 0x27000 for 7.3.0) and the UF2 bootloader's write guard begins at `MBR_SIZE` (0x1000), so the SoftDevice
 * region is writable. Flashing the 6.1.1 image to a 7.3.0 device erases the SoftDevice's last page, which needs SWD or
 * serial DFU to recover. Never infer this value — an unresolved variant must refuse the operation.
 */
enum class SoftDeviceVariant {
    S140_6_1_1,
    S140_7_3_0,
    ;

    companion object {
        /**
         * Maps the wire value to a variant, returning `null` for anything unrecognized.
         *
         * The asset carries a `String?` rather than this enum because the shared `Json` sets `coerceInputValues =
         * true`, which would silently coerce a typo'd enum value to a default. An unrecognized string lands on `null`
         * instead, and `null` refuses.
         */
        fun fromWire(value: String?): SoftDeviceVariant? = when (value) {
            "6.1.1" -> S140_6_1_1
            "7.3.0" -> S140_7_3_0
            else -> null
        }
    }
}

/**
 * One hardware model's SoftDevice mapping.
 *
 * @property platformioTargets Every catalog target for this [hwModel]. Resolution requires the device-reported target
 *   to appear here, because `hwModel` alone is not unique — hwModel 94 (`HELTEC_MESH_POCKET`) has two nRF52840 targets
 *   — and borrowing a sibling's variant is exactly the mistake that corrupts a SoftDevice.
 * @property softDevice Wire value (`"6.1.1"` / `"7.3.0"`), or absent when the variant is unknown. An entry present with
 *   no `softDevice` records a deliberate, greppable refusal rather than an oversight.
 */
@Serializable
data class SoftDeviceVariantEntry(
    @SerialName("hwModel") val hwModel: Int,
    @SerialName("hwModelSlug") val hwModelSlug: String? = null,
    @SerialName("platformioTargets") val platformioTargets: List<String> = emptyList(),
    @SerialName("softDevice") val softDevice: String? = null,
)

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
