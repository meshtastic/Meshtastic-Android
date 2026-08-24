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
package org.meshtastic.feature.discovery.scan

import org.koin.core.annotation.Single
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.NodeRepository

/** Result of a 2.4 GHz capability check. */
sealed interface HardwareCapabilityResult {
    /** The connected radio supports 2.4 GHz operation. */
    data object Supported : HardwareCapabilityResult

    /** The connected radio does NOT support 2.4 GHz operation. */
    data class Unsupported(val reason: String) : HardwareCapabilityResult

    /** Capability could not be determined (hardware data unavailable or ambiguous). */
    data class Unknown(val reason: String) : HardwareCapabilityResult
}

/**
 * Determines whether the currently connected radio supports 2.4 GHz LoRa operation (SX1280 chip).
 *
 * Uses a layered heuristic:
 * 1. Check for explicit `2.4ghz` or `sx1280` tags in the hardware metadata.
 * 2. Check the platformIO target or slug for `sx1280`, `2.4`, or `2400` patterns.
 * 3. Default to [HardwareCapabilityResult.Unknown] when no evidence is available.
 */
@Single
class Check24GhzCapability(
    private val nodeRepository: NodeRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
) {
    /**
     * Checks if the currently connected radio supports 2.4 GHz. Returns [HardwareCapabilityResult.Unknown] if not
     * connected or hardware data is unavailable.
     */
    @Suppress("ReturnCount")
    suspend operator fun invoke(): HardwareCapabilityResult {
        val ourNode = nodeRepository.ourNodeInfo.value ?: return HardwareCapabilityResult.Unknown("No radio connected")
        val hwModel = ourNode.user.hw_model.value
        if (hwModel == 0) return HardwareCapabilityResult.Unknown("Hardware model unknown")

        val myNodeInfo = nodeRepository.myNodeInfo.value
        val target = myNodeInfo?.pioEnv

        val hw =
            deviceHardwareRepository.getDeviceHardwareByModel(hwModel, target).getOrNull()
                ?: return HardwareCapabilityResult.Unknown("Hardware metadata unavailable for model $hwModel")

        return evaluate(hw)
    }

    @Suppress("ReturnCount")
    internal fun evaluate(hw: DeviceHardware): HardwareCapabilityResult {
        // Layer 1: Check explicit tags
        val tags = hw.tags.orEmpty().map { it.lowercase() }
        if (tags.any { it in SUPPORTED_TAGS }) return HardwareCapabilityResult.Supported
        if (tags.any { it in UNSUPPORTED_TAGS }) {
            return HardwareCapabilityResult.Unsupported("Hardware tagged as sub-GHz only")
        }

        // Layer 2: Check platformioTarget or hwModelSlug for SX1280/2.4GHz patterns
        val targetLower = hw.platformioTarget.lowercase()
        val slugLower = hw.hwModelSlug.lowercase()
        if (SUPPORTED_PATTERNS.any { it in targetLower || it in slugLower }) {
            return HardwareCapabilityResult.Supported
        }

        // Layer 3: No definitive evidence — default to unknown/unsupported
        return HardwareCapabilityResult.Unknown("Cannot verify 2.4 GHz support for ${hw.displayName}")
    }

    companion object {
        private val SUPPORTED_TAGS = setOf("2.4ghz", "sx1280", "lora24", "2400mhz")
        private val UNSUPPORTED_TAGS = setOf("sub-ghz-only", "sx1262", "sx1276")
        private val SUPPORTED_PATTERNS = listOf("sx1280", "2.4", "2400", "lora24")
    }
}
