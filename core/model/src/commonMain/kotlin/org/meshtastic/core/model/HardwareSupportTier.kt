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
 * The rung a board reads at wherever the app names its support status. Ordered top to bottom: Backer/Partner hardware,
 * independent maker hardware, then community hardware.
 */
enum class HardwareSupportTier {
    SUPPORTED,
    MAKER,
    COMMUNITY,
}

/** The registry's `supportLevel` for legacy hardware; 1 is flagship and 2 is niche. */
private const val SUPPORT_LEVEL_LEGACY = 3

/**
 * Resolves the rung in one place so every surface agrees. [DeviceHardware.activelySupported] is the top rung and wins
 * over [DeviceHardware.isMaker]; a maker board reads as maker only while its `supportLevel` is flagship or niche, so a
 * legacy or untiered maker board is community.
 */
val DeviceHardware.supportTier: HardwareSupportTier
    get() =
        when {
            activelySupported -> HardwareSupportTier.SUPPORTED
            isMaker && supportLevel != null && supportLevel < SUPPORT_LEVEL_LEGACY -> HardwareSupportTier.MAKER
            else -> HardwareSupportTier.COMMUNITY
        }
