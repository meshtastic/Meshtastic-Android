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

import org.meshtastic.core.common.util.nowMillis

data class FirmwareRelease(
    val id: String = "",
    val pageUrl: String = "",
    val releaseNotes: String = "",
    val title: String = "",
    val zipUrl: String = "",
    val lastUpdated: Long = nowMillis,
    val releaseType: FirmwareReleaseType = FirmwareReleaseType.STABLE,
)

fun FirmwareRelease.asDeviceVersion(): DeviceVersion = DeviceVersion(id.substringBeforeLast(".").replace("v", ""))

enum class FirmwareReleaseType {
    STABLE,
    ALPHA,

    /** Nightly preview from the nightly host's root; gated behind the modules unlock. */
    NIGHTLY,
    LOCAL,
}
