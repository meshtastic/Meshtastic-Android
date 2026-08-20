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
import org.meshtastic.core.model.EraseImageEntry
import org.meshtastic.core.model.MaintenanceUf2Manifest
import org.meshtastic.core.model.SoftDeviceVariant
import org.meshtastic.core.network.HttpClientDefaults

/**
 * A pinned, content-verified UF2 image used by a maintenance flow (factory erase, bootloader upgrade).
 *
 * The URL/fileName/sha256 come from [MaintenanceUf2Manifest] (fetched from `resource/maintenanceUf2`, seeded from a
 * bundled asset — see `MaintenanceUf2Repository`) rather than being hardcoded here, so a new OTAFIX release or a
 * changed erase-image digest ships without an app release. Verifying [sha256] before any write is what makes that safe:
 * this class is the download-time gate, the manifest's own compile-time digest pin (see
 * `MaintenanceUf2RepositoryImpl.EXPECTED_MANIFEST_SHA256`) is the fetch-time one.
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
        // downloadFile interpolates fileName straight into a temp path; the manifest is content-addressed by its own
        // digest pin, but assert this anyway so a future edit (or a compromised manifest that somehow passed the pin)
        // can't introduce traversal.
        require(fileName.isNotBlank() && fileName.none { it == '/' || it == '\\' } && !fileName.contains("..")) {
            "Unsafe maintenance UF2 filename: $fileName"
        }
    }
}

/**
 * Factory-erase images are now vendored and served by `meshtastic/api` (`resource/maintenanceUf2/asset/<fileName>`)
 * rather than a commit-pinned URL into `meshtastic/web-flasher`'s `public/uf2/` — see `data/maintenanceUf2.json` in
 * that repo.
 */
private fun EraseImageEntry.toMaintenanceUf2(): MaintenanceUf2 = MaintenanceUf2(
    url = "${HttpClientDefaults.API_BASE_URL}resource/maintenanceUf2/asset/$fileName",
    fileName = fileName,
    sha256 = sha256,
    expectedFirstTargetAddress = expectedFirstTargetAddress,
)

/**
 * OTAFIX bootloader self-update images stay hosted on `Adafruit_nRF52_Bootloader_OTAFIX`'s own GitHub releases — only
 * [MaintenanceUf2Manifest.otafixBase]/[MaintenanceUf2Manifest.otafixReleaseTag] (and the per-board digest) now come
 * from the fetched manifest instead of being hardcoded.
 */
private fun MaintenanceUf2Manifest.otafixAsset(board: String, sha256: String): MaintenanceUf2 {
    val name = "update-${board}_bootloader-${otafixReleaseTag}_nosd.uf2"
    return MaintenanceUf2(url = "$otafixBase/$name", fileName = name, sha256 = sha256)
}

/**
 * The factory-erase image for [hardware] given [manifest], or `null` when none can be resolved safely.
 *
 * `null` when [manifest] carries no `erase` set at all (never fetched/seeded yet — fail closed, same as an unresolved
 * [DeviceHardware.softDeviceVariant]). nRF52840 additionally requires a resolved [DeviceHardware.softDeviceVariant]:
 * the two images are linked for different application start addresses, and the UF2 bootloader's write guard begins at
 * `MBR_SIZE`, so the wrong one erases a SoftDevice page. There is deliberately no default branch.
 */
internal fun eraseUf2For(manifest: MaintenanceUf2Manifest, hardware: DeviceHardware): MaintenanceUf2? {
    val erase = manifest.erase ?: return null
    return when {
        hardware.isRp2040Arc -> erase.rp2040.toMaintenanceUf2()

        hardware.isNrf52Arc ->
            when (hardware.softDeviceVariant) {
                SoftDeviceVariant.S140_6_1_1 -> erase.sd611.toMaintenanceUf2()
                SoftDeviceVariant.S140_7_3_0 -> erase.sd730.toMaintenanceUf2()
                null -> null
            }

        else -> null
    }
}

