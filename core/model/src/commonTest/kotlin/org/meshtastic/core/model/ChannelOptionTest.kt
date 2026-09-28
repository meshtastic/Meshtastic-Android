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

import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChannelOptionTest {

    /**
     * Ensures that every [ModemPreset] defined in the protobufs has a corresponding entry in [ChannelOption].
     *
     * If this test fails, a ModemPreset was added or changed in the firmware/protobufs and you must update the
     * [ChannelOption] enum to match.
     */
    @Test
    fun ensure_every_ModemPreset_is_mapped_in_ChannelOption() {
        val unmappedPresets = ModemPreset.entries.filter { it.name != "UNSET" && it.name != "UNRECOGNIZED" }

        unmappedPresets.forEach { preset ->
            val channelOption = ChannelOption.from(preset)
            assertNotNull(
                channelOption,
                "Missing ChannelOption mapping for ModemPreset: '${preset.name}'. " +
                    "Please add a corresponding entry to the ChannelOption enum class.",
            )
        }
    }

    /**
     * Ensures that there are no extra entries in [ChannelOption] that don't correspond to a valid [ModemPreset].
     *
     * If this test fails, a ModemPreset was removed from the protobufs and you must remove the corresponding entry from
     * the [ChannelOption] enum.
     */
    @Test
    fun ensure_no_extra_mappings_exist_in_ChannelOption() {
        val protoPresets = ModemPreset.entries.filter { it.name != "UNSET" && it.name != "UNRECOGNIZED" }.toSet()
        val mappedPresets = ChannelOption.entries.map { it.modemPreset }.toSet()

        assertEquals(
            protoPresets,
            mappedPresets,
            "The set of ModemPresets in protobufs does not match the set of ModemPresets mapped in ChannelOption. " +
                "Check for removed presets in protobufs or duplicate mappings in ChannelOption.",
        )

        assertEquals(
            protoPresets.size,
            ChannelOption.entries.size,
            "Each ChannelOption must map to a unique ModemPreset.",
        )
    }

    @Test
    fun a_preset_offers_only_the_coding_rates_above_its_own() {
        assertEquals(6..8, ChannelOption.LONG_FAST.codingRateOverrides)
        assertEquals(7..8, ChannelOption.NARROW_FAST.codingRateOverrides)
        assertTrue(ChannelOption.LONG_SLOW.codingRateOverrides.isEmpty())
    }

    @Test
    fun a_stored_coding_rate_the_preset_already_meets_is_the_preset_default() {
        assertEquals(0, ChannelOption.LONG_FAST.codingRateOverride(5))
        assertEquals(0, ChannelOption.LONG_FAST.codingRateOverride(0))
        assertEquals(0, ChannelOption.LONG_FAST.codingRateOverride(9))
        assertEquals(0, ChannelOption.NARROW_FAST.codingRateOverride(6))
        assertEquals(0, ChannelOption.LONG_SLOW.codingRateOverride(8))
        assertEquals(7, ChannelOption.LONG_FAST.codingRateOverride(7))
    }

    @Test
    fun the_effective_coding_rate_is_the_override_or_else_the_preset() {
        assertEquals(7, ChannelOption.LONG_FAST.effectiveCodingRate(7))
        assertEquals(5, ChannelOption.LONG_FAST.effectiveCodingRate(0))
        assertEquals(6, ChannelOption.NARROW_FAST.effectiveCodingRate(5))
    }

    @Test
    fun normalizing_keeps_an_override_that_still_raises_the_new_preset() {
        assertEquals(7, presetConfig(ModemPreset.MEDIUM_FAST, codingRate = 7).normalizeCodingRateOverride().coding_rate)
    }

    @Test
    fun normalizing_resets_an_override_the_preset_already_meets() {
        assertEquals(0, presetConfig(ModemPreset.LONG_SLOW, codingRate = 7).normalizeCodingRateOverride().coding_rate)
        assertEquals(0, presetConfig(ModemPreset.LONG_FAST, codingRate = 5).normalizeCodingRateOverride().coding_rate)
    }

    @Test
    fun normalizing_leaves_a_manual_config_alone() {
        val manual =
            LoRaConfig.Builder()
                .also { wb ->
                    wb.use_preset = false
                    wb.coding_rate = 5
                }
                .build()
        assertEquals(manual, manual.normalizeCodingRateOverride())
    }

    private fun presetConfig(preset: ModemPreset, codingRate: Int) = LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = true
            wb.modem_preset = preset
            wb.coding_rate = codingRate
        }
        .build()
}
