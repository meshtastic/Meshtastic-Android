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
package org.meshtastic.feature.discovery

import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.testing.FakeDeviceHardwareRepository
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.feature.discovery.scan.Check24GhzCapability
import org.meshtastic.feature.discovery.scan.HardwareCapabilityResult
import kotlin.test.Test
import kotlin.test.assertIs

class Check24GhzCapabilityTest {

    private val check =
        Check24GhzCapability(
            nodeRepository = FakeNodeRepository(),
            deviceHardwareRepository = FakeDeviceHardwareRepository(),
        )

    // --- Tag-based detection ---

    @Test
    fun evaluate_returns_supported_when_tag_contains_sx1280() {
        val hw = baseHardware(tags = listOf("sx1280", "ble"))
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_supported_when_tag_contains_2_4ghz() {
        val hw = baseHardware(tags = listOf("2.4ghz"))
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_supported_when_tag_contains_lora24() {
        val hw = baseHardware(tags = listOf("lora24", "esp32"))
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_unsupported_when_tag_contains_sub_ghz_only() {
        val hw = baseHardware(tags = listOf("sub-ghz-only"))
        assertIs<HardwareCapabilityResult.Unsupported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_unsupported_when_tag_contains_sx1262() {
        val hw = baseHardware(tags = listOf("sx1262"))
        assertIs<HardwareCapabilityResult.Unsupported>(check.evaluate(hw))
    }

    // --- Pattern-based detection (target / slug) ---

    @Test
    fun evaluate_returns_supported_when_target_contains_sx1280() {
        val hw = baseHardware(platformioTarget = "tlora-v2_1-1_6-sx1280")
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_supported_when_slug_contains_2400() {
        val hw = baseHardware(hwModelSlug = "rak-2400")
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_returns_supported_when_target_contains_lora24() {
        val hw = baseHardware(platformioTarget = "nano-g2-lora24")
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    // --- Fallback to unknown ---

    @Test
    fun evaluate_returns_unknown_when_no_evidence_available() {
        val hw = baseHardware(platformioTarget = "heltec-v3", hwModelSlug = "heltec-v3", tags = emptyList())
        val result = check.evaluate(hw)
        assertIs<HardwareCapabilityResult.Unknown>(result)
    }

    @Test
    fun evaluate_returns_unknown_when_tags_are_null() {
        val hw = baseHardware(tags = null)
        val result = check.evaluate(hw)
        assertIs<HardwareCapabilityResult.Unknown>(result)
    }

    // --- Edge cases ---

    @Test
    fun evaluate_tag_matching_is_case_insensitive() {
        val hw = baseHardware(tags = listOf("SX1280", "BLE"))
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    @Test
    fun evaluate_supported_tag_takes_precedence_when_both_present() {
        // If hardware has both supported and unsupported tags (unusual), supported wins
        val hw = baseHardware(tags = listOf("sx1280", "sx1262"))
        assertIs<HardwareCapabilityResult.Supported>(check.evaluate(hw))
    }

    private fun baseHardware(
        platformioTarget: String = "generic-target",
        hwModelSlug: String = "generic-slug",
        tags: List<String>? = null,
    ) = DeviceHardware(
        activelySupported = true,
        architecture = "esp32",
        displayName = "Test Device",
        hwModel = 42,
        hwModelSlug = hwModelSlug,
        platformioTarget = platformioTarget,
        tags = tags,
    )
}
