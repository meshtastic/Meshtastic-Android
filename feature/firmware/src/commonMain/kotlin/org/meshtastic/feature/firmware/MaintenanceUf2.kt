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
package org.meshtastic.feature.firmware

import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.SoftDeviceVariant

/**
 * A pinned, content-verified UF2 image used by a maintenance flow (factory erase, bootloader upgrade).
 *
 * Unlike release firmware — which `FirmwareRetriever` resolves against a versioned release folder — these images have no
 * versioned upstream. `meshtastic/nrf52_factory_erase` has cut no GitHub releases, so the flasher serves them from a
 * mutable `public/uf2/` path. Pinning the URL to a commit and verifying [sha256] before any write is what turns that
 * mutable path back into an immutable one.
 *
 * @property expectedFirstTargetAddress For nRF erase images, the flash address the UF2's first block writes to. Checked
 *   against the resolved [SoftDeviceVariant] before the image is offered, because a swapped URL/digest row is the one
 *   authoring mistake a digest alone cannot catch — and the mistake that corrupts a SoftDevice. Null when the image's
 *   address carries no such invariant (RP2040, bootloader self-updates).
 */
internal data class MaintenanceUf2(
    val url: String,
    val fileName: String,
    val sha256: String,
    val expectedFirstTargetAddress: Long? = null,
) {
    init {
        // downloadFile interpolates fileName straight into a temp path; these are compile-time constants, but assert it
        // so a future edit can't introduce traversal.
        require(fileName.isNotBlank() && fileName.none { it == '/' || it == '\\' } && !fileName.contains("..")) {
            "Unsafe maintenance UF2 filename: $fileName"
        }
    }
}

/**
 * Commit-pinned base for the factory-erase images checked into `meshtastic/web-flasher` at `public/uf2/`.
 *
 * The blobs have not changed since this commit (2024-09-03), so the pin is cheap to hold. Built from
 * `meshtastic/nrf52_factory_erase` (GPL-3.0).
 */
private const val ERASE_UF2_BASE =
    "https://raw.githubusercontent.com/meshtastic/web-flasher/0e353b5d0756c9a1b76f53be78e948fafc1ebd8a/public/uf2"

/** Release-pinned base for the OTAFIX bootloader self-update images (`oltaco/…_OTAFIX`, MIT). */
private const val OTAFIX_RELEASE_TAG = "0.9.2-OTAFIX2.2-BP1.3"

private const val OTAFIX_BASE =
    "https://github.com/oltaco/Adafruit_nRF52_Bootloader_OTAFIX/releases/download/$OTAFIX_RELEASE_TAG"

/** S140 6.1.1 application start — the address `nrf_erase2.uf2` is linked for. */
internal const val APP_START_S140_6_1_1 = 0x26000L

/** S140 7.3.0 application start — the address `nrf_erase_sd7_3.uf2` is linked for. */
internal const val APP_START_S140_7_3_0 = 0x27000L

private val ERASE_S140_6_1_1 = MaintenanceUf2(
    url = "$ERASE_UF2_BASE/nrf_erase2.uf2",
    fileName = "nrf_erase2.uf2",
    sha256 = "4b778a3def19854415db64cb51bfd29c15b11cc46006353dd518f62d09efe3fe",
    expectedFirstTargetAddress = APP_START_S140_6_1_1,
)

private val ERASE_S140_7_3_0 = MaintenanceUf2(
    url = "$ERASE_UF2_BASE/nrf_erase_sd7_3.uf2",
    fileName = "nrf_erase_sd7_3.uf2",
    sha256 = "13941bedce009e61255c37b1524d11ca604e88c38e7588bb8b391e2998da468f",
    expectedFirstTargetAddress = APP_START_S140_7_3_0,
)

private val PICO_ERASE = MaintenanceUf2(
    url = "$ERASE_UF2_BASE/pico_erase.uf2",
    fileName = "pico_erase.uf2",
    sha256 = "08aa7d561e8b8bf2f9b061b3506fb4d8f135e832efe0f3ae978241db2da0c853",
)

