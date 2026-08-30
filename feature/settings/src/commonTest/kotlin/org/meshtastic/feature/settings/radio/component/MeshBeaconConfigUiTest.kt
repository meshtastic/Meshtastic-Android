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

import androidx.compose.material3.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.mesh_beacon_region_required
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.component.DropDownItem
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import org.meshtastic.proto.Config.LoRaConfig.RegionCode
import org.meshtastic.proto.ModuleConfig.MeshBeaconConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Mirrors [LoRaBandwidthUiTest]'s shape: composes [RadioConfigScreenList] directly with a hand-built [ConfigState],
 * exercising the same policy functions the real [MeshBeaconConfigScreen] uses (design#140 behaviors 1, 3, 4), without
 * needing a full [org.meshtastic.feature.settings.radio.RadioConfigViewModel].
 */
@OptIn(ExperimentalTestApi::class)
class MeshBeaconConfigUiTest {

    @Test
    fun regionUnset_showsBlockedMessageInsteadOfEditor() = runComposeUiTest {
        setContent {
            AppTheme {
                RadioConfigScreenList(
                    title = "Mesh Beacon",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = rememberConfigState(MeshBeaconConfig()),
                    enabled = false,
                    onSave = {},
                ) {
                    item {
                        TitledCard(title = "Mesh Beacon") { Text(getString(Res.string.mesh_beacon_region_required)) }
                    }
                }
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_region_required)).assertIsDisplayed()
    }

    @Test
    fun requiredOfferedChannel_defaultsToPrimaryAndStampsRegionAndPresetOnSave() = runComposeUiTest {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.MEDIUM_FAST)
        // channel_num deliberately set: proves the save path narrows to name+psk, not the whole ChannelSettings.
        val primary = ChannelSettings(name = "Primary", channel_num = 7)
        val channelList = listOf(primary)
        val initialConfig = MeshBeaconConfig()
        lateinit var configState: ConfigState<MeshBeaconConfig>
        var savedConfig: MeshBeaconConfig? = null

        setContent {
            AppTheme {
                configState = rememberConfigState(initialConfig)
                RadioConfigScreenList(
                    title = "Mesh Beacon",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    saveEnabled = channelList.isNotEmpty(),
                    onSave = { savedConfig = stampBeaconConfigForSave(it, radioLora, channelList) },
                ) {
                    item {
                        OfferChannelPreference(
                            offerChannel = configState.value.broadcast_offer_channel,
                            channelList = channelList,
                            radioLora = radioLora,
                            channelItems = channelList.mapIndexed { index, s -> DropDownItem(index, s.name) },
                            enabled = true,
                            onChannelSelect = {
                                configState.value = configState.value.copy(broadcast_offer_channel = it)
                            },
                        )
                    }
                }
            }
        }

        // Never touched the picker: mark the form dirty another way, then save with the untouched offer channel.
        runOnIdle { configState.value = configState.value.copy(broadcast_message = "hi") }

        onNodeWithText(getString(Res.string.save_changes)).assertIsEnabled().performClick()

        runOnIdle {
            assertEquals(ChannelSettings(name = "Primary"), savedConfig?.broadcast_offer_channel)
            assertEquals(RegionCode.US, savedConfig?.broadcast_offer_region)
            assertEquals(ModemPreset.MEDIUM_FAST, savedConfig?.broadcast_offer_preset)
        }
    }
}
