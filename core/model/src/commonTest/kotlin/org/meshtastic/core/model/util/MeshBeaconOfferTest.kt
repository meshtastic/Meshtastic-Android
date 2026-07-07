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
import org.meshtastic.core.model.MeshBeaconOffer
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
        LoRaConfig(use_preset = true, modem_preset = ModemPreset.LONG_FAST, region = RegionCode.US, channel_num = 0)
    private val radioChannels = listOf(ChannelSettings(name = "HomeMesh"))

    @Test
    fun `no offer channel yields NONE`() {
        val beacon = MeshBeacon(message = "hi")
        assertEquals(BeaconJoinOption.NONE, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `matching preset region and slot yields ADD`() {
        // Offering the radio's own primary channel name forces an identical name-hash slot → addable with no reboot.
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "HomeMesh"),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.US,
            )
        assertEquals(BeaconJoinOption.ADD, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `unnamed primary matches a beacon that names the preset explicitly yielding ADD`() {
        // The radio's primary has an empty name (resolves to the preset display name "LongFast" for the slot hash); a
        // beacon offering a channel literally named "LongFast" on the same preset+region is the same slot -> ADD.
        // Without effective-name resolution the empty primary would hash "" and misclassify as SWITCH.
        val emptyPrimary = listOf(ChannelSettings(name = ""))
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "LongFast"),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.US,
            )
        assertEquals(BeaconJoinOption.ADD, beacon.beaconJoinOption(radioLora, emptyPrimary))
    }

    @Test
    fun `different preset forces SWITCH`() {
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "HomeMesh"),
                offer_preset = ModemPreset.SHORT_FAST,
                offer_region = RegionCode.US,
            )
        assertEquals(BeaconJoinOption.SWITCH, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `different region forces SWITCH`() {
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "HomeMesh"),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.EU_868,
            )
        assertEquals(BeaconJoinOption.SWITCH, beacon.beaconJoinOption(radioLora, radioChannels))
    }

    @Test
    fun `null lora config forces SWITCH`() {
        val beacon = MeshBeacon(offer_channel = ChannelSettings(name = "HomeMesh"))
        assertEquals(
            BeaconJoinOption.SWITCH,
            beacon.beaconJoinOption(currentLora = null, currentChannels = emptyList()),
        )
    }

    @Test
    fun `toJoinChannelSet omits lora for ADD and includes it for SWITCH`() {
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "PartyNet"),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.US,
            )
        assertNull(beacon.toJoinChannelSet(BeaconJoinOption.ADD, radioLora)?.lora_config, "ADD must not retune")
        assertNotNull(beacon.toJoinChannelSet(BeaconJoinOption.SWITCH, radioLora)?.lora_config, "SWITCH carries lora")
        assertNull(beacon.toJoinChannelSet(BeaconJoinOption.NONE, radioLora))
    }

    @Test
    fun `SWITCH ships a fresh lora config so stale RF pins never survive the retune`() {
        // The join must NOT copy the current config: a stale channel_num / override / manual preset would strand the
        // radio on the old slot. use_preset is set, the offered preset+region applied, and every RF field left blank.
        val current =
            radioLora.copy(
                modem_preset = ModemPreset.MEDIUM_FAST,
                region = RegionCode.EU_868,
                hop_limit = 7,
                tx_power = 27,
                tx_enabled = true,
                channel_num = 5,
                override_frequency = 915.5f,
            )
        val beacon =
            MeshBeacon(
                offer_channel = ChannelSettings(name = "PartyNet"),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.US,
            )
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
        val current = radioLora.copy(modem_preset = ModemPreset.MEDIUM_FAST, channel_num = 5)
        val beacon = MeshBeacon(offer_channel = ChannelSettings(name = "PartyNet"))
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
            MeshBeacon(
                offer_channel =
                ChannelSettings(
                    name = "PartyNet",
                    module_settings = org.meshtastic.proto.ModuleSettings(position_precision = 32),
                ),
                offer_preset = ModemPreset.LONG_FAST,
                offer_region = RegionCode.US,
            )
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
                MeshBeacon(
                    message = "Join us",
                    offer_channel = ChannelSettings(name = "PartyNet", psk = "secret".encodeUtf8()),
                    offer_preset = ModemPreset.LONG_FAST,
                    offer_region = RegionCode.US,
                ),
                snr = 6.5f,
                rssi = -70,
            )
        val restored = MeshBeaconOffer.decode(offer.encode())
        assertEquals(offer, restored)
    }

    @Test
    fun `decode returns null for a malformed record`() {
        assertNull(MeshBeaconOffer.decode("not-a-valid-record"))
    }
}
