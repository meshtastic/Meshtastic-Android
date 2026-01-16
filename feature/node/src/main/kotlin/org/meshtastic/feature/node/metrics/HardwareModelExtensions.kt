/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.feature.node.metrics

import co.touchlab.kermit.Logger
import org.meshtastic.proto.HardwareModel

/**
 * Safely extracts the hardware model number from a HardwareModel enum.
 *
 * This function handles unknown enum values gracefully by catching IllegalArgumentException and returning a fallback
 * value. This prevents crashes when the app receives data from devices with hardware models not yet defined in the
 * current protobuf version.
 *
 * @param fallbackValue The value to return if the enum is unknown (defaults to 0 for UNSET)
 * @return The hardware model number, or the fallback value if the enum is unknown
 */
@Suppress("detekt:SwallowedException")
fun HardwareModel.safeNumber(fallbackValue: Int = -1): Int = try {
    this.value
} catch (e: IllegalArgumentException) {
    Logger.w { "Unknown hardware model enum value: $this, using fallback value: $fallbackValue" }
    fallbackValue
}
