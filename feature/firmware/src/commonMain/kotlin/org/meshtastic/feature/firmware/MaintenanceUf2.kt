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
 * this class is the download-time gate against a corrupted transfer, independent of how the manifest naming this image
 * was itself fetched.
 *
 * @property expectedFirstTargetAddress For the SoftDevice-specific nRF erase sketches, the flash address the UF2's
 *   first block writes to. Checked against the resolved [SoftDeviceVariant] before the image is offered, because a
 *   swapped URL/digest row is the one authoring mistake a digest alone cannot catch — and the mistake that corrupts a
 *   SoftDevice. Null when the image's address carries no such invariant (RP2040, bootloader self-updates, and the
 *   bootloader-driven erase image, whose `targetAddr` is 0 by design).
 * @property expectedFamilyId For the bootloader-driven erase image, the UF2 family ID its block must declare. That
 *   image is a single block the bootloader consumes itself, so its integrity contract is the family ID rather than an
 *   address. Null for every other image.
 * @property requiresCdcUnblock True only for the SoftDevice-specific nRF erase sketches, which block in `while
 *   (!Serial)` before formatting and need a host to assert DTR. False for images the bootloader consumes itself (RP2040
 *   `pico_erase`, OTAFIX self-updates, and the bootloader-driven erase image): opening a CDC port after one of those
 *   latches onto the bootloader's own port and stalls the flow for nothing.
 */
internal data class MaintenanceUf2(
    val url: String,
    val fileName: String,
    val sha256: String,
    val expectedFirstTargetAddress: Long? = null,
    val expectedFamilyId: Long? = null,
    val requiresCdcUnblock: Boolean = false,
) {
    init {
        // downloadFile interpolates fileName straight into a temp path. The mapping boundary already refuses unsafe
        // names (see SAFE_UF2_FILE_NAME) so this never fires in practice; it stays as defence-in-depth for values
        // constructed directly, and is deliberately the *same* rule so there is one definition of "safe".
        require(SAFE_UF2_FILE_NAME.matches(fileName)) { "Unsafe maintenance UF2 filename: $fileName" }
    }
}

/**
 * The only shape a manifest-supplied UF2 file name may take: a plain `<name>.uf2` of unreserved characters.
 *
 * Every name reaching [MaintenanceUf2] is now a string from `resource/maintenanceUf2` rather than a compile-time
 * constant, so it is validated at the mapping boundary and a rejected row resolves to `null` — refusing that one image
 * instead of throwing out of a resolver whose callers all document a nullable result. An allowlist rather than a
 * traversal blacklist: excluding separators outright makes `..` inert, and nothing legitimate falls outside it.
 */
private val SAFE_UF2_FILE_NAME = Regex("""[A-Za-z0-9._-]+\.uf2""")

/**
 * Factory-erase images are now vendored and served by `meshtastic/api` (`resource/maintenanceUf2/asset/<fileName>`)
 * rather than a commit-pinned URL into `meshtastic/web-flasher`'s `public/uf2/` — see `data/maintenanceUf2.json` in
 * that repo.
 *
 * `null` when the row names an unsafe file: a refusal of that image, never a crash. One malformed row must not take
 * down the whole firmware screen.
 */
private fun EraseImageEntry.toMaintenanceUf2OrNull(requiresCdcUnblock: Boolean = false): MaintenanceUf2? {
    if (!SAFE_UF2_FILE_NAME.matches(fileName)) return null
    return MaintenanceUf2(
        url = "${HttpClientDefaults.API_BASE_URL}resource/maintenanceUf2/asset/$fileName",
        fileName = fileName,
        sha256 = sha256,
        expectedFirstTargetAddress = expectedFirstTargetAddress,
        expectedFamilyId = expectedFamilyId,
        requiresCdcUnblock = requiresCdcUnblock,
    )
}

/**
 * The bootloader-driven erase image from [MaintenanceUf2EraseSet.nrf52Bootloader], when the volume's advertised
 * `Factory-Erase:` family ([volumeFamily]) equals the entry's `expectedFamilyId`; `null` otherwise.
 *
 * Both sides must be present and equal: a bootloader that advertises no family silently ignores the file (every
 * bootloader shipped before OTAFIX PR #41), and an entry with no expected family cannot be verified against the bytes,
 * so neither may resolve here — the caller falls through to the SoftDevice-specific sketch path unchanged.
 */
internal fun bootloaderEraseUf2For(manifest: MaintenanceUf2Manifest, volumeFamily: Long?): MaintenanceUf2? =
    manifest.erase
        ?.nrf52Bootloader
        ?.takeIf { it.expectedFamilyId != null && it.expectedFamilyId == volumeFamily }
        // targetAddr is 0 in this image by design, so the first-target-address invariant does not apply — the family
        // ID is the contract the retriever checks instead. Force it null so an authored address can never reject it.
        ?.copy(expectedFirstTargetAddress = null)
        ?.toMaintenanceUf2OrNull()

