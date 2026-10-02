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

/**
 * The rung a board reads at wherever the app names its support status. Top to bottom: hardware the project actively
 * supports, independent maker hardware, then community hardware (including anything not actively supported).
 */
enum class HardwareSupportTier {
    SUPPORTED,
    MAKER,
    COMMUNITY,
}

/** The registry's `supportLevel` for legacy hardware; 1 is flagship and 2 is niche. */
private const val SUPPORT_LEVEL_LEGACY = 3

/**
 * Resolves the rung in one place so every surface agrees. [DeviceHardware.isMaker] is a relationship and wins first, so
 * a maker board never reads as plain supported once the registry promotes it; [DeviceHardware.activelySupported] is the
 * project's lifecycle flag and decides the other two rungs. A legacy or untiered maker board falls through to that
 * lifecycle check.
 */
val DeviceHardware.supportTier: HardwareSupportTier
    get() =
        when {
            isMaker && supportLevel != null && supportLevel < SUPPORT_LEVEL_LEGACY -> HardwareSupportTier.MAKER
            activelySupported -> HardwareSupportTier.SUPPORTED
            else -> HardwareSupportTier.COMMUNITY
        }
