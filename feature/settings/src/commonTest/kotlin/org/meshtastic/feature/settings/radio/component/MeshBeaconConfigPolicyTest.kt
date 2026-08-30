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
package org.meshtastic.feature.settings.radio.component

import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.feature.settings.util.FixedUpdateIntervals
import org.meshtastic.feature.settings.util.IntervalConfiguration
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LoRaRegionPresets
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshBeaconConfigPolicyTest {

    @Test
    fun beaconPresetConstraint_noMap_usesConservativeSet() {
        val constraint = beaconPresetConstraint(null, RegionCode.EU_868)

        assertEquals(CONSERVATIVE_BEACON_PRESETS, constraint.presets)
        assertTrue(ModemPreset.LONG_FAST in constraint.presets)
        assertEquals(false, constraint.licensedOnly)
    }

    @Test
    fun beaconPresetConstraint_mapPresent_usesFirmwareGroup() {
        val map =
            LoRaRegionPresetMap(
                groups =
                listOf(
                    LoRaPresetGroup(
                        presets = listOf(ModemPreset.SHORT_FAST, ModemPreset.SHORT_TURBO),
                        default_preset = ModemPreset.SHORT_FAST,
                        licensed_only = true,
                    ),
                ),
                region_groups = listOf(LoRaRegionPresets(region = RegionCode.US, group_index = 0)),
            )

        val constraint = beaconPresetConstraint(map, RegionCode.US)

        assertEquals(listOf(ModemPreset.SHORT_FAST, ModemPreset.SHORT_TURBO), constraint.presets)
        assertEquals(ModemPreset.SHORT_FAST, constraint.defaultPreset)
        assertTrue(constraint.licensedOnly)
    }

    @Test
    fun beaconPresetConstraint_mapPresentButRegionMissing_fallsBackToConservative() {
        val map = LoRaRegionPresetMap(groups = emptyList(), region_groups = emptyList())

        val constraint = beaconPresetConstraint(map, RegionCode.JP)

        assertEquals(CONSERVATIVE_BEACON_PRESETS, constraint.presets)
    }

    @Test
    fun beaconIntervalFallback_storedValueMatchesAllowed_returnsNull() {
        val allowed = IntervalConfiguration.MESH_BEACON_BROADCAST.allowedIntervals

        val fallback = beaconIntervalFallback(FixedUpdateIntervals.ONE_HOUR.value, allowed)

        assertNull(fallback)
    }

    @Test
    fun beaconIntervalFallback_neverConfiguredSentinelZero_isKeptVisible() {
        val allowed = IntervalConfiguration.MESH_BEACON_BROADCAST.allowedIntervals

        val fallback = beaconIntervalFallback(0L, allowed)

        assertEquals(0L, fallback)
    }

    @Test
    fun beaconIntervalFallback_nonstandardStoredValue_isKeptVisible() {
        val allowed = IntervalConfiguration.MESH_BEACON_BROADCAST.allowedIntervals

        val fallback = beaconIntervalFallback(5400L, allowed)

        assertEquals(5400L, fallback)
    }

    @Test
    fun beaconOfferChannelIndex_nullChannel_returnsNull() {
        val channelList = listOf(ChannelSettings(name = "Primary"))

        assertNull(beaconOfferChannelIndex(null, channelList))
    }

    @Test
    fun beaconOfferChannelIndex_matchByNameAndPsk_returnsIndex() {
        val channelList =
            listOf(
                ChannelSettings(name = "Primary", psk = "a".encodeUtf8()),
                ChannelSettings(name = "Secondary", psk = "b".encodeUtf8()),
            )
        val offer = ChannelSettings(name = "Secondary", psk = "b".encodeUtf8())

        assertEquals(1, beaconOfferChannelIndex(offer, channelList))
    }

    @Test
    fun beaconOfferChannelIndex_noRadioChannelMatches_returnsNull() {
        val channelList = listOf(ChannelSettings(name = "Primary", psk = "a".encodeUtf8()))
        val offer = ChannelSettings(name = "Stale", psk = "z".encodeUtf8())

        assertNull(beaconOfferChannelIndex(offer, channelList))
    }

    @Test
    fun stampBeaconConfigForSave_stampsRegionAndPresetOnConfigAndTargets() {
        val radioLora = Config.LoRaConfig(region = RegionCode.EU_868, modem_preset = ModemPreset.MEDIUM_FAST)
        val config =
            MeshBeaconConfig(
                broadcast_offer_region = RegionCode.US,
                broadcast_offer_preset = ModemPreset.LONG_FAST,
                broadcast_targets =
                listOf(
                    MeshBeaconConfig.BroadcastTarget(region = RegionCode.JP),
                    MeshBeaconConfig.BroadcastTarget(region = RegionCode.CN, preset = ModemPreset.SHORT_FAST),
                ),
            )

        val stamped = stampBeaconConfigForSave(config, radioLora, channelList = emptyList())

        assertEquals(RegionCode.EU_868, stamped.broadcast_offer_region)
        assertEquals(ModemPreset.MEDIUM_FAST, stamped.broadcast_offer_preset)
        assertTrue(stamped.broadcast_targets.all { it.region == RegionCode.EU_868 })
        // Preset stamping applies to the offer, not to individual target rows: a target's own preset survives.
        assertEquals(ModemPreset.SHORT_FAST, stamped.broadcast_targets[1].preset)
    }

    @Test
    fun stampBeaconConfigForSave_untouchedOfferChannel_defaultsToPrimary() {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST)
        val primary = ChannelSettings(name = "Primary", psk = "a".encodeUtf8())
        val config = MeshBeaconConfig(broadcast_offer_channel = null)

        val stamped = stampBeaconConfigForSave(config, radioLora, channelList = listOf(primary))

        assertEquals("Primary", stamped.broadcast_offer_channel?.name)
        assertEquals("a".encodeUtf8(), stamped.broadcast_offer_channel?.psk)
    }

    @Test
    fun stampBeaconConfigForSave_alreadySetOfferChannel_isKeptEvenIfStale() {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST)
        val stale = ChannelSettings(name = "Stale", psk = "z".encodeUtf8())
        val config = MeshBeaconConfig(broadcast_offer_channel = stale)

        val stamped =
            stampBeaconConfigForSave(config, radioLora, channelList = listOf(ChannelSettings(name = "Primary")))

        assertEquals("Stale", stamped.broadcast_offer_channel?.name)
        assertEquals("z".encodeUtf8(), stamped.broadcast_offer_channel?.psk)
    }

    @Test
    fun stampBeaconConfigForSave_onlyNameAndPskCarryOverToOfferChannel() {
        // A beacon invites a stranger's radio to join this channel: the radio's own index/id/uplink/downlink/module
        // flags have no meaning there and must never leak onto someone else's node.
        val radioLora = Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST)
        val fullChannel =
            ChannelSettings(
                name = "Primary",
                psk = "a".encodeUtf8(),
                channel_num = 5,
                id = 42,
                uplink_enabled = true,
                downlink_enabled = true,
            )
        val config = MeshBeaconConfig(broadcast_offer_channel = null)

        val stamped = stampBeaconConfigForSave(config, radioLora, channelList = listOf(fullChannel))

        assertEquals(ChannelSettings(name = "Primary", psk = "a".encodeUtf8()), stamped.broadcast_offer_channel)
    }

    @Test
    fun selectBeaconTargetChannel_noPresetYet_preselectsCurrentPreset() {
        val target = MeshBeaconConfig.BroadcastTarget(preset = null)

        val updated = selectBeaconTargetChannel(target, channelIndex = 2, currentPreset = ModemPreset.SHORT_FAST)

        assertEquals(2, updated.channel_index)
        assertEquals(ModemPreset.SHORT_FAST, updated.preset)
    }

    @Test
    fun selectBeaconTargetChannel_presetAlreadyChosen_isNotOverwritten() {
        val target = MeshBeaconConfig.BroadcastTarget(preset = ModemPreset.LONG_MODERATE)

        val updated = selectBeaconTargetChannel(target, channelIndex = 3, currentPreset = ModemPreset.SHORT_FAST)

        assertEquals(3, updated.channel_index)
        assertEquals(ModemPreset.LONG_MODERATE, updated.preset)
    }
}
