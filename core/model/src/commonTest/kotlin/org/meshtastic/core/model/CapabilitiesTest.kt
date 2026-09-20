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

import org.meshtastic.proto.FieldMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CapabilitiesTest {

    private fun caps(version: String?) = Capabilities(version, forceEnableAll = false)

    @Test
    fun canMuteNode_requires_V2_7_18() {
        assertFalse(caps("2.7.15").canMuteNode)
        assertTrue(caps("2.7.18").canMuteNode)
        assertTrue(caps("2.8.0").canMuteNode)
    }

    @Test
    fun canRequestNeighborInfo_is_currently_disabled() {
        assertFalse(caps("2.7.14").canRequestNeighborInfo)
        assertFalse(caps("3.0.0").canRequestNeighborInfo)
    }

    @Test
    fun supportsLockdown_requires_V2_8_0() {
        assertFalse(caps("2.7.21").supportsLockdown)
        assertTrue(caps("2.8.0").supportsLockdown)
    }

    @Test
    fun canSendVerifiedContacts_requires_V2_7_12() {
        assertFalse(caps("2.7.11").canSendVerifiedContacts)
        assertTrue(caps("2.7.12").canSendVerifiedContacts)
    }

    @Test
    fun canToggleUnmessageable_requires_V2_6_9() {
        assertFalse(caps("2.6.8").canToggleUnmessageable)
        assertTrue(caps("2.6.9").canToggleUnmessageable)
    }

    @Test
    fun supportsQrCodeSharing_requires_V2_6_8() {
        assertFalse(caps("2.6.7").supportsQrCodeSharing)
        assertTrue(caps("2.6.8").supportsQrCodeSharing)
    }

    @Test
    fun supportsSecondaryChannelLocation_requires_V2_6_10() {
        assertFalse(caps("2.6.9").supportsSecondaryChannelLocation)
        assertTrue(caps("2.6.10").supportsSecondaryChannelLocation)
    }

    @Test
    fun supportsStatusMessage_requires_V2_7_20() {
        assertFalse(caps("2.7.19").supportsStatusMessage)
        assertTrue(caps("2.7.20").supportsStatusMessage)
    }

    @Test
    fun offers_hides_a_field_below_since_firmware() {
        val field = FieldMetadata.Builder().since_firmware("2.7.13").build()
        assertFalse(caps("2.7.12").offers(field))
        assertTrue(caps("2.7.13").offers(field))
        assertFalse(caps(null).offers(field))
    }

    @Test
    fun offers_hides_a_deprecated_field_at_deprecated_since_unless_set() {
        val field = FieldMetadata.Builder().deprecated_since("2.8.0").build()
        assertTrue(caps("2.7.26").offers(field))
        assertFalse(caps("2.8.0").offers(field))
        assertTrue(caps("2.8.0").offers(field, isSet = true))
        // Unknown firmware cannot be shown to have retired the field.
        assertTrue(caps(null).offers(field))
    }

    @Test
    fun offers_an_unannotated_field_everywhere() {
        val field = FieldMetadata.Builder().build()
        assertTrue(caps(null).offers(field))
        assertTrue(caps("2.3.15").offers(field))
    }

    @Test
    fun offers_everything_when_forceEnableAll() {
        val c = Capabilities(firmwareVersion = null, forceEnableAll = true)
        assertTrue(c.offers(FieldMetadata.Builder().since_firmware("9.9.9").build()))
        assertTrue(c.offers(FieldMetadata.Builder().deprecated_since("1.0.0").build()))
    }

    @Test
    fun offers_rejects_an_unparseable_schema_version() {
        val field = FieldMetadata.Builder().since_firmware("soon").build()
        assertFailsWith<IllegalArgumentException> { caps("2.8.0").offers(field) }
    }

    @Test
    fun supportsTakConfig_requires_V2_8_0() {
        assertFalse(caps("2.7.19").supportsTakConfig)
        assertFalse(caps("2.7.26").supportsTakConfig)
        assertTrue(caps("2.8.0").supportsTakConfig)
    }

    @Test
    fun supportsEsp32Ota_requires_V2_7_18() {
        assertFalse(caps("2.7.17").supportsEsp32Ota)
        assertTrue(caps("2.7.18").supportsEsp32Ota)
    }

    @Test
    fun supportsRegion_gates_2_8_regions_but_not_established_ones() {
        val old = caps("2.7.21")
        val new = caps("2.8.0")
        val gated = RegionInfo.entries.filter { it.minFirmware != null }
        assertEquals(
            setOf(
                RegionInfo.EU_866,
                RegionInfo.EU_N_868,
                RegionInfo.ITU1_2M,
                RegionInfo.ITU2_2M,
                RegionInfo.ITU3_2M,
                RegionInfo.ITU2_125CM,
                RegionInfo.ITU1_70CM,
                RegionInfo.ITU2_70CM,
                RegionInfo.ITU3_70CM,
            ),
            gated.toSet(),
        )
        gated.forEach { region ->
            assertFalse(old.supportsRegion(region), "${region.name} should be hidden on 2.7 firmware")
            assertTrue(new.supportsRegion(region), "${region.name} should be shown on 2.8 firmware")
        }
        // Established regions are never gated, even with unknown firmware.
        assertTrue(caps(null).supportsRegion(RegionInfo.US))
        assertTrue(old.supportsRegion(RegionInfo.UNSET))
        // Unknown firmware hides gated regions; debug forceEnableAll shows them.
        assertFalse(caps(null).supportsRegion(RegionInfo.ITU1_2M))
        assertTrue(Capabilities(firmwareVersion = null, forceEnableAll = true).supportsRegion(RegionInfo.ITU1_2M))
    }

    @Test
    fun supportsPreset_gates_2_8_presets_but_not_established_ones() {
        // LITE/NARROW enum values were vendored into v2.7.23 protobufs, but their radio support
        // (modemPresetToParams cases — firmware#10120) ships in 2.8 like TINY and MEDIUM_TURBO.
        val gated =
            setOf(
                ChannelOption.TINY_FAST,
                ChannelOption.TINY_SLOW,
                ChannelOption.MEDIUM_TURBO,
                ChannelOption.LITE_FAST,
                ChannelOption.LITE_SLOW,
                ChannelOption.NARROW_FAST,
                ChannelOption.NARROW_SLOW,
            )
        // LongTurbo's radio support lands earlier, in v2.7.17 (firmware#8985), so it gates on its own version.
        assertEquals(gated + ChannelOption.LONG_TURBO, ChannelOption.entries.filter { it.minFirmware != null }.toSet())

        val old = caps("2.7.26")
        val new = caps("2.8.0")
        gated.forEach { preset ->
            assertFalse(old.supportsPreset(preset), "${preset.name} should be hidden on 2.7 firmware")
            assertTrue(new.supportsPreset(preset), "${preset.name} should be shown on 2.8 firmware")
        }
        assertFalse(caps("2.7.16").supportsPreset(ChannelOption.LONG_TURBO), "LongTurbo predates 2.7.17")
        assertTrue(old.supportsPreset(ChannelOption.LONG_TURBO), "LongTurbo ships from 2.7.17")
        // Established presets are never gated, even with unknown firmware.
        assertTrue(caps(null).supportsPreset(ChannelOption.LONG_FAST))
        // Unknown firmware hides gated presets; debug forceEnableAll shows them.
        assertFalse(caps(null).supportsPreset(ChannelOption.MEDIUM_TURBO))
        assertTrue(
            Capabilities(firmwareVersion = null, forceEnableAll = true).supportsPreset(ChannelOption.MEDIUM_TURBO),
        )
    }

    @Test
    fun nullFirmware_returns_all_false() {
        val c = caps(null)
        assertFalse(c.canMuteNode)
        assertFalse(c.canRequestNeighborInfo)
        assertFalse(c.canSendVerifiedContacts)
        assertFalse(c.canToggleUnmessageable)
        assertFalse(c.supportsQrCodeSharing)
        assertFalse(c.supportsSecondaryChannelLocation)
        assertFalse(c.supportsStatusMessage)
        assertFalse(c.supportsTakConfig)
        assertFalse(c.supportsEsp32Ota)
    }

    @Test
    fun forceEnableAll_returns_true_regardless_of_version() {
        val c = Capabilities(firmwareVersion = null, forceEnableAll = true)
        assertTrue(c.canMuteNode)
        assertTrue(c.canSendVerifiedContacts)
        assertTrue(c.supportsStatusMessage)
        assertTrue(c.supportsTakConfig)
    }
}
