/*
 * Copyright (c) 2025 Meshtastic LLC
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

import junit.framework.Assert.assertNotNull
import org.junit.Test
import org.meshtastic.proto.ConfigProtos.Config.LoRaConfig.ModemPreset

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
                // UNSET is a valid state but doesn't have a specific channel configuration.
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
}
