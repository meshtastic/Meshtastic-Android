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
package org.meshtastic.core.model.util

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.numChannels
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.LoRaConfig
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.MeshBeacon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MeshBeaconOfferTest {

    private val radioLora =
        LoRaConfig.Builder()
            .also { wb ->
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
                wb.region = RegionCode.US
                wb.channel_num = 0
            }
            .build()
    private val radioChannels = listOf(ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build())

    @Test
    fun `no offer channel yields NONE`() {
        val beacon = MeshBeacon.Builder().also { wb -> wb.message = "hi" }.build()
        assertEquals(BeaconJoinOption.NONE, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `matching preset region and slot yields ADD`() {
        // Offering the radio's own primary channel name forces an identical name-hash slot → addable with no reboot.
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        assertEquals(BeaconJoinOption.ADD, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `unnamed primary matches a beacon that names the preset explicitly yielding ADD`() {
        // The radio's primary has an empty name (resolves to the preset display name "LongFast" for the slot hash); a
        // beacon offering a channel literally named "LongFast" on the same preset+region is the same slot -> ADD.
        // Without effective-name resolution the empty primary would hash "" and misclassify as SWITCH.
        val emptyPrimary = listOf(ChannelSettings.Builder().also { wb -> wb.name = "" }.build())
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "LongFast" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        assertEquals(BeaconJoinOption.ADD, beacon.beaconJoinOption(radioLora, emptyPrimary))
    }

    @Test
    fun `different preset forces SWITCH`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build()
                    wb.offer_preset = ModemPreset.SHORT_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        assertEquals(BeaconJoinOption.SWITCH, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `different region forces SWITCH`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.EU_868
                }
                .build()
        assertEquals(BeaconJoinOption.SWITCH, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `null lora config forces SWITCH`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb -> wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build() }
                .build()
        assertEquals(
            BeaconJoinOption.SWITCH,
            beacon.beaconJoinOption(currentLora = null, currentChannels = emptyList()),
        )
    }

    @Test
    fun `toJoinChannelSet omits lora for ADD and includes it for SWITCH`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        assertNull(beacon.toJoinChannelSet(BeaconJoinOption.ADD, radioLora)?.lora_config, "ADD must not retune")
        assertNotNull(beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, radioLora)?.lora_config, "SWITCH carries lora")
        assertNull(beacon.toJoinChannelSet(BeaconJoinOption.NONE, radioLora))
    }

    @Test
    fun `SWITCH ships a fresh lora config so stale RF pins never survive the retune`() {
        // The join must NOT copy the current config: a stale channel_num / override / manual preset would strand the
        // radio on the old slot. use_preset is set, the offered preset+region applied, and every RF field left blank.
        val current =
            radioLora
                .newBuilder()
                .also { wb ->
                    wb.modem_preset = ModemPreset.MEDIUM_FAST
                    wb.region = RegionCode.EU_868
                    wb.hop_limit = 7
                    wb.tx_power = 27
                    wb.tx_enabled = true
                    wb.channel_num = 5
                    wb.override_frequency = 915.5f
                }
                .build()
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        val lora = beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, current)?.lora_config
        assertNotNull(lora)
        assertEquals(true, lora.use_preset, "use_preset must be set")
        assertEquals(ModemPreset.LONG_FAST, lora.modem_preset, "offered preset applied")
        assertEquals(RegionCode.US, lora.region, "offered region applied")
        assertEquals(0, lora.channel_num, "channel_num reset to 0 for frequency derivation")
        assertEquals(0f, lora.override_frequency, "stale override_frequency dropped — firmware re-derives it")
        assertEquals(0, lora.tx_power, "stale tx_power dropped — firmware picks the region max")
        assertEquals(3, lora.hop_limit, "hop_limit falls back to the standard default, not the stale value")
    }

    @Test
    fun `SWITCH without an offered preset keeps the current preset and region and resets channel_num`() {
        // A channel-only beacon (no preset) must still reset channel_num=0 so firmware re-derives the frequency from
        // the new primary name, keep the current preset, and carry the current region (a zero region disables TX).
        val current =
            radioLora
                .newBuilder()
                .also { wb ->
                    wb.modem_preset = ModemPreset.MEDIUM_FAST
                    wb.channel_num = 5
                }
                .build()
        val beacon =
            MeshBeacon.Builder()
                .also { wb -> wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build() }
                .build()
        val lora = beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, current)?.lora_config
        assertNotNull(lora, "SWITCH must always carry lora so channel_num resets")
        assertEquals(0, lora.channel_num, "channel_num reset to 0 even without an offered preset")
        assertEquals(ModemPreset.MEDIUM_FAST, lora.modem_preset, "current preset kept when none offered")
        assertEquals(RegionCode.US, lora.region, "current region carried when none offered")
    }

    @Test
    fun `join strips position sharing from the offered channel`() {
        // Privacy: joining a stranger's mesh must never broadcast our location (Apple sets positionPrecision=0).
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel =
                        ChannelSettings.Builder()
                            .also { wb ->
                                wb.name = "PartyNet"
                                wb.module_settings =
                                    org.meshtastic.proto.ModuleSettings.Builder()
                                        .also { wb -> wb.position_precision = 32 }
                                        .build()
                            }
                            .build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.US
                }
                .build()
        val added = beacon.toJoinChannelSet(BeaconJoinOption.ADD, radioLora)?.settings?.first()
        val switched = beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, radioLora)?.settings?.first()
        assertEquals(0, added?.module_settings?.position_precision, "ADD zeroes position precision")
        assertEquals(0, switched?.module_settings?.position_precision, "SWITCH zeroes position precision")
    }

    @Test
    fun `encode then decode round-trips a beacon offer`() {
        val offer =
            MeshBeaconOffer(
                fromNodeNum = 42,
                beacon =
                MeshBeacon.Builder()
                    .also { wb ->
                        wb.message = "Join us"
                        wb.offer_channel =
                            ChannelSettings.Builder()
                                .also { wb ->
                                    wb.name = "PartyNet"
                                    wb.psk = "secret".encodeUtf8()
                                }
                                .build()
                        wb.offer_preset = ModemPreset.LONG_FAST
                        wb.offer_region = RegionCode.US
                    }
                    .build(),
                snr = 6.5f,
                rssi = -70,
            )
        val restored = MeshBeaconOffer.decode(offer.encode())
        assertEquals(offer, restored)
    }

    @Test
    fun `encode then decode round-trips an absent rssi without collapsing it to zero`() {
        val offer =
            MeshBeaconOffer(
                fromNodeNum = 42,
                beacon =
                MeshBeacon.Builder()
                    .also { wb ->
                        wb.message = "Join us"
                        wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()
                    }
                    .build(),
                snr = 6.5f,
                rssi = null,
            )
        val restored = MeshBeaconOffer.decode(offer.encode())
        assertEquals(offer, restored)
        assertNull(restored?.rssi)
    }

    @Test
    fun `decode returns null for a malformed record`() {
        assertNull(MeshBeaconOffer.decode("not-a-valid-record"))
    }

    @Test
    fun `offer matching a configured channel by name and psk is already joined`() {
        val configured =
            listOf(
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.name = "PartyNet"
                        wb.psk = "secret".encodeUtf8()
                    }
                    .build(),
            )
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel =
                        ChannelSettings.Builder()
                            .also { wb ->
                                wb.name = "PartyNet"
                                wb.psk = "secret".encodeUtf8()
                            }
                            .build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(true, beacon.isAlreadyJoined(radioLora, configured))
    }

    @Test
    fun `offer for a channel not configured on the radio is not already joined`() {
        val configured = listOf(ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build())
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "PartyNet" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(false, beacon.isAlreadyJoined(radioLora, configured))
    }

    @Test
    fun `same name but different psk is a different mesh and is not already joined`() {
        // Same name under a different key is deliberately NOT a match.
        val configured =
            listOf(
                ChannelSettings.Builder()
                    .also { wb ->
                        wb.name = "PartyNet"
                        wb.psk = "secret".encodeUtf8()
                    }
                    .build(),
            )
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel =
                        ChannelSettings.Builder()
                            .also { wb ->
                                wb.name = "PartyNet"
                                wb.psk = "different".encodeUtf8()
                            }
                            .build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(false, beacon.isAlreadyJoined(radioLora, configured))
    }

    @Test
    fun `empty-name primary channel matches an offer naming the preset display name`() {
        // The radio's primary has a blank name, which resolves to the preset display name for identity purposes.
        val configured = listOf(ChannelSettings.Builder().also { wb -> wb.name = "" }.build())
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "LongFast" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(true, beacon.isAlreadyJoined(radioLora, configured))
    }

    @Test
    fun `default 1-byte psk shorthand matches its expanded full-length equivalent`() {
        // Both sides resolve to the same expanded default key bytes even though one is the 1-byte shorthand.
        val expandedDefaultKey =
            ChannelSettings.Builder()
                .also { wb ->
                    wb.name = "HomeMesh"
                    wb.psk = byteArrayOf(1).toByteString()
                }
                .build()
                .let { it.newBuilder().also { wb -> wb.psk = Channel(it, radioLora).psk }.build() }
        val configured = listOf(expandedDefaultKey)
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel =
                        ChannelSettings.Builder()
                            .also { wb ->
                                wb.name = "HomeMesh"
                                wb.psk = byteArrayOf(1).toByteString()
                            }
                            .build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(true, beacon.isAlreadyJoined(radioLora, configured))
    }

    @Test
    fun `isAlreadyJoined returns false when lora config is unknown so a real invitation is never hidden`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb -> wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build() }
                .build()
        assertEquals(false, beacon.isAlreadyJoined(currentLora = null, currentChannels = radioChannels))
    }

    @Test
    fun `isAlreadyJoined returns false when no channels are configured`() {
        val beacon =
            MeshBeacon.Builder()
                .also { wb -> wb.offer_channel = ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build() }
                .build()
        assertEquals(false, beacon.isAlreadyJoined(currentLora = radioLora, currentChannels = emptyList()))
    }

    @Test
    fun `isAlreadyJoined returns false for a beacon with no offer channel`() {
        val beacon = MeshBeacon.Builder().also { wb -> wb.message = "hi" }.build()
        assertEquals(false, beacon.isAlreadyJoined(radioLora, radioChannels))
    }

    @Test
    fun `blank-name no-psk secondary placeholder slot is not mistaken for an already-joined channel`() {
        // SwitchingChannelSetDataSource pads a gap left by a removed channel with a bare ChannelSettings(); a beacon
        // offering a genuinely blank cleartext channel on the same preset must not match that padding.
        val configured =
            listOf(
                ChannelSettings.Builder().also { wb -> wb.name = "HomeMesh" }.build(),
                ChannelSettings.Builder().build(),
            )
        val beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.offer_channel = ChannelSettings.Builder().build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                }
                .build()
        assertEquals(false, beacon.isAlreadyJoined(radioLora, configured))
    }

    private fun slotOf(name: String, lora: LoRaConfig = radioLora): Int =
        Channel(ChannelSettings.Builder().also { wb -> wb.name = name }.build(), lora).channelNum

    private fun offerOn(name: String, slot: Int? = null): MeshBeacon = MeshBeacon.Builder()
        .also { wb ->
            wb.offer_channel = ChannelSettings.Builder().also { cb -> cb.name = name }.build()
            wb.offer_preset = ModemPreset.LONG_FAST
            wb.offer_region = RegionCode.US
            wb.offer_frequency_slot = slot
        }
        .build()

    @Test
    fun `an advertised slot is honoured over the offered channel name hash`() {
        // A mesh that pins a slot its channel name does not hash to can only be described by the advertised slot.
        val homeSlot = slotOf("HomeMesh")
        val offered = generateSequence(0) { it + 1 }.map { "PinnedMesh$it" }.first { slotOf(it) != homeSlot }
        assertEquals(BeaconJoinOption.ADD, offerOn(offered, slot = homeSlot).beaconJoinOption(radioLora, radioChannels))
        // Same offer without the slot derives elsewhere, so it is only addable because the slot was advertised.
        assertEquals(BeaconJoinOption.SWITCH, offerOn(offered).beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `an advertised slot away from ours forces SWITCH even when the names hash alike`() {
        // The NYMesh case: the offered name hashes to our slot, but the mesh actually sits elsewhere. Deriving would
        // call this a no-reboot ADD and the user would join and hear nothing.
        val homeSlot = slotOf("HomeMesh")
        val elsewhere = if (homeSlot < radioLora.numChannels) homeSlot + 1 else homeSlot - 1
        assertEquals(
            BeaconJoinOption.SWITCH,
            offerOn("HomeMesh", slot = elsewhere).beaconJoinOption(radioLora, radioChannels),
        )
    }

    @Test
    fun `a pinned radio compares against an advertised slot instead of refusing to reason`() {
        val pinned = radioLora.newBuilder().also { wb -> wb.channel_num = 48 }.build()
        assertEquals(BeaconJoinOption.ADD, offerOn("AnyName", slot = 48).beaconJoinOption(pinned, radioChannels))
        assertEquals(BeaconJoinOption.SWITCH, offerOn("AnyName", slot = 49).beaconJoinOption(pinned, radioChannels))
        // With nothing advertised there is still nothing to compare a pin against.
        assertEquals(BeaconJoinOption.SWITCH, offerOn("HomeMesh").beaconJoinOption(pinned, radioChannels))
    }

    @Test
    fun `an advertised slot outside the region falls back to the name hash`() {
        // Unaddressable here, so behave as though the field were absent rather than refuse an advisory offer.
        assertEquals(
            BeaconJoinOption.ADD,
            offerOn("HomeMesh", slot = radioLora.numChannels + 1).beaconJoinOption(radioLora, radioChannels),
        )
        assertEquals(BeaconJoinOption.ADD, offerOn("HomeMesh", slot = 0).beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `a switch carries the advertised slot into channel_num`() {
        val beacon = offerOn("PinnedMesh", slot = 48)
        val set = assertNotNull(beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, radioLora))
        assertEquals(48, assertNotNull(set.lora_config).channel_num)
    }

    @Test
    fun `a switch with no advertised slot leaves channel_num zero for firmware to derive`() {
        val set = assertNotNull(offerOn("PinnedMesh").toJoinChannelSet(BeaconJoinOption.SWITCH, radioLora))
        assertEquals(0, assertNotNull(set.lora_config).channel_num)
    }
}