/**
 * OTAFIX bootloader self-update images, keyed by Meshtastic `platformioTarget`.
 *
 * Deliberately sparse. The OTAFIX assets are named after *their* PlatformIO board names, which do not correspond to
 * Meshtastic's by any mechanical rule: `heltec_t114` vs `heltec-mesh-node-t114`, `thinknode_m1` vs `ThinkNode-M1`,
 * `t1000_e` vs `tracker-t1000-e`. Worse, six distinct Meshtastic products (WISMESH Hub/Tap/Tag, Nomadstar Meteor Pro,
 * RAK3401, RAK4631) all build against the single `wiscore_rak4631` board, so a board-level match would offer one
 * product's bootloader to five others.
 *
 * A bootloader built for different hardware is as unrecoverable as a wrong-SoftDevice erase, so entries are added only
 * where the correspondence is unambiguous *and* the pairing has been validated on real hardware. Everything else
 * resolves to null and the upgrade action is simply not offered — see `usbMaintenanceGate`.
 */
private val OTAFIX_BY_TARGET: Map<String, MaintenanceUf2> = mapOf(
    "rak4631" to
        MaintenanceUf2(
            url = "$OTAFIX_BASE/update-wiscore_rak4631_board_bootloader-${OTAFIX_RELEASE_TAG}_nosd.uf2",
            fileName = "update-wiscore_rak4631_board_bootloader-${OTAFIX_RELEASE_TAG}_nosd.uf2",
            sha256 = "3509c8b01296bc6473acf5a9422f9aec857a5bbb47e80000f9b98e15b046e46a",
        ),
    "tracker-t1000-e" to
        MaintenanceUf2(
            url = "$OTAFIX_BASE/update-t1000_e_bootloader-${OTAFIX_RELEASE_TAG}_nosd.uf2",
            fileName = "update-t1000_e_bootloader-${OTAFIX_RELEASE_TAG}_nosd.uf2",
            sha256 = "c1dd30cce0f250eb7ad21e8c065cef6692b53e7efcba0578371bedcfd2493cc9",
        ),
)

/**
 * The factory-erase image for [hardware], or `null` when none can be resolved safely.
 *
 * nRF52840 requires a resolved [DeviceHardware.softDeviceVariant]: the two images are linked for different application
 * start addresses, and the UF2 bootloader's write guard begins at `MBR_SIZE`, so the wrong one erases a SoftDevice page.
 * There is deliberately no default branch.
 */
internal fun eraseUf2For(hardware: DeviceHardware): MaintenanceUf2? = when {
    hardware.isRp2040Arc -> PICO_ERASE

    hardware.isNrf52Arc ->
        when (hardware.softDeviceVariant) {
            SoftDeviceVariant.S140_6_1_1 -> ERASE_S140_6_1_1
            SoftDeviceVariant.S140_7_3_0 -> ERASE_S140_7_3_0
            null -> null
        }

    else -> null
}

/** The OTAFIX bootloader self-update image for [platformioTarget], or `null` when this board is not mapped. */
internal fun otafixUf2For(platformioTarget: String): MaintenanceUf2? = OTAFIX_BY_TARGET[platformioTarget]

/** UF2 block size, per the UF2 specification. */
private const val UF2_BLOCK_SIZE = 512

private const val UF2_MAGIC_START0 = 0x0A324655
private const val UF2_TARGET_ADDR_OFFSET = 12

/**
 * Reads the target flash address of the first UF2 block in [bytes], or `null` when the payload is not a UF2 image.
 *
 * Used to cross-check a pinned erase image against the resolved SoftDevice variant before it is written.
 */
internal fun uf2FirstTargetAddress(bytes: ByteArray): Long? {
    if (bytes.size < UF2_BLOCK_SIZE) return null
    if (readLittleEndianUInt32(bytes, 0) != UF2_MAGIC_START0.toLong()) return null
    return readLittleEndianUInt32(bytes, UF2_TARGET_ADDR_OFFSET)
}

private fun readLittleEndianUInt32(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 3 downTo 0) {
        value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
    }
    return value
}
