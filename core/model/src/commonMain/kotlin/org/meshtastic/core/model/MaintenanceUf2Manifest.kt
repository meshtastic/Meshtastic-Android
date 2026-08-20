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

/**
 * Pinned nRF52/RP2040 maintenance UF2 manifest — factory-erase and OTAFIX bootloader self-update images, keyed by
 * hardware. Envelope of `resource/maintenanceUf2` and the bundled `maintenance_uf2.json` seed asset; both are the same
 * bytes (verified by a digest test), unlike [BootloaderOtaQuirksResponse] this gates an irreversible write, so callers
 * must only trust a fetched copy whose raw-byte SHA-256 matches a compile-time pin — see `MaintenanceUf2Repository`.
 */
@Serializable
data class MaintenanceUf2Manifest(
    @SerialName("manifestVersion") val manifestVersion: Int = 1,
    @SerialName("otafixReleaseTag") val otafixReleaseTag: String = "",
    @SerialName("otafixBase") val otafixBase: String = "",
    @SerialName("erase") val erase: MaintenanceUf2EraseSet? = null,
    @SerialName("otafixByBoardId") val otafixByBoardId: Map<String, OtafixAssetEntry> = emptyMap(),
    @SerialName("otafixSupportedTargets") val otafixSupportedTargets: List<String> = emptyList(),
)

@Serializable
data class MaintenanceUf2EraseSet(
    @SerialName("s140_6_1_1") val sd611: EraseImageEntry,
    @SerialName("s140_7_3_0") val sd730: EraseImageEntry,
    @SerialName("rp2040") val rp2040: EraseImageEntry,
)

@Serializable
data class EraseImageEntry(
    @SerialName("fileName") val fileName: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("expectedFirstTargetAddress") val expectedFirstTargetAddress: Long? = null,
)

@Serializable
data class OtafixAssetEntry(@SerialName("board") val board: String, @SerialName("sha256") val sha256: String)