/**
 * True when [manifest] lists OTAFIX support for [platformioTarget]'s product. UX gate only — see
 * [MaintenanceUf2Manifest.otafixSupportedTargets]'s own doc in the source data.
 */
internal fun otafixSupportsTarget(manifest: MaintenanceUf2Manifest, platformioTarget: String): Boolean =
    platformioTarget in manifest.otafixSupportedTargets

/**
 * The OTAFIX image matching the [boardId] a device reported in its `INFO_UF2.TXT`, or `null` when unrecognized.
 *
 * `null` refuses the upgrade. That is the correct outcome even for a board OTAFIX supports: an unrecognized Board-ID
 * means the installed bootloader is not one we have a verified pairing for, and writing a bootloader built for other
 * hardware is unrecoverable without SWD.
 */
internal fun otafixUf2ForBoardId(manifest: MaintenanceUf2Manifest, boardId: String): MaintenanceUf2? =
    manifest.otafixByBoardId[boardId.trim()]?.let { manifest.otafixAsset(board = it.board, sha256 = it.sha256) }

/**
 * Extracts the `Board-ID:` value from the contents of a UF2 bootloader's `INFO_UF2.TXT`.
 *
 * Format is fixed by `ghostfat.c`: `UF2 Bootloader <ver>` / `Model: <name>` / `Board-ID: <id>` / `Date: <date>`,
 * CRLF-separated. Returns `null` when the line is absent, which means the volume is not an Adafruit-family UF2
 * bootloader drive — itself a reason to refuse a destructive write.
 */
internal fun parseUf2BoardId(infoUf2Text: String): String? = infoUf2Text
    .lineSequence()
    .firstOrNull { it.trimStart().startsWith(UF2_BOARD_ID_PREFIX, ignoreCase = true) }
    ?.substringAfter(':')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * Extracts the installed SoftDevice from the contents of a UF2 bootloader's `INFO_UF2.TXT`.
 *
 * `uf2_init()` appends this line at boot from `SD_ID_GET(MBR_SIZE)`/`SD_VERSION_GET(MBR_SIZE)` — i.e. read out of the
 * MBR's registers — formatted as `SoftDevice: S<id> <major>.<minor>.<patch>`. Present in upstream Adafruit and in
 * OTAFIX, and verified on a stock Seeed bootloader (`SoftDevice: S140 7.3.0`).
 *
 * This is the **authoritative** answer to the question the manifest only estimates: not what the firmware was built
 * against, but which SoftDevice is actually in flash. Returns `null` when the line is absent (very old bootloader),
 * when no SoftDevice is installed, or when the id/version is not one we ship an erase image for.
 */
