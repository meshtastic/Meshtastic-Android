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
 * Unlike release firmware — which `FirmwareRetriever` resolves against a versioned release folder — these images have
 * no versioned upstream. `meshtastic/nrf52_factory_erase` has cut no GitHub releases, so the flasher serves them from a
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

/** Release-pinned base for the OTAFIX bootloader self-update images (`meshtastic/…_OTAFIX`, MIT). */
private const val OTAFIX_RELEASE_TAG = "0.9.2-OTAFIX2.2-BP1.4"

private const val OTAFIX_BASE =
    "https://github.com/meshtastic/Adafruit_nRF52_Bootloader_OTAFIX/releases/download/$OTAFIX_RELEASE_TAG"

/** S140 6.1.1 application start — the address `nrf_erase2.uf2` is linked for. */
internal const val APP_START_S140_6_1_1 = 0x26000L

/** S140 7.3.0 application start — the address `nrf_erase_sd7_3.uf2` is linked for. */
internal const val APP_START_S140_7_3_0 = 0x27000L

private val ERASE_S140_6_1_1 =
    MaintenanceUf2(
        url = "$ERASE_UF2_BASE/nrf_erase2.uf2",
        fileName = "nrf_erase2.uf2",
        sha256 = "4b778a3def19854415db64cb51bfd29c15b11cc46006353dd518f62d09efe3fe",
        expectedFirstTargetAddress = APP_START_S140_6_1_1,
    )

private val ERASE_S140_7_3_0 =
    MaintenanceUf2(
        url = "$ERASE_UF2_BASE/nrf_erase_sd7_3.uf2",
        fileName = "nrf_erase_sd7_3.uf2",
        sha256 = "13941bedce009e61255c37b1524d11ca604e88c38e7588bb8b391e2998da468f",
        expectedFirstTargetAddress = APP_START_S140_7_3_0,
    )

private val PICO_ERASE =
    MaintenanceUf2(
        url = "$ERASE_UF2_BASE/pico_erase.uf2",
        fileName = "pico_erase.uf2",
        sha256 = "08aa7d561e8b8bf2f9b061b3506fb4d8f135e832efe0f3ae978241db2da0c853",
    )

private fun otafixAsset(board: String, sha256: String): MaintenanceUf2 {
    val name = "update-${board}_bootloader-${OTAFIX_RELEASE_TAG}_nosd.uf2"
    return MaintenanceUf2(url = "$OTAFIX_BASE/$name", fileName = name, sha256 = sha256)
}

/**
 * OTAFIX bootloader self-update images, keyed by the **Board-ID the device itself reports**.
 *
 * Every Adafruit-family bootloader writes `INFO_UF2.TXT` to its mass-storage volume containing a `Board-ID:` line
 * (`ghostfat.c`, from each board's `UF2_BOARD_ID`). Reading that line off the mounted drive identifies the hardware
 * authoritatively, so nothing here depends on correlating names between the two projects — which is impossible anyway:
 * `heltec_t114` vs `heltec-mesh-node-t114`, `thinknode_m1` vs `ThinkNode-M1`, `t1000_e` vs `tracker-t1000-e`.
 *
 * USB VID/PID would not do: four OTAFIX boards share `239A/0029`, all three ThinkNodes share `239A/00DA`, and SenseCAP
 * Solar P1 collides with XIAO nRF52840 BLE on `2886/0044`. Board-IDs are unique across all 14, which a test asserts.
 *
 * This is also the only way to resolve the XIAO nRF52840 BLE / BLE Sense split that OTAFIX's own README calls out: the
 * two differ only by Board-ID, and installing the wrong one over UF2 is exactly what that note warns against.
 */
