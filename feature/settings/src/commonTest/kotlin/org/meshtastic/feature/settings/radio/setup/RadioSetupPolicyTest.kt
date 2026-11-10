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
package org.meshtastic.feature.settings.radio.setup

import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.LoRaPresetGroup
import org.meshtastic.proto.LoRaRegionPresetMap
import org.meshtastic.proto.LoRaRegionPresets
import org.meshtastic.proto.MeshBeacon
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioSetupPolicyTest {

    private fun lora(
        region: RegionCode = RegionCode.UNSET,
        preset: ModemPreset = ModemPreset.LONG_FAST,
        usePreset: Boolean = true,
    ) = Config.LoRaConfig.Builder()
        .also { wb ->
            wb.region = region
            wb.modem_preset = preset
            wb.use_preset = usePreset
        }
        .build()

    private val usMap =
        LoRaRegionPresetMap.Builder()
            .also { wb ->
                wb.groups =
                    listOf(
                        LoRaPresetGroup.Builder()
                            .also { gb ->
                                gb.presets = listOf(ModemPreset.LONG_FAST, ModemPreset.LONG_TURBO)
                                gb.default_preset = ModemPreset.LONG_FAST
                            }
                            .build(),
                    )
                wb.region_groups =
                    listOf(
                        LoRaRegionPresets.Builder()
                            .also { rb ->
                                rb.region = RegionCode.US
                                rb.group_index = 0
                            }
                            .build(),
                    )
            }
            .build()

    @Test
    fun `licensed operators skip the name page`() {
        assertEquals(
            listOf(RadioSetupStep.INTRO, RadioSetupStep.REGION, RadioSetupStep.PRESET, RadioSetupStep.DONE),
            radioSetupSteps(isLicensed = true),
        )
        assertEquals(
            RadioSetupStep.entries - RadioSetupStep.BEACONS,
            radioSetupSteps(isLicensed = false, supportsBeacons = false),
        )
    }

    @Test
    fun `the nearby meshes page exists only with the mesh beacon module`() {
        assertEquals(RadioSetupStep.entries, radioSetupSteps(isLicensed = false, supportsBeacons = true))
        assertFalse(RadioSetupStep.BEACONS in radioSetupSteps(isLicensed = true, supportsBeacons = false))
    }

    @Test
    fun `the listen switch changes only the listen flag`() {
        val broadcast = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
        val stored =
            MeshBeaconConfig.Builder()
                .also { wb ->
                    wb.flags = broadcast
                    wb.broadcast_message = "Join us"
                }
                .build()

        val listening = stored.withListen(true)
        assertTrue(listening.listens)
        assertEquals("Join us", listening.broadcast_message)
        assertEquals(broadcast, listening.flags and broadcast)
        assertFalse(listening.withListen(false).listens)
        assertEquals(stored, listening.withListen(false))
    }

    @Test
    fun `the page lists at most five beacons not already joined`() {
        val offers = (1..7).map { offer(it) }

        assertEquals(
            RADIO_SETUP_BEACON_LIMIT,
            setupBeaconOffers(offers, lora(region = RegionCode.US), emptyList()).size,
        )
        // Mesh1 is already on the radio, so Mesh6 moves into the five.
        val joined = listOf(ChannelSettings.Builder().also { cb -> cb.name = "Mesh1" }.build())
        val listed = setupBeaconOffers(offers, lora(region = RegionCode.US), joined).map { it.fromNodeNum }
        assertEquals(listOf(2, 3, 4, 5, 6), listed)
        // A channel with the same name but another key is a different mesh, so its offer stays.
        val otherKey =
            listOf(
                ChannelSettings.Builder()
                    .also { cb ->
                        cb.name = "Mesh1"
                        cb.psk = okio.ByteString.of(1, 2, 3)
                    }
                    .build(),
            )
        assertEquals(1, setupBeaconOffers(offers, lora(region = RegionCode.US), otherKey).first().fromNodeNum)
        assertEquals(emptyList(), setupBeaconOffers(emptyList(), null, emptyList()))
    }

    private fun offer(n: Int) = MeshBeaconOffer(
        fromNodeNum = n,
        beacon =
        MeshBeacon.Builder()
            .also { wb ->
                wb.message = "Mesh $n"
                wb.offer_channel = ChannelSettings.Builder().also { cb -> cb.name = "Mesh$n" }.build()
            }
            .build(),
    )

    @Test
    fun `a factory fresh US radio moves to Long Turbo`() {
        val edited = lora().withSetupRegion(RegionCode.US, usMap)

        assertEquals(RegionCode.US, edited.region)
        assertEquals(ModemPreset.LONG_TURBO, edited.modem_preset)
    }

    @Test
    fun `custom modem settings survive a region change until a preset is picked`() {
        val custom = lora(preset = ModemPreset.MEDIUM_FAST, usePreset = false)

        assertFalse(custom.withSetupRegion(RegionCode.EU_868, null).use_preset)
        assertTrue(custom.withSetupPreset(ModemPreset.LONG_FAST).use_preset)
    }

    @Test
    fun `only a region or preset change needs a write`() {
        val stored = lora(region = RegionCode.US)

        assertFalse(loraSetupChanged(stored, stored))
        assertTrue(loraSetupChanged(stored, stored.withSetupRegion(RegionCode.EU_868, null)))
        assertTrue(loraSetupChanged(stored, stored.withSetupPreset(ModemPreset.SHORT_FAST)))
        assertTrue(loraSetupChanged(stored, lora(region = RegionCode.US, usePreset = false)))
    }

    @Test
    fun `writes need the bound radio to be the connected one`() {
        assertTrue(radioSetupCanWrite(connected = true, boundAddress = "a", identityAddress = "a"))
        assertFalse(radioSetupCanWrite(connected = false, boundAddress = "a", identityAddress = "a"))
        assertFalse(radioSetupCanWrite(connected = true, boundAddress = "a", identityAddress = "b"))
        assertFalse(radioSetupCanWrite(connected = true, boundAddress = "a", identityAddress = null))
        assertFalse(radioSetupCanWrite(connected = true, boundAddress = null, identityAddress = "a"))
    }
}