@Suppress("ReturnCount") // guard clauses; an unparseable line must yield null rather than a guess
internal fun parseUf2SoftDevice(infoUf2Text: String): SoftDeviceVariant? {
    val value =
        infoUf2Text
            .lineSequence()
            .firstOrNull { it.trimStart().startsWith(UF2_SOFTDEVICE_PREFIX, ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim() ?: return null

    val parts = value.split(' ').filter { it.isNotBlank() }
    if (parts.size < 2 || !parts[0].equals(SUPPORTED_SOFTDEVICE_ID, ignoreCase = true)) return null
    return SoftDeviceVariant.fromWire(parts[1])
}

/**
 * Which erase image a given variant needs, from [manifest]. `null` when [manifest] carries no `erase` set (never
 * fetched/seeded — fail closed); otherwise total over the enum, so a new variant cannot silently reuse an old image.
 */
internal fun eraseUf2ForVariant(manifest: MaintenanceUf2Manifest, variant: SoftDeviceVariant): MaintenanceUf2? {
    val erase = manifest.erase ?: return null
    return when (variant) {
        SoftDeviceVariant.S140_6_1_1 -> erase.sd611
        SoftDeviceVariant.S140_7_3_0 -> erase.sd730
    }.toMaintenanceUf2()
}

/** Outcome of reconciling the SoftDevice the drive reports against the manifest's pre-flight hint. */
internal sealed interface EraseImageResolution {
    /** Safe to write [asset]; [variant] is the SoftDevice it is linked for. */
    data class Resolved(val asset: MaintenanceUf2, val variant: SoftDeviceVariant) : EraseImageResolution

    /**
     * The drive and the manifest's pre-flight hint disagree. Always a refusal: one of the two is wrong and we cannot
     * tell which, and guessing writes an erase image into a SoftDevice. Also the signal that a map row needs
     * correcting.
     */
    data class Conflict(val reported: SoftDeviceVariant, val mapped: SoftDeviceVariant) : EraseImageResolution

    /** Neither source produced a variant, or the manifest has no erase images to offer at all. */
    data object Unresolved : EraseImageResolution
}

/**
 * Picks the nRF erase image, preferring what the device reports over what [manifest]'s pre-flight hint predicted.
 *
 * The hint ([DeviceHardware.softDeviceVariant], itself derived from the bootloader-quirks catalog) decides whether the
 * action is offered before any drive is mounted. Once the drive is readable its own report wins, because it comes from
 * the MBR rather than from a hand-authored table. A disagreement refuses rather than picking a side.
 */
internal fun resolveNrfEraseImage(
    manifest: MaintenanceUf2Manifest,
    mapped: SoftDeviceVariant?,
    reportedFromDrive: SoftDeviceVariant?,
): EraseImageResolution = when {
    reportedFromDrive != null && mapped != null && reportedFromDrive != mapped ->
        EraseImageResolution.Conflict(reported = reportedFromDrive, mapped = mapped)

    reportedFromDrive != null ->
        eraseUf2ForVariant(manifest, reportedFromDrive)?.let {
            EraseImageResolution.Resolved(it, reportedFromDrive)
        } ?: EraseImageResolution.Unresolved

    // No SoftDevice line: a bootloader older than the uf2_init that emits it. Fall back to the pre-flight hint.
    mapped != null ->
        eraseUf2ForVariant(manifest, mapped)?.let { EraseImageResolution.Resolved(it, mapped) }
            ?: EraseImageResolution.Unresolved

    else -> EraseImageResolution.Unresolved
}

/** The file every Adafruit-family UF2 bootloader exposes on its mass-storage volume. */
internal const val INFO_UF2_FILE_NAME = "INFO_UF2.TXT"

private const val UF2_BOARD_ID_PREFIX = "Board-ID:"

private const val UF2_SOFTDEVICE_PREFIX = "SoftDevice:"

/** All Meshtastic nRF52840 boards run the S140 SoftDevice; anything else is out of scope and refuses. */
private const val SUPPORTED_SOFTDEVICE_ID = "S140"

/** UF2 block size, per the UF2 specification. */
internal const val UF2_BLOCK_BYTES = 512

/** Byte offset of `targetAddr` within a UF2 block header. */
internal const val UF2_TARGET_ADDR_OFFSET = 12

private const val UF2_MAGIC_START0 = 0x0A324655

/** Bytes in a little-endian 32-bit field, and the mask/shift used to reassemble one. */
private const val UINT32_BYTES = 4

private const val BITS_PER_BYTE = 8

private const val BYTE_MASK = 0xFFL

/**
 * Reads the target flash address of the first UF2 block in [bytes], or `null` when the payload is not a UF2 image.
 *
 * Used to cross-check a pinned erase image against the resolved SoftDevice variant before it is written.
 */
@Suppress("ReturnCount") // guard clauses over a binary header
internal fun uf2FirstTargetAddress(bytes: ByteArray): Long? {
    if (bytes.size < UF2_BLOCK_BYTES) return null
    if (readLittleEndianUInt32(bytes, 0) != UF2_MAGIC_START0.toLong()) return null
    return readLittleEndianUInt32(bytes, UF2_TARGET_ADDR_OFFSET)
}

private fun readLittleEndianUInt32(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in UINT32_BYTES - 1 downTo 0) {
        value = (value shl BITS_PER_BYTE) or (bytes[offset + i].toLong() and BYTE_MASK)
    }
    return value
}
