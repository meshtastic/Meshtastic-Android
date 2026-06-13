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

import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract test pinning the preset -> channel-name mapping in [Channel.name].
 *
 * This name is INTEROP-CRITICAL: for a channel with an empty name and `use_preset = true`, the name is hashed into the
 * channel hash, the channel number / radio frequency, and the MQTT topic, so it must byte-match the firmware. Source of
 * truth is firmware `DisplayFormatters::getModemPresetDisplayName(preset, useShortName = false)`. Two names are
 * deliberately abbreviated (LONG_MODERATE -> "LongMod", VERY_LONG_SLOW -> "VLongSlow") and must NOT be auto-derived
 * from the enum name.
 *
 * When firmware adds a preset, [Channel.name]'s exhaustive `when` fails to compile first (by design). This test is the
 * backstop: it forces the new branch to carry the EXACT firmware name, guards the mapping against accidental edits, and
 * fails if a preset is left unpinned (e.g. someone silenced the compile error with an `else`). It deliberately covers
 * the deprecated VERY_LONG_SLOW, which still has a real firmware name. The numeric anchors (hash/channelNum/radioFreq)
 * live in ChannelTest and are the genuine on-air interop guard — keep both.
 */
class ChannelPresetNameTest {

    // Firmware-canonical names: DisplayFormatters::getModemPresetDisplayName(preset, useShortName = false).
    private val expectedNames =
        mapOf(
            ModemPreset.SHORT_TURBO to "ShortTurbo",
            ModemPreset.SHORT_FAST to "ShortFast",
            ModemPreset.SHORT_SLOW to "ShortSlow",
            ModemPreset.MEDIUM_FAST to "MediumFast",
            ModemPreset.MEDIUM_SLOW to "MediumSlow",
            ModemPreset.LONG_FAST to "LongFast",
            ModemPreset.LONG_SLOW to "LongSlow",
            ModemPreset.LONG_MODERATE to "LongMod",
            ModemPreset.VERY_LONG_SLOW to "VLongSlow",
            ModemPreset.LONG_TURBO to "LongTurbo",
            ModemPreset.LITE_FAST to "LiteFast",
            ModemPreset.LITE_SLOW to "LiteSlow",
            ModemPreset.NARROW_FAST to "NarrowFast",
            ModemPreset.NARROW_SLOW to "NarrowSlow",
            ModemPreset.TINY_FAST to "TinyFast",
            ModemPreset.TINY_SLOW to "TinySlow",
        )

    private fun presetChannelName(preset: ModemPreset): String =
        Channel(loraConfig = Channel.default.loraConfig.copy(use_preset = true, modem_preset = preset)).name

    @Test
    fun every_preset_maps_to_its_exact_firmware_name() {
        expectedNames.forEach { (preset, expected) ->
            assertEquals(expected, presetChannelName(preset), "Channel name for $preset must match firmware exactly")
        }
    }

    @Test
    fun every_ModemPreset_is_pinned() {
        val protoPresets = ModemPreset.entries.filter { it.name != "UNSET" && it.name != "UNRECOGNIZED" }.toSet()
        assertEquals(
            protoPresets,
            expectedNames.keys,
            "Every ModemPreset must be pinned to its firmware name here. A new preset was added to the protos " +
                "(and to Channel.name's `when`) but not recorded in this contract — add it with its exact " +
                "DisplayFormatters::getModemPresetDisplayName string.",
        )
    }
}
