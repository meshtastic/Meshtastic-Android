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
 * hardware. Envelope of `resource/maintenanceUf2` and the bundled `maintenance_uf2.json` seed asset. Each image's own
 * `sha256` is checked against its downloaded bytes before any write — see `MaintenanceUf2.kt` — independently of how
 * this manifest itself is fetched.
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

/**
 * Nested by architecture, then (for nRF52) by SoftDevice wire value — RP2040 has no SoftDevice concept at all, so it
 * correctly has no sub-key, unlike the old flat {s140_6_1_1, s140_7_3_0, rp2040} shape that mixed a SoftDevice-variant
 * axis with an architecture axis in one object.
 */
@Serializable
data class MaintenanceUf2EraseSet(
    /** Keyed by [SoftDeviceVariant.fromWire]'s own input strings, e.g. "6.1.1" / "7.3.0". */
    @SerialName("nrf52") val nrf52: Map<String, EraseImageEntry> = emptyMap(),
    /**
     * The board-agnostic bootloader-driven erase image (OTAFIX `tools/meshtastic_factory_erase.uf2`): a single UF2
     * block the bootloader itself consumes when its `INFO_UF2.TXT` advertises `Factory-Erase: UF2 family <id>`. Absent
     * from manifests that predate it, and ignored by bootloaders that do not advertise it — so it is only ever
     * preferred over [nrf52], never a replacement for it.
     */
    @SerialName("nrf52Bootloader") val nrf52Bootloader: EraseImageEntry? = null,
    @SerialName("rp2040") val rp2040: EraseImageEntry,
)

/**
 * @property expectedFirstTargetAddress The flash address the image's first block writes to, for images whose address
 *   carries a safety invariant (the SoftDevice-specific nRF erase sketches). Null otherwise.
 * @property expectedFamilyId The UF2 family ID the image's block must declare (flag `0x2000` set, u32 at offset 28) —
 *   the bootloader-driven erase image's contract, checked against the bytes instead of an address, because that image's
 *   `targetAddr` is 0 by design. Null for every other image.
 */
@Serializable
data class EraseImageEntry(
    @SerialName("fileName") val fileName: String,
    @SerialName("sha256") val sha256: String,
    @SerialName("expectedFirstTargetAddress") val expectedFirstTargetAddress: Long? = null,
    @SerialName("expectedFamilyId") val expectedFamilyId: Long? = null,
)

/**
 * @property otafixBoardSlug OTAFIX's own release-asset board slug (e.g. "wiscore_rak4631_board") — deliberately NOT
 *   named the same as [DeviceHardware.platformioTarget] (e.g. "rak4631"): the two vocabularies differ per board, and a
 *   shared name here would invite exactly the confusion this class's callers already have to spell out in prose.
 */
@Serializable
data class OtafixAssetEntry(
    @SerialName("otafixBoardSlug") val otafixBoardSlug: String,
    @SerialName("sha256") val sha256: String,
)
