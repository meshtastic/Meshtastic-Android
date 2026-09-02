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

        assertNull(beaconOfferChannelIndex(null, selectableBeaconChannels(channelList)))
    }

    @Test
    fun beaconOfferChannelIndex_matchByNameAndPsk_returnsIndex() {
        val channelList =
            listOf(
                ChannelSettings(name = "Primary", psk = "a".encodeUtf8()),
                ChannelSettings(name = "Secondary", psk = "b".encodeUtf8()),
            )
        val offer = ChannelSettings(name = "Secondary", psk = "b".encodeUtf8())

        assertEquals(1, beaconOfferChannelIndex(offer, selectableBeaconChannels(channelList)))
    }

    @Test
    fun beaconOfferChannelIndex_noRadioChannelMatches_returnsNull() {
        val channelList = listOf(ChannelSettings(name = "Primary", psk = "a".encodeUtf8()))
        val offer = ChannelSettings(name = "Stale", psk = "z".encodeUtf8())

        assertNull(beaconOfferChannelIndex(offer, selectableBeaconChannels(channelList)))
    }

    @Test
    fun beaconOfferChannelIndex_offerMatchesOnlyAPlaceholderSlot_returnsNull() {
        // A blank/empty offer's identity coincidentally matches the placeholder secondary in the RAW list; once
        // filtered through selectableBeaconChannels that slot is gone, so the match must not go through (design#140
        // Q2) -- otherwise the offer picker would silently select an excluded, never-rendered item.
        val channelList = listOf(ChannelSettings(name = "Primary", psk = "a".encodeUtf8()), ChannelSettings())
        val offer = ChannelSettings()

        assertNull(beaconOfferChannelIndex(offer, selectableBeaconChannels(channelList)))
    }

    @Test
    fun selectableBeaconChannels_placeholderSecondaryExcluded_followingRealSlotKeepsTrueIndex() {
        val channelList =
            listOf(
                ChannelSettings(name = "Primary", psk = "a".encodeUtf8()),
                ChannelSettings(), // placeholder secondary: blank name, empty psk
                ChannelSettings(name = "Real", psk = "b".encodeUtf8()),
            )

        val selectable = selectableBeaconChannels(channelList)

        assertEquals(listOf(0, 2), selectable.map { it.first })
    }

    @Test
    fun selectableBeaconChannels_blankPrimaryKept() {
        val channelList = listOf(ChannelSettings())

        val selectable = selectableBeaconChannels(channelList)

        assertEquals(listOf(0), selectable.map { it.first })
    }

    @Test
    fun selectableBeaconChannels_nameOnlySecondaryKept() {
        val channelList = listOf(ChannelSettings(name = "Primary"), ChannelSettings(name = "Named"))

        val selectable = selectableBeaconChannels(channelList)

        assertEquals(listOf(0, 1), selectable.map { it.first })
    }

    @Test
    fun selectableBeaconChannels_pskOnlySecondaryKept() {
        val channelList = listOf(ChannelSettings(name = "Primary"), ChannelSettings(psk = "b".encodeUtf8()))

        val selectable = selectableBeaconChannels(channelList)

        assertEquals(listOf(0, 1), selectable.map { it.first })
    }

    @Test
    fun selectableBeaconChannels_oneBytePskCleartextSentinelSecondaryKept() {
        // A raw ChannelSettings.psk of size 1 (firmware's cleartext sentinel is a single 0x00 byte) is not padding:
        // isChannelPlaceholder only treats size == 0 as placeholder.
        val channelList = listOf(ChannelSettings(name = "Primary"), ChannelSettings(psk = "\u0000".encodeUtf8()))

        val selectable = selectableBeaconChannels(channelList)

        assertEquals(listOf(0, 1), selectable.map { it.first })
    }

    @Test
    fun stampBeaconConfigForSave_stampsRegionAndPresetOnConfigAndTargets() {
        val radioLora =
            Config.LoRaConfig(region = RegionCode.EU_868, modem_preset = ModemPreset.MEDIUM_FAST, use_preset = true)
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

        val stamped = stampBeaconConfigForSave(config, config, radioLora, channelList = emptyList())

        assertEquals(RegionCode.EU_868, stamped.broadcast_offer_region)
        assertEquals(ModemPreset.MEDIUM_FAST, stamped.broadcast_offer_preset)
        assertTrue(stamped.broadcast_targets.all { it.region == RegionCode.EU_868 })
        // Preset stamping applies to the offer, not to individual target rows: a target's own preset survives.
        assertEquals(ModemPreset.SHORT_FAST, stamped.broadcast_targets[1].preset)
    }

    @Test
    fun stampBeaconConfigForSave_defaultTargetRow_nullFieldsSurviveButRegionIsStamped() {
        // A seeded/"Default" row (null channel_index, null preset) must reach the outgoing ModuleConfig with those
        // fields still null -- that's the whole point of the sentinel, matching firmware's own "unset falls back to
        // running config" semantics (module_config.proto). Region is the one field every target always gets, per
        // save-time stamping (behavior 1), regardless of the row's own null fields.
        val radioLora =
            Config.LoRaConfig(region = RegionCode.EU_868, modem_preset = ModemPreset.MEDIUM_FAST, use_preset = true)
        val config = MeshBeaconConfig(broadcast_targets = listOf(MeshBeaconConfig.BroadcastTarget()))

        val stamped = stampBeaconConfigForSave(config, config, radioLora, channelList = emptyList())

        val target = stamped.broadcast_targets.single()
        assertNull(target.channel_index)
        assertNull(target.preset)
        assertEquals(RegionCode.EU_868, target.region)
    }

    @Test
    fun stampBeaconConfigForSave_untouchedOfferChannel_defaultsToPrimary() {
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST, use_preset = true)
        val primary = ChannelSettings(name = "Primary", psk = "a".encodeUtf8())
        val config = MeshBeaconConfig(broadcast_offer_channel = null)

        val stamped = stampBeaconConfigForSave(config, config, radioLora, channelList = listOf(primary))

        assertEquals("Primary", stamped.broadcast_offer_channel?.name)
        assertEquals("a".encodeUtf8(), stamped.broadcast_offer_channel?.psk)
    }

    @Test
    fun stampBeaconConfigForSave_alreadySetOfferChannel_isKeptEvenIfStale() {
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST, use_preset = true)
        val stale = ChannelSettings(name = "Stale", psk = "z".encodeUtf8())
        val config = MeshBeaconConfig(broadcast_offer_channel = stale)

        val stamped =
            stampBeaconConfigForSave(config, config, radioLora, channelList = listOf(ChannelSettings(name = "Primary")))

        assertEquals("Stale", stamped.broadcast_offer_channel?.name)
        assertEquals("z".encodeUtf8(), stamped.broadcast_offer_channel?.psk)
    }

    @Test
    fun stampBeaconConfigForSave_onlyNameAndPskCarryOverToOfferChannel() {
        // A beacon invites a stranger's radio to join this channel: the radio's own index/id/uplink/downlink/module
        // flags have no meaning there and must never leak onto someone else's node.
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST, use_preset = true)
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

        val stamped = stampBeaconConfigForSave(config, config, radioLora, channelList = listOf(fullChannel))

        assertEquals(ChannelSettings(name = "Primary", psk = "a".encodeUtf8()), stamped.broadcast_offer_channel)
    }

    @Test
    fun stampBeaconConfigForSave_customParams_preservesStoredBroadcastFieldsVerbatim() {
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST, use_preset = false)
        val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
        val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
        val stored =
            MeshBeaconConfig(
                flags = listenFlag or broadcastFlag,
                broadcast_message = "Stored message",
                broadcast_interval_secs = 3600,
                broadcast_offer_region = RegionCode.EU_868,
                broadcast_offer_preset = ModemPreset.MEDIUM_FAST,
                broadcast_offer_channel = ChannelSettings(name = "Stored", psk = "s".encodeUtf8()),
                broadcast_targets =
                listOf(MeshBeaconConfig.BroadcastTarget(region = RegionCode.EU_868, channel_index = 1)),
            )
        // The form differs on every broadcast field, plus clears the BROADCAST flag (the only edit the gated UI
        // actually allows) -- none of the broadcast field edits should survive the save.
        val form =
            stored.copy(
                flags = listenFlag,
                broadcast_message = "Edited but discarded",
                broadcast_interval_secs = 7200,
                broadcast_offer_region = RegionCode.JP,
                broadcast_offer_preset = ModemPreset.SHORT_FAST,
                broadcast_offer_channel = ChannelSettings(name = "Different"),
                broadcast_targets = emptyList(),
            )

        val stamped =
            stampBeaconConfigForSave(form, stored, radioLora, channelList = listOf(ChannelSettings(name = "Primary")))

        assertEquals(stored.broadcast_message, stamped.broadcast_message)
        assertEquals(stored.broadcast_interval_secs, stamped.broadcast_interval_secs)
        assertEquals(stored.broadcast_offer_region, stamped.broadcast_offer_region)
        assertEquals(stored.broadcast_offer_preset, stamped.broadcast_offer_preset)
        assertEquals(stored.broadcast_offer_channel, stamped.broadcast_offer_channel)
        assertEquals(stored.broadcast_targets, stamped.broadcast_targets)
        // Flags come from the form, not the stored config: the listen/broadcast toggle edits are honored.
        assertEquals(form.flags, stamped.flags)
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

    @Test
    fun selectBeaconTargetChannel_defaultSentinel_clearsChannelAndLeavesPresetUntouched() {
        val target = MeshBeaconConfig.BroadcastTarget(channel_index = 2, preset = null)

        val updated = selectBeaconTargetChannel(target, channelIndex = null, currentPreset = ModemPreset.SHORT_FAST)

        assertNull(updated.channel_index)
        // Picking "Default" is not "picking a channel" (design#140 behavior 7 only fires on a concrete channel).
        assertNull(updated.preset)
    }

    @Test
    fun selectBeaconTargetChannel_defaultSentinelWithConcretePreset_presetUnchanged() {
        // Same as the null-preset case above, but with a preset the user has already deliberately chosen -- the
        // regression this guards is "Default" silently resetting a concrete preset, not just leaving null alone.
        val target = MeshBeaconConfig.BroadcastTarget(channel_index = 2, preset = ModemPreset.SHORT_FAST)

        val updated = selectBeaconTargetChannel(target, channelIndex = null, currentPreset = ModemPreset.LONG_FAST)

        assertNull(updated.channel_index)
        assertEquals(ModemPreset.SHORT_FAST, updated.preset)
    }

    @Test
    fun seedBeaconTargets_emptyStoredList_seedsOneDefaultRow() {
        val seeded = seedBeaconTargets(emptyList())

        assertEquals(listOf(MeshBeaconConfig.BroadcastTarget()), seeded)
    }

    @Test
    fun seedBeaconTargets_nonEmptyStoredList_isUnchanged() {
        val stored = listOf(MeshBeaconConfig.BroadcastTarget(channel_index = 1, preset = ModemPreset.LONG_FAST))

        val seeded = seedBeaconTargets(stored)

        assertEquals(stored, seeded)
    }

    @Test
    fun initialBeaconFormState_emptyStoredConfig_seedsFormTargetsThroughTheProductionPath() {
        // Calls the exact function MeshBeaconConfigScreen calls to build formState's initial value -- not
        // seedBeaconTargets directly -- so this breaks if the screen's wiring to it ever comes apart.
        val loaded = MeshBeaconConfig(broadcast_message = "hi", broadcast_targets = emptyList())

        val initial = initialBeaconFormState(loaded)

        assertEquals(listOf(MeshBeaconConfig.BroadcastTarget()), initial.broadcast_targets)
        assertEquals("hi", initial.broadcast_message)
    }

    @Test
    fun initialBeaconFormState_nonEmptyStoredConfig_isUnchanged() {
        val stored = listOf(MeshBeaconConfig.BroadcastTarget(channel_index = 3))
        val loaded = MeshBeaconConfig(broadcast_targets = stored)

        val initial = initialBeaconFormState(loaded)

        assertEquals(stored, initial.broadcast_targets)
    }

    @Test
    fun removeBeaconTarget_removingOneOfSeveral_dropsOnlyThatRow() {
        val targets =
            listOf(
                MeshBeaconConfig.BroadcastTarget(channel_index = 0),
                MeshBeaconConfig.BroadcastTarget(channel_index = 1),
            )

        val updated = removeBeaconTarget(targets, index = 0)

        assertEquals(listOf(MeshBeaconConfig.BroadcastTarget(channel_index = 1)), updated)
    }

    @Test
    fun removeBeaconTarget_removingTheOnlyRow_reseedsADefaultRowInstead() {
        val targets = listOf(MeshBeaconConfig.BroadcastTarget(channel_index = 4, preset = ModemPreset.SHORT_FAST))

        val updated = removeBeaconTarget(targets, index = 0)

        assertEquals(listOf(MeshBeaconConfig.BroadcastTarget()), updated)
    }
}
