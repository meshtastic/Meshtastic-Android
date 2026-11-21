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
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Frequency-slot expectations mirror the firmware's region table and slot math in
 * `RadioInterface.cpp::applyModemConfig` (slot width = spacing + 2*padding + bandwidth; default slots from each
 * region's RDEF entry).
 */
class RegionInfoTest {

    private fun lora(region: RegionCode, preset: ModemPreset, channelNum: Int = 0) = LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = true
            wb.modem_preset = preset
            wb.region = region
            wb.channel_num = channelNum
        }
        .build()

    private fun customLora(region: RegionCode, bandwidthCode: Int, channelNum: Int = 0) = LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = false
            wb.bandwidth = bandwidthCode
            wb.region = region
            wb.channel_num = channelNum
        }
        .build()

    /**
     * Every region the firmware's region table defines must be selectable in the app. The proto enum is a superset
     * (e.g. EU_874, EU_917, UA_868 have no firmware entry), so parity is asserted against this explicit list.
     */
    @Test
    fun ensure_firmware_regions_are_mapped_in_RegionInfo() {
        val firmwareRegions =
            listOf(
                RegionCode.US,
                RegionCode.EU_433,
                RegionCode.EU_868,
                RegionCode.EU_866,
                RegionCode.EU_N_868,
                RegionCode.CN,
                RegionCode.JP,
                RegionCode.ANZ,
                RegionCode.ANZ_433,
                RegionCode.RU,
                RegionCode.KR,
                RegionCode.TW,
                RegionCode.IN,
                RegionCode.NZ_865,
                RegionCode.TH,
                RegionCode.UA_433,
                RegionCode.MY_433,
                RegionCode.MY_919,
                RegionCode.SG_923,
                RegionCode.PH_433,
                RegionCode.PH_868,
                RegionCode.PH_915,
                RegionCode.KZ_433,
                RegionCode.KZ_863,
                RegionCode.NP_865,
                RegionCode.BR_902,
                RegionCode.ITU1_2M,
                RegionCode.ITU2_2M,
                RegionCode.ITU3_2M,
                RegionCode.ITU2_125CM,
                RegionCode.ITU1_70CM,
                RegionCode.ITU2_70CM,
                RegionCode.ITU3_70CM,
                RegionCode.LORA_24,
                RegionCode.UNSET,
            )
        firmwareRegions.forEach { region ->
            assertNotNull(
                RegionInfo.fromRegionCode(region),
                "Missing RegionInfo entry for firmware region '${region.name}'.",
            )
        }
    }

    @Test
    fun ham_2m_regions_use_20kHz_slots() {
        // ITU1_2M: 144-146 MHz, TINY_FAST (15.625 kHz) padded to 20.025 kHz slots -> 100 slots, default slot 26.
        val itu1 = lora(RegionCode.ITU1_2M, ModemPreset.TINY_FAST)
        assertEquals(100, itu1.numChannels)
        assertEquals(26, itu1.channelNum("TinyFast"))
        assertEquals(144.5106f, itu1.radioFreq(26), 0.001f)

        // ITU2_2M: 144-148 MHz -> 200 slots, default slot 51 (145.010 MHz).
        val itu2 = lora(RegionCode.ITU2_2M, ModemPreset.TINY_FAST)
        assertEquals(200, itu2.numChannels)
        assertEquals(145.011f, itu2.radioFreq(itu2.channelNum("TinyFast")), 0.001f)

        // ITU3_2M: 144-148 MHz -> 200 slots, default slot 33 (144.650 MHz).
        val itu3 = lora(RegionCode.ITU3_2M, ModemPreset.TINY_FAST)
        assertEquals(200, itu3.numChannels)
        assertEquals(144.6508f, itu3.radioFreq(itu3.channelNum("TinyFast")), 0.001f)
    }

    @Test
    fun ham_70cm_and_125cm_regions_use_100kHz_slots() {
        // ITU2_70CM: 420-450 MHz, NARROW_SLOW (62.5 kHz) padded to 100 kHz slots -> 300 slots,
        // default slot 137 (433.650 MHz).
        val itu270cm = lora(RegionCode.ITU2_70CM, ModemPreset.NARROW_SLOW)
        assertEquals(300, itu270cm.numChannels)
        assertEquals(137, itu270cm.channelNum("NarrowSlow"))
        assertEquals(433.65f, itu270cm.radioFreq(137), 0.001f)

        // ITU1_70CM: 430-440 MHz -> 100 slots, default slot 37 (433.650 MHz).
        val itu170cm = lora(RegionCode.ITU1_70CM, ModemPreset.NARROW_SLOW)
        assertEquals(100, itu170cm.numChannels)
        assertEquals(433.65f, itu170cm.radioFreq(itu170cm.channelNum("NarrowSlow")), 0.001f)

        // ITU3_70CM: 430-450 MHz -> 200 slots, default slot 37 (433.650 MHz).
        val itu370cm = lora(RegionCode.ITU3_70CM, ModemPreset.NARROW_SLOW)
        assertEquals(200, itu370cm.numChannels)
        assertEquals(433.65f, itu370cm.radioFreq(itu370cm.channelNum("NarrowSlow")), 0.001f)

        // ITU2_125CM: 220-225 MHz -> 50 slots, default slot 37 (223.650 MHz).
        val itu2125cm = lora(RegionCode.ITU2_125CM, ModemPreset.NARROW_SLOW)
        assertEquals(50, itu2125cm.numChannels)
        assertEquals(223.65f, itu2125cm.radioFreq(itu2125cm.channelNum("NarrowSlow")), 0.001f)
    }

    @Test
    fun eu_866_lite_gives_four_600kHz_slots() {
        // EU_866: 865.6-867.6 MHz, LITE_FAST (125 kHz) with 400 kHz spacing + 37.5 kHz padding
        // -> 4 slots at 865.7/866.3/866.9/867.5 MHz.
        val eu866 = lora(RegionCode.EU_866, ModemPreset.LITE_FAST)
        assertEquals(4, eu866.numChannels)
        assertEquals(865.7f, eu866.radioFreq(1), 0.001f)
        assertEquals(867.5f, eu866.radioFreq(4), 0.001f)
    }

    @Test
    fun eu_n_868_narrow_defaults_to_first_slot() {
        val euN868 = lora(RegionCode.EU_N_868, ModemPreset.NARROW_SLOW)
        assertEquals(3, euN868.numChannels)
        assertEquals(1, euN868.channelNum("NarrowSlow"))
        assertEquals(869.4417f, euN868.radioFreq(1), 0.001f)
    }

    @Test
    fun standard_regions_keep_legacy_slot_math() {
        // US: 902-928 MHz, LONG_FAST (250 kHz), no spacing/padding/override -> 104 hash-addressed slots.
        val us = lora(RegionCode.US, ModemPreset.LONG_FAST)
        assertEquals(104, us.numChannels)
        assertEquals(906.875f, us.radioFreq(20), 0.001f)

        // Firmware adds frequency_offset to the slot frequency as well as to an override.
        val offset = us.newBuilder().also { wb -> wb.frequency_offset = 0.01f }.build()
        assertEquals(906.885f, offset.radioFreq(20), 0.001f)

        // KZ_863: 863-868 MHz, standard (not wide) LoRa like firmware's RDEF -> 20 slots.
        assertEquals(20, lora(RegionCode.KZ_863, ModemPreset.LONG_FAST).numChannels)
    }

    @Test
    fun non_ascii_channel_names_hash_their_utf8_bytes_like_firmware() {
        val us = lora(RegionCode.US, ModemPreset.LONG_FAST)
        assertEquals(20, us.channelNum("LongFast"))
        assertEquals(23, us.channelNum("Häme"))
        assertEquals(93, us.channelNum("Mesh🙂"))
    }

    @Test
    fun explicit_channel_num_wins_over_override_slot() {
        val itu1 = lora(RegionCode.ITU1_2M, ModemPreset.TINY_FAST, channelNum = 5)
        assertEquals(5, itu1.channelNum("TinyFast"))
    }

    @Test
    fun jp_narrow_and_lite_presets_use_15_200kHz_unit_channels() {
        // ARIB STD-T108: 62.5 kHz and 125 kHz occupy 1 unit channel (0.2 MHz): 9 lower + 6 upper = 15 slots.
        for (preset in listOf(ModemPreset.NARROW_SLOW, ModemPreset.LITE_FAST)) {
            val cfg = lora(RegionCode.JP, preset)
            assertEquals(15, cfg.numChannels)
            assertEquals(920.6f, cfg.radioFreq(1), 0.001f)
            assertEquals(920.8f, cfg.radioFreq(2), 0.001f)
            assertEquals(922.2f, cfg.radioFreq(9), 0.001f)
            assertEquals(922.4f, cfg.radioFreq(10), 0.001f)
            assertEquals(923.4f, cfg.radioFreq(15), 0.001f)
        }

        val narrowSlow = lora(RegionCode.JP, ModemPreset.NARROW_SLOW)
        assertEquals(2, narrowSlow.channelNum("NarrowSlow"))
        assertEquals(920.8f, narrowSlow.radioFreq(narrowSlow.channelNum("NarrowSlow")), 0.001f)

        val narrowFast = lora(RegionCode.JP, ModemPreset.NARROW_FAST)
        assertEquals(11, narrowFast.channelNum("NarrowFast"))
        assertEquals(922.6f, narrowFast.radioFreq(narrowFast.channelNum("NarrowFast")), 0.001f)

        val liteFast = lora(RegionCode.JP, ModemPreset.LITE_FAST)
        assertEquals(5, liteFast.channelNum("LiteFast"))
        assertEquals(921.4f, liteFast.radioFreq(liteFast.channelNum("LiteFast")), 0.001f)
    }

    @Test
    fun jp_250kHz_presets_bond_two_unit_channels_without_crossing_922_3_MHz() {
        // ARIB STD-T108 Table 3-2: 250 kHz bonds 2 unit channels (0.4 MHz): 4 lower + 3 upper = 7 slots.
        val longFast = lora(RegionCode.JP, ModemPreset.LONG_FAST)
        assertEquals(7, longFast.numChannels)
        assertEquals(6, longFast.channelNum("LongFast"))
        val expected = listOf(920.7f, 921.1f, 921.5f, 921.9f, 922.5f, 922.9f, 923.3f)
        expected.forEachIndexed { index, freq -> assertEquals(freq, longFast.radioFreq(index + 1), 0.001f) }
    }

    @Test
    fun jp_500kHz_turbo_presets_bond_three_unit_channels() {
        // ARIB STD-T108 Table 3-3: 500 kHz bonds 3 unit channels (0.6 MHz): 3 lower + 2 upper = 5 slots.
        val shortTurbo = lora(RegionCode.JP, ModemPreset.SHORT_TURBO)
        assertEquals(5, shortTurbo.numChannels)
        assertEquals(1, shortTurbo.channelNum("ShortTurbo"))
        val expected = listOf(920.8f, 921.4f, 922.0f, 922.6f, 923.2f)
        expected.forEachIndexed { index, freq -> assertEquals(freq, shortTurbo.radioFreq(index + 1), 0.001f) }
    }

    @Test
    fun jp_custom_bandwidths_and_overrides_follow_unit_channel_grid() {
        // 203.125 kHz (wire code 200) exceeds 1 unit channel (200 kHz) so it bonds 2 units (400 kHz -> 7 slots).
        val bw203 = customLora(RegionCode.JP, bandwidthCode = 200)
        assertEquals(7, bw203.numChannels)
        assertEquals(920.7f, bw203.radioFreq(1), 0.001f)
        assertEquals(922.5f, bw203.radioFreq(5), 0.001f)

        // 406.25 kHz (wire code 400) exceeds 2 unit channels (400 kHz) so it bonds 3 units (600 kHz -> 5 slots).
        val bw406 = customLora(RegionCode.JP, bandwidthCode = 400)
        assertEquals(5, bw406.numChannels)
        assertEquals(920.8f, bw406.radioFreq(1), 0.001f)
        assertEquals(922.6f, bw406.radioFreq(4), 0.001f)

        val narrowSlow = lora(RegionCode.JP, ModemPreset.NARROW_SLOW)
        val offset = narrowSlow.newBuilder().also { wb -> wb.frequency_offset = 0.01f }.build()
        assertEquals(920.81f, offset.radioFreq(2), 0.001f)

        val overrideFreq = narrowSlow.newBuilder().also { wb -> wb.override_frequency = 922.7f }.build()
        assertEquals(922.7f, overrideFreq.radioFreq(2), 0.001f)

        // Slot 0 must clamp safely to slot 1 instead of underflowing below freqStart.
        assertEquals(920.7f, lora(RegionCode.JP, ModemPreset.LONG_FAST).radioFreq(0), 0.001f)
    }

    @Test
    fun jp_slots_stay_within_band_edges_and_never_straddle_922_3_MHz() {
        val jpPresets =
            listOf(ModemPreset.NARROW_SLOW, ModemPreset.LITE_FAST, ModemPreset.LONG_FAST, ModemPreset.SHORT_TURBO)
        for (preset in jpPresets) {
            val cfg = lora(RegionCode.JP, preset)
            val halfBw = requireNotNull(ChannelOption.from(preset)).bandwidth * 0.5f
            var prevUpper = 0f
            for (slot in 1..cfg.numChannels) {
                val center = cfg.radioFreq(slot)
                val lo = center - halfBw
                val hi = center + halfBw
                assertTrue(lo >= 920.5f - 1e-4f, "$preset slot $slot lower edge $lo < 920.5")
                assertTrue(hi <= 923.5f + 1e-4f, "$preset slot $slot upper edge $hi > 923.5")
                if (slot > 1) {
                    assertTrue(lo >= prevUpper - 1e-4f, "$preset slot $slot overlaps previous ($lo < $prevUpper)")
                }
                assertTrue(
                    hi <= 922.3f + 1e-4f || lo >= 922.3f - 1e-4f,
                    "$preset slot $slot [$lo, $hi] straddles 922.3 MHz",
                )
                prevUpper = hi
            }
        }
    }
}
