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
        // ITU1_2M: 144-146 MHz, TINY_FAST (firmware's 15.6 kHz) padded to 20 kHz slots -> 100 slots, default slot 26.
        // The tolerance is tight enough to reject 15.625 kHz, which drifts 25 Hz per slot.
        val itu1 = lora(RegionCode.ITU1_2M, ModemPreset.TINY_FAST)
        assertEquals(100, itu1.numChannels)
        assertEquals(26, itu1.channelNum("TinyFast"))
        assertEquals(144.51f, itu1.radioFreq(26), 0.0001f)
        assertEquals(145.99f, itu1.radioFreq(100), 0.0001f)

        // ITU2_2M: 144-148 MHz -> 200 slots, default slot 51 (145.010 MHz).
        val itu2 = lora(RegionCode.ITU2_2M, ModemPreset.TINY_FAST)
        assertEquals(200, itu2.numChannels)
        assertEquals(145.01f, itu2.radioFreq(itu2.channelNum("TinyFast")), 0.0001f)

        // ITU3_2M: 144-148 MHz -> 200 slots, default slot 33 (144.650 MHz).
        val itu3 = lora(RegionCode.ITU3_2M, ModemPreset.TINY_FAST)
        assertEquals(200, itu3.numChannels)
        assertEquals(144.65f, itu3.radioFreq(itu3.channelNum("TinyFast")), 0.0001f)
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
    fun wide_lora_widens_only_presets_with_a_wide_variant() {
        // LORA_24: 2400-2483.5 MHz. LONG_FAST runs at 812.5 kHz there -> 103 slots.
        assertEquals(103, lora(RegionCode.LORA_24, ModemPreset.LONG_FAST).numChannels)
        // NARROW_FAST keeps 62.5 kHz on a wide region -> 1336 slots, not 411 at 203.125 kHz.
        assertEquals(1336, lora(RegionCode.LORA_24, ModemPreset.NARROW_FAST).numChannels)
        assertEquals(1336, lora(RegionCode.LORA_24, ModemPreset.NARROW_SLOW).numChannels)
        // LITE keeps 125 kHz -> 668 slots, and TINY keeps 15.6 kHz -> 5353.
        assertEquals(668, lora(RegionCode.LORA_24, ModemPreset.LITE_FAST).numChannels)
        assertEquals(668, lora(RegionCode.LORA_24, ModemPreset.LITE_SLOW).numChannels)
        assertEquals(5353, lora(RegionCode.LORA_24, ModemPreset.TINY_FAST).numChannels)
        assertEquals(5353, lora(RegionCode.LORA_24, ModemPreset.TINY_SLOW).numChannels)
    }

    @Test
    fun out_of_range_channel_num_falls_back_like_firmware() {
        // US LONG_FAST has 104 slots: slot 500 is ignored and the name hash picks slot 20.
        assertEquals(20, lora(RegionCode.US, ModemPreset.LONG_FAST, channelNum = 500).channelNum("LongFast"))
        // ITU1_2M has 100 slots: slot 101 is ignored and the region's override slot 26 applies.
        assertEquals(26, lora(RegionCode.ITU1_2M, ModemPreset.TINY_FAST, channelNum = 101).channelNum("TinyFast"))
    }

    @Test
    fun override_slot_past_the_slot_count_falls_back_to_the_name_hash() {
        // ITU2_70CM at a custom 500 kHz has 56 slots, so its override slot 137 cannot apply.
        val wide =
            lora(RegionCode.ITU2_70CM, ModemPreset.NARROW_SLOW)
                .newBuilder()
                .also { wb ->
                    wb.use_preset = false
                    wb.bandwidth = 500
                }
                .build()
        assertEquals(56, wide.numChannels)
        assertEquals(52, wide.channelNum("Ham"))
    }

    private fun custom(region: RegionCode, bandwidthCode: Int) = LoRaConfig.Builder()
        .also { wb ->
            wb.use_preset = false
            wb.region = region
            wb.bandwidth = bandwidthCode
        }
        .build()

    @Test
    fun fractional_bandwidth_codes_decode_like_firmware() {
        // Firmware bwCodeToKHz: 8, 10 and 16 stand for 7.8, 10.4 and 15.6 kHz over US's 26 MHz.
        assertEquals(3333, custom(RegionCode.US, 8).numChannels)
        assertEquals(2500, custom(RegionCode.US, 10).numChannels)
        assertEquals(1667, custom(RegionCode.US, 16).numChannels)
        // 21 and 42 stand for 20.8 and 41.7 kHz.
        assertEquals(1250, custom(RegionCode.US, 21).numChannels)
        assertEquals(624, custom(RegionCode.US, 42).numChannels)
        // ANZ (915-928 MHz) at 7.8 kHz: slot 1625 sits at 915 + 0.0039 + 1624 * 0.0078.
        assertEquals(927.6711f, custom(RegionCode.ANZ, 8).radioFreq(1625), 0.001f)
    }

    @Test
    fun custom_bandwidth_zero_runs_at_250_kHz() {
        assertEquals(104, custom(RegionCode.US, 0).numChannels)
    }

    @Test
    fun custom_bandwidth_wider_than_the_region_runs_at_the_region_default_preset() {
        // EU_N_868 is 250 kHz wide, so a custom 500 kHz runs as NARROW_SLOW does there: 3 slots, slot 1 at 869.4417.
        val tooWide = custom(RegionCode.EU_N_868, 500)
        assertEquals(3, tooWide.numChannels)
        assertEquals(869.4417f, tooWide.radioFreq(1), 0.001f)
        // EU_868 is as narrow and defaults to LONG_FAST: one 250 kHz slot.
        assertEquals(869.525f, custom(RegionCode.EU_868, 500).radioFreq(1), 0.001f)
        // EU_433 is 1 MHz wide: a custom 1 MHz just fits and stays custom, where the LONG_FAST default would give 4.
        assertEquals(1, custom(RegionCode.EU_433, 1000).numChannels)
    }
}