private val OTAFIX_BY_BOARD_ID: Map<String, MaintenanceUf2> =
    mapOf(
        "HT-n5262" to
            otafixAsset(
                board = "heltec_t114",
                sha256 = "c1ce07c1e66dbf42faea03df88f1e4bac6d66f1177600f41c280059e1653cba2",
            ),
        "MinewSemi-MX25LE01" to
            otafixAsset(
                board = "minewsemi_mx25le01",
                sha256 = "b50a9bd0381155074ccc0a211942365ebd9cd108697c8f2e9d9da947e10265a1",
            ),
        "TRACKER L1" to
            otafixAsset(
                board = "wio_tracker_l1",
                sha256 = "29e11b45d43d0d2ffc49a780c6299bbef86992465a568d74c533d0d0dd5d5e30",
            ),
        "WisBlock-RAK4631-Board" to
            otafixAsset(
                board = "wiscore_rak4631_board",
                sha256 = "910806d0aedfcacf317fc4b9f2469593d6ec0d855568ff69c70faec3a4b06c4a",
            ),
        "WisMesh-Tag" to
            otafixAsset(
                board = "wismesh_tag",
                sha256 = "b9e92b4ec1a74d176f75473be00804ee9902a4816bd94e098ad153ecd60a34c1",
            ),
        "nRF52840-SeeedSenseCAPSolarP1-v1" to
            otafixAsset(
                board = "sensecap_solar_p1",
                sha256 = "f0fad2cfa98867504085fe524a0af65916aa13c781cc5e1ff3025f04cea5db0b",
            ),
        "nRF52840-SeeedXiao-v1" to
            otafixAsset(
                board = "xiao_nrf52840_ble",
                sha256 = "6c7d6c6226c4b425a473f689bb25687baa9cdc79d9a350fd5201762bf7819cba",
            ),
        "nRF52840-SeeedXiaoSense-v1" to
            otafixAsset(
                board = "xiao_nrf52840_ble_sense",
                sha256 = "4857ae18d2f3145534515da3c6e6e2a813722069f0bc415a7fe43d9de8a0be62",
            ),
        "nRF52840-T1000-E-v1" to
            otafixAsset(board = "t1000_e", sha256 = "1b02fb4e8083a85930f615d95adcc29e983f2795a9c7755674d6a380b00410e5"),
        "nRF52840-TEcho-v1" to
            otafixAsset(
                board = "lilygo_techo",
                sha256 = "b254aa092b312238a857e68db5beffda922410092e63044410e4c25f25498b2e",
            ),
        "nRF52840-ThinkNode-M3-v1" to
            otafixAsset(
                board = "thinknode_m3",
                sha256 = "b04f020c7f4f0b7bd99548efbd5db33ebc9e09ef42e5dd874ef69433c363798d",
            ),
        "nRF52840-ThinkNodeM1-v1" to
            otafixAsset(
                board = "thinknode_m1",
                sha256 = "991114392f6b731860f05a932e1c6529f0c97a5e4c054ff51e081d81f2e7d3f1",
            ),
        "nRF52840-ThinkNodeM6-v1" to
            otafixAsset(
                board = "thinknode_m6",
                sha256 = "aea8e4ce5d9f9ff7adc68e794ff735fe94bace7a6d391c3606df4c0ae6f45547",
            ),
        "nRF52840-promicro" to
            otafixAsset(
                board = "promicro_nrf52840",
                sha256 = "bd9cc4de26fd162b6600eafc2634a1e8c6e81ade84c141f8eb44350506321e8b",
            ),
    )

/**
 * Meshtastic `platformioTarget`s whose products OTAFIX lists as supported.
 *
 * A **UX gate only** — it decides whether the upgrade action is offered, never which image is written. Being wrong here
 * costs a user a tap and an "unsupported board" message after the drive is read; it cannot flash anything. Every
 * correctness decision is made by [otafixUf2ForBoardId] against the Board-ID on the drive.
 *
 * Derived from OTAFIX's "Boards supported" list. Deliberately excludes products that merely share a build target with a
 * supported one — WISMESH Hub/Tap, Nomadstar Meteor Pro and RAK3401 all build against `wiscore_rak4631`, and T-Echo
 * Plus/Lite against `t-echo`, but OTAFIX ships no bootloader for them.
 */
