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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.mesh_beacon_offer_add
import org.meshtastic.core.resources.mesh_beacon_offer_join
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.RadioConfigState
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.MeshBeacon
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class BeaconsPageUiTest {

    private val jpPinnedSlot12 =
        Config.LoRaConfig.Builder()
            .also { wb ->
                wb.region = RegionCode.JP
                wb.use_preset = true
                wb.modem_preset = ModemPreset.LONG_FAST
                wb.channel_num = 12
            }
            .build()

    private val jpOfferSlot12 =
        MeshBeaconOffer(
            fromNodeNum = 7,
            beacon =
            MeshBeacon.Builder()
                .also { wb ->
                    wb.message = "Tokyo Mesh"
                    wb.offer_channel = ChannelSettings.Builder().also { cb -> cb.name = "Tokyo" }.build()
                    wb.offer_preset = ModemPreset.LONG_FAST
                    wb.offer_region = RegionCode.JP
                    wb.offer_frequency_slot = 12
                }
                .build(),
        )

    private fun stateForFirmware(version: String) = RadioConfigState(
        metadata = DeviceMetadata.Builder().also { wb -> wb.firmware_version = version }.build(),
        channelList = listOf(ChannelSettings.Builder().also { wb -> wb.name = "Home" }.build()),
    )

    @Test
    fun jpOfferAtSlot12_offersAddWithoutRetuneOnLegacyFirmware() = runComposeUiTest {
        var joined: ChannelSet? = null

        setContent {
            AppTheme {
                BeaconsPage(
                    beaconConfig = MeshBeaconConfig.Builder().build(),
                    offers = listOf(jpOfferSlot12),
                    lora = jpPinnedSlot12,
                    state = stateForFirmware("2.7.26"),
                    canWrite = true,
                    onListenChange = {},
                    onJoin = { joined = it },
                    onNavigate = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_offer_add)).assertIsDisplayed().performClick()

        runOnIdle {
            val channelSet = assertNotNull(joined)
            assertNull(channelSet.lora_config, "ADD must not retune the radio")
        }
    }

    @Test
    fun jpOfferAtSlot12_offersJoinWithDerivedSlotOnAribFirmware() = runComposeUiTest {
        var joined: ChannelSet? = null

        setContent {
            AppTheme {
                BeaconsPage(
                    beaconConfig = MeshBeaconConfig.Builder().build(),
                    offers = listOf(jpOfferSlot12),
                    lora = jpPinnedSlot12,
                    state = stateForFirmware("2.8.3"),
                    canWrite = true,
                    onListenChange = {},
                    onJoin = { joined = it },
                    onNavigate = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_offer_join)).assertIsDisplayed().performClick()

        runOnIdle {
            val channelSet = assertNotNull(joined)
            assertEquals(0, assertNotNull(channelSet.lora_config).channel_num)
        }
    }
}
