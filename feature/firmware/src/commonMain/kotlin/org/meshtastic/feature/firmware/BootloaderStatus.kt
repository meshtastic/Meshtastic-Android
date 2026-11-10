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

import org.meshtastic.core.model.MaintenanceUf2Manifest

/** Installed bootloader against the latest release, or no comparison when the latest tag is not plain `x.y.z`. */
data class BootloaderStatus(val installed: InstalledBootloader, val latest: KotlinVersion?) {
    val isCurrent: Boolean
        get() = installed is InstalledBootloader.Known && latest != null && installed.version >= latest
}

/** The bootloader a running nRF52 device reports in `DeviceMetadata.bootloader_version`. */
sealed interface InstalledBootloader {
    /** Stock Adafruit 0.9.2 and every Meshtastic nRF52 Bootloader release before 2.6.0 report 0.9.2 alike. */
    data object OlderThanFirstNumbered : InstalledBootloader

    data class Known(val version: KotlinVersion) : InstalledBootloader
}

/** First Meshtastic nRF52 Bootloader release tagged plain `x.y.z`, so the first that reports its own version. */
internal val FIRST_NUMBERED_BOOTLOADER = KotlinVersion(2, 6, 0)

private val SHARED_LEGACY_BOOTLOADER = KotlinVersion(0, 9, 2)
private const val BYTE_MASK = 0xff
private const val MAJOR_SHIFT = 16
private const val MINOR_SHIFT = 8
private val PLAIN_VERSION = Regex("""\d+\.\d+\.\d+""")

/** Decodes `major << 16 | minor << 8 | patch`; 0, or a major past one byte, reads as no reported version. */
internal fun decodeBootloaderVersion(packed: Int): InstalledBootloader? {
    val major = packed ushr MAJOR_SHIFT
    if (packed == 0 || major > BYTE_MASK) return null
    val version = KotlinVersion(major, (packed ushr MINOR_SHIFT) and BYTE_MASK, packed and BYTE_MASK)
    return if (version == SHARED_LEGACY_BOOTLOADER) {
        InstalledBootloader.OlderThanFirstNumbered
    } else {
        InstalledBootloader.Known(version)
    }
}

/** Parses a release tag that is exactly `x.y.z`; anything else has no order to compare against. */
internal fun parsePlainBootloaderVersion(tag: String): KotlinVersion? = tag.takeIf { PLAIN_VERSION.matches(it) }
    ?.split('.')
    ?.map { it.toIntOrNull() ?: -1 }
    ?.takeIf { parts -> parts.all { it in 0..KotlinVersion.MAX_COMPONENT_VALUE } }
    ?.let { (major, minor, patch) -> KotlinVersion(major, minor, patch) }

/** Null when the device reports no bootloader version, which leaves the screen as it was before the field existed. */
internal fun bootloaderStatus(packed: Int, manifest: MaintenanceUf2Manifest): BootloaderStatus? =
    decodeBootloaderVersion(packed)?.let { installed ->
        BootloaderStatus(installed, parsePlainBootloaderVersion(manifest.otafixReleaseTag))
    }