private val OTAFIX_SUPPORTED_TARGETS: Set<String> =
    setOf(
        "rak4631",
        "rak_wismeshtag",
        "t-echo",
        "heltec-mesh-node-t114",
        "nrf52_promicro_diy_tcxo",
        "thinknode_m1",
        "thinknode_m3",
        "thinknode_m6",
        "tracker-t1000-e",
        "seeed_wio_tracker_L1",
        "seeed_wio_tracker_L1_eink",
        "seeed_solar_node",
        "seeed_xiao_nrf52840_kit",
    )

/**
 * The factory-erase image for [hardware], or `null` when none can be resolved safely.
 *
 * nRF52840 requires a resolved [DeviceHardware.softDeviceVariant]: the two images are linked for different application
 * start addresses, and the UF2 bootloader's write guard begins at `MBR_SIZE`, so the wrong one erases a SoftDevice
 * page. There is deliberately no default branch.
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

/**
 * True when OTAFIX lists a bootloader for [platformioTarget]'s product. UX gate only — see [OTAFIX_SUPPORTED_TARGETS].
 */
internal fun otafixSupportsTarget(platformioTarget: String): Boolean = platformioTarget in OTAFIX_SUPPORTED_TARGETS

/**
 * The OTAFIX image matching the [boardId] a device reported in its `INFO_UF2.TXT`, or `null` when unrecognized.
 *
 * `null` refuses the upgrade. That is the correct outcome even for a board OTAFIX supports: an unrecognized Board-ID
 * means the installed bootloader is not one we have a verified pairing for, and writing a bootloader built for other
 * hardware is unrecoverable without SWD.
 */
internal fun otafixUf2ForBoardId(boardId: String): MaintenanceUf2? = OTAFIX_BY_BOARD_ID[boardId.trim()]

/** Board-IDs we ship an OTAFIX image for. Exposed for the uniqueness test. */
internal val otafixBoardIds: Set<String>
    get() = OTAFIX_BY_BOARD_ID.keys

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
 * This is the **authoritative** answer to the question the bundled map only estimates: not what the firmware was built
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
 * Which erase image a given variant needs. Total over the enum, so a new variant cannot silently reuse an old image.
 */
internal fun eraseUf2ForVariant(variant: SoftDeviceVariant): MaintenanceUf2 = when (variant) {
    SoftDeviceVariant.S140_6_1_1 -> ERASE_S140_6_1_1
    SoftDeviceVariant.S140_7_3_0 -> ERASE_S140_7_3_0
}

/** Outcome of reconciling the SoftDevice the drive reports against the bundled map. */
internal sealed interface EraseImageResolution {
    /** Safe to write [asset]; [variant] is the SoftDevice it is linked for. */
    data class Resolved(val asset: MaintenanceUf2, val variant: SoftDeviceVariant) : EraseImageResolution

    /**
     * The drive and the bundled map disagree. Always a refusal: one of the two is wrong and we cannot tell which, and
     * guessing writes an erase image into a SoftDevice. Also the signal that a map row needs correcting.
     */
    data class Conflict(val reported: SoftDeviceVariant, val mapped: SoftDeviceVariant) : EraseImageResolution

    /** Neither source produced a variant. */
    data object Unresolved : EraseImageResolution
}

/**
 * Picks the nRF erase image, preferring what the device reports over what the bundled map predicted.
 *
 * The map ([DeviceHardware.softDeviceVariant]) is a pre-flight hint — it decides whether the action is offered before
 * any drive is mounted. Once the drive is readable its own report wins, because it comes from the MBR rather than from
 * a hand-authored table. A disagreement refuses rather than picking a side.
 */
internal fun resolveNrfEraseImage(
    mapped: SoftDeviceVariant?,
    reportedFromDrive: SoftDeviceVariant?,
): EraseImageResolution = when {
    reportedFromDrive != null && mapped != null && reportedFromDrive != mapped ->
        EraseImageResolution.Conflict(reported = reportedFromDrive, mapped = mapped)

    reportedFromDrive != null ->
        EraseImageResolution.Resolved(eraseUf2ForVariant(reportedFromDrive), reportedFromDrive)

    // No SoftDevice line: a bootloader older than the uf2_init that emits it. Fall back to the bundled hint.
    mapped != null -> EraseImageResolution.Resolved(eraseUf2ForVariant(mapped), mapped)

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
