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
package org.meshtastic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.proto.ConfigKt.loRaConfig
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.RegionCode

class ChannelOptionTest {

    /**
     * This test ensures that every `ModemPreset` defined in the protobufs has a corresponding entry in our
     * `ChannelOption` enum.
     *
     * If this test fails, it means a `ModemPreset` was added or changed in the firmware/protobufs, and you must update
     * the `ChannelOption` enum to match.
     */
    @Test
    fun `ensure every ModemPreset is mapped in ChannelOption`() {
        // Get all possible ModemPreset values, excluding the ones we expect to ignore.
        val unmappedPresets =
            ModemPreset.entries.filter {
                // UNRECOGNIZED is a system-generated value for forward compatibility.
                it != ModemPreset.UNRECOGNIZED
            }

        unmappedPresets.forEach { preset ->
            // Attempt to find the corresponding ChannelOption
            val channelOption = ChannelOption.from(preset)

            // Assert that a mapping exists, with a detailed failure message.
            assertNotNull(
                "Missing ChannelOption mapping for ModemPreset: '${preset.name}'. " +
                    "Please add a corresponding entry to the ChannelOption enum class.",
                channelOption,
            )
        }
    }

    /**
     * This test ensures that there are no extra entries in `ChannelOption` that don't correspond to a valid
     * `ModemPreset`.
     *
     * If this test fails, it means a `ModemPreset` was removed from the protobufs, and you must remove the
     * corresponding entry from the `ChannelOption` enum.
     */
    @Test
    fun `ensure no extra mappings exist in ChannelOption`() {
        val protoPresets = ModemPreset.entries.filter { it != ModemPreset.UNRECOGNIZED }.toSet()
        val mappedPresets = ChannelOption.entries.map { it.modemPreset }.toSet()

        assertEquals(
            "The set of ModemPresets in protobufs does not match the set of ModemPresets mapped in ChannelOption. " +
                "Check for removed presets in protobufs or duplicate mappings in ChannelOption.",
            protoPresets,
            mappedPresets,
        )

        assertEquals(
            "Each ChannelOption must map to a unique ModemPreset.",
            protoPresets.size,
            ChannelOption.entries.size,
        )
    }

    @Test
    fun `test radioFreq and numChannels for NARROW_868`() {
        val loraConfig = loRaConfig {
            region = RegionCode.NARROW_868
            usePreset = true
            modemPreset = ModemPreset.NARROW_FAST
        }

        // bw = 0.0625, spacing = 0.015, channelSpacing = 0.0775
        // Range = 869.65 - 869.4 = 0.25
        // numChannels = round(0.25 / 0.0775) = 3
        assertEquals(3, loraConfig.numChannels)

        // Slot 1: freqStart + spacing + bw/2 = 869.4 + 0.015 + 0.03125 = 869.44625
        assertEquals(869.44625f, loraConfig.radioFreq(1), 0.0001f)

        // Slot 3: 869.44625 + 2 * 0.0775 = 869.44625 + 0.155 = 869.60125
        assertEquals(869.60125f, loraConfig.radioFreq(3), 0.0001f)
    }
}