/**
 * OTAFIX bootloader self-update images stay hosted on `Adafruit_nRF52_Bootloader_OTAFIX`'s own GitHub releases — only
 * [MaintenanceUf2Manifest.otafixBase]/[MaintenanceUf2Manifest.otafixReleaseTag] (and the per-board digest) now come
 * from the fetched manifest instead of being hardcoded.
 *
 * `null` on the same terms as [toMaintenanceUf2OrNull]. The name is composed from two manifest-supplied strings
 * ([otafixBoardSlug] and [MaintenanceUf2Manifest.otafixReleaseTag]), so it is validated after composition.
 */
private fun MaintenanceUf2Manifest.otafixAssetOrNull(otafixBoardSlug: String, sha256: String): MaintenanceUf2? {
    val name = "update-${otafixBoardSlug}_bootloader-${otafixReleaseTag}_nosd.uf2"
    if (!SAFE_UF2_FILE_NAME.matches(name)) return null
    return MaintenanceUf2(url = "$otafixBase/$name", fileName = name, sha256 = sha256)
}

/** [SoftDeviceVariant.fromWire]'s own input strings — the key space [MaintenanceUf2EraseSet.nrf52] is indexed by. */
private val SoftDeviceVariant.wireValue: String
    get() =
        when (this) {
            SoftDeviceVariant.S140_6_1_1 -> "6.1.1"
            SoftDeviceVariant.S140_7_3_0 -> "7.3.0"
        }

/**
 * The factory-erase image for [hardware] given [manifest], or `null` when none can be resolved safely.
 *
 * `null` when [manifest] carries no `erase` set at all (never fetched/seeded yet — fail closed, same as an unresolved
 * [DeviceHardware.softDeviceVariant]), when the matching row names an unsafe file, or — for nRF52840 — without a
 * resolved [DeviceHardware.softDeviceVariant]: the two images are linked for different application start addresses, and
 * the UF2 bootloader's write guard begins at `MBR_SIZE`, so the wrong one erases a SoftDevice page. There is
 * deliberately no default branch.
 */
internal fun eraseUf2For(manifest: MaintenanceUf2Manifest, hardware: DeviceHardware): MaintenanceUf2? {
    val erase = manifest.erase ?: return null
    return when {
        hardware.isRp2040Arc -> erase.rp2040.toMaintenanceUf2OrNull()

        hardware.isNrf52Arc ->
            hardware.softDeviceVariant?.let {
                erase.nrf52[it.wireValue]?.toMaintenanceUf2OrNull(requiresCdcUnblock = true)
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
    manifest.otafixByBoardId[boardId.trim()]?.let {
        manifest.otafixAssetOrNull(otafixBoardSlug = it.otafixBoardSlug, sha256 = it.sha256)
    }

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
 * Extracts the UF2 family ID a bootloader advertises for its own factory erase, from its `INFO_UF2.TXT`.
 *
 * OTAFIX (PR #41 onwards) appends `Factory-Erase: UF2 family 0x4D455348` when it will consume a single-block UF2 of
 * that family as an erase command. Parsed like its siblings — by line prefix, case-insensitively, tolerating leading
 * whitespace — taking the last `0x`-prefixed token. Returns `null` when the line is absent (every earlier bootloader)
 * or carries no parseable hex token; a *different* family parses fine and is refused by [bootloaderEraseUf2For].
 */
@Suppress("ReturnCount") // guard clauses; an unparseable line must yield null rather than a guess
internal fun parseUf2FactoryEraseFamily(infoUf2Text: String): Long? {
    val value =
        infoUf2Text
            .lineSequence()
            .firstOrNull { it.trimStart().startsWith(UF2_FACTORY_ERASE_PREFIX, ignoreCase = true) }
            ?.substringAfter(':') ?: return null
    val token =
        value.split(' ').lastOrNull { it.startsWith(HEX_PREFIX, ignoreCase = true) }?.drop(HEX_PREFIX.length)
            ?: return null
    return token.toLongOrNull(HEX_RADIX)
}

/**
 * Which erase image [variant] needs, from [manifest]. `null` when [manifest] carries no `erase` set at all (never
 * fetched/seeded — fail closed), when this specific variant's row is missing from `erase.nrf52` (a malformed or partial
 * manifest), or when that row names an unsafe file — never a guess at a substitute image.
 */
internal fun eraseUf2ForVariant(manifest: MaintenanceUf2Manifest, variant: SoftDeviceVariant): MaintenanceUf2? {
    val erase = manifest.erase ?: return null
    return erase.nrf52[variant.wireValue]?.toMaintenanceUf2OrNull(requiresCdcUnblock = true)
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

private const val UF2_FACTORY_ERASE_PREFIX = "Factory-Erase:"

private const val HEX_PREFIX = "0x"

private const val HEX_RADIX = 16

/** All Meshtastic nRF52840 boards run the S140 SoftDevice; anything else is out of scope and refuses. */
private const val SUPPORTED_SOFTDEVICE_ID = "S140"
