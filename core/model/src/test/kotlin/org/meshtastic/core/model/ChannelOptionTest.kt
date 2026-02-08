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
import org.meshtastic.proto.Config

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
        // Get all possible ModemPreset values.
        val unmappedPresets =
            Config.LoRaConfig.ModemPreset.entries.filter { it.name != "UNSET" && it.name != "UNRECOGNIZED" }

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
        val protoPresets =
            Config.LoRaConfig.ModemPreset.entries.filter { it.name != "UNSET" && it.name != "UNRECOGNIZED" }.toSet()
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
}
