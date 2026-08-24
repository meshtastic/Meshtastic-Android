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

import kotlin.jvm.JvmInline

/** Outcome of checking a device's firmware version against the app's minimum requirements. */
enum class FirmwareCheckStatus {
    /** Firmware meets the recommended minimum. */
    OK,

    /** Firmware is usable but below the recommended minimum; the user should be nudged to update. */
    SHOULD_UPDATE,

    /** Firmware is below the absolute minimum the app supports. */
    TOO_OLD,

    /** The version string could not be parsed (e.g. a blank/transient value during connection). Status is unknown. */
    UNKNOWN,
}

/** Zero-overhead wrapper providing structured access to parse and compare device version strings. */
@JvmInline
value class DeviceVersion(val asString: String) : Comparable<DeviceVersion> {

    /** The integer representation of the version (e.g., 2.7.12 → 20712). */
    val asInt: Int
        get() = parseVersion(asString)

    /** True when the version string parsed to a real version (non-zero). */
    val isValid: Boolean
        get() = asInt > 0

    /**
     * Classify this version against the app's firmware requirements.
     *
     * An unparseable version (int 0) is [FirmwareCheckStatus.UNKNOWN], NOT [FirmwareCheckStatus.TOO_OLD] — a transient
     * or garbled version string arriving mid-handshake must never be mistaken for ancient firmware (regression #3726).
     */
    val checkStatus: FirmwareCheckStatus
        get() =
            when {
                !isValid -> FirmwareCheckStatus.UNKNOWN
                this < DeviceVersion(ABS_MIN_FW_VERSION) -> FirmwareCheckStatus.TOO_OLD
                this < DeviceVersion(MIN_FW_VERSION) -> FirmwareCheckStatus.SHOULD_UPDATE
                else -> FirmwareCheckStatus.OK
            }

    override fun compareTo(other: DeviceVersion): Int = asInt.compareTo(other.asInt)

    companion object {
        const val MIN_FW_VERSION = "2.5.14"
        const val ABS_MIN_FW_VERSION = "2.3.15"

        private val VERSION_REGEX = Regex("(\\d{1,2})\\.(\\d{1,2})\\.(\\d{1,2})")

        /**
         * Convert a version string of the form 1.23.57 to a comparable integer (12357). Returns 0 for unparseable
         * strings.
         */
        @Suppress("MagicNumber")
        private fun parseVersion(s: String): Int {
            val versionString = if (s.count { it == '.' } == 1) "$s.0" else s
            val match = VERSION_REGEX.find(versionString) ?: return 0
            val (major, minor, build) = match.destructured
            return major.toInt() * 10000 + minor.toInt() * 100 + build.toInt()
        }
    }
}
