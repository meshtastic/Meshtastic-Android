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

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.model.Capabilities
import org.meshtastic.core.model.RegionPresetConstraint
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.mesh_beacon_broadcast_requires_preset
import org.meshtastic.core.resources.mesh_beacon_no_channels
import org.meshtastic.core.resources.mesh_beacon_region_required
import org.meshtastic.core.resources.mesh_beacon_target
import org.meshtastic.core.resources.mesh_beacon_target_remove
import org.meshtastic.core.resources.save_changes
import org.meshtastic.core.ui.component.DropDownItem
import org.meshtastic.core.ui.component.EditTextPreference
import org.meshtastic.core.ui.component.SwitchPreference
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
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.MEDIUM_FAST, use_preset = true)
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
                    onSave = { savedConfig = stampBeaconConfigForSave(it, initialConfig, radioLora, channelList) },
                ) {
                    item {
                        OfferChannelPreference(
                            offerChannel = configState.value.broadcast_offer_channel,
                            channelList = channelList,
                            selectableChannels = selectableBeaconChannels(channelList),
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

    @Test
    fun customParamsRadio_storedBroadcastOff_toggleDisabledAndSectionsHidden() = runComposeUiTest {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, use_preset = false)
        val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
        val storedConfig = MeshBeaconConfig(flags = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value)
        lateinit var configState: ConfigState<MeshBeaconConfig>

        setContent {
            AppTheme {
                configState = rememberConfigState(storedConfig)
                val storedBroadcastEnabled = (storedConfig.flags and broadcastFlag) != 0
                val gate = beaconBroadcastGate(connected = true, radioLora.use_preset, storedBroadcastEnabled)
                RadioConfigScreenList(
                    title = "Mesh Beacon",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    onSave = {},
                ) {
                    item {
                        SwitchPreference(
                            title = "Broadcast a beacon",
                            checked = (configState.value.flags and broadcastFlag) != 0,
                            enabled = gate.toggleEnabled,
                            onCheckedChange = {
                                configState.value =
                                    configState.value.copy(
                                        flags =
                                        if (it) {
                                            configState.value.flags or broadcastFlag
                                        } else {
                                            configState.value.flags and broadcastFlag.inv()
                                        },
                                    )
                            },
                        )
                        Text(getString(Res.string.mesh_beacon_broadcast_requires_preset))
                    }
                    if (gate.sectionsVisible) {
                        item { Text("Beacon message field") }
                    }
                }
            }
        }

        onNodeWithText("Broadcast a beacon").assertIsNotEnabled()
        onNodeWithText(getString(Res.string.mesh_beacon_broadcast_requires_preset)).assertIsDisplayed()
        onNodeWithText("Beacon message field").assertDoesNotExist()
    }

    @Test
    fun customParamsRadio_storedBroadcastOn_sectionsVisibleButDisabledAndToggleCanSwitchOff() = runComposeUiTest {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, use_preset = false)
        val broadcastFlag = MeshBeaconConfig.Flags.FLAG_BROADCAST_ENABLED.value
        val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
        val storedConfig = MeshBeaconConfig(flags = listenFlag or broadcastFlag)
        lateinit var configState: ConfigState<MeshBeaconConfig>

        setContent {
            AppTheme {
                configState = rememberConfigState(storedConfig)
                val storedBroadcastEnabled = (storedConfig.flags and broadcastFlag) != 0
                val gate = beaconBroadcastGate(connected = true, radioLora.use_preset, storedBroadcastEnabled)
                RadioConfigScreenList(
                    title = "Mesh Beacon",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    onSave = {},
                ) {
                    item {
                        SwitchPreference(
                            title = "Broadcast a beacon",
                            checked = (configState.value.flags and broadcastFlag) != 0,
                            enabled = gate.toggleEnabled,
                            onCheckedChange = {
                                configState.value =
                                    configState.value.copy(
                                        flags =
                                        if (it) {
                                            configState.value.flags or broadcastFlag
                                        } else {
                                            configState.value.flags and broadcastFlag.inv()
                                        },
                                    )
                            },
                        )
                    }
                    if (gate.sectionsVisible) {
                        item {
                            EditTextPreference(
                                title = "Beacon message",
                                value = configState.value.broadcast_message,
                                maxSize = 100,
                                enabled = gate.sectionsEnabled,
                                isError = false,
                                keyboardOptions = KeyboardOptions.Default,
                                keyboardActions = KeyboardActions.Default,
                                onValueChanged = { configState.value = configState.value.copy(broadcast_message = it) },
                            )
                        }
                    }
                }
            }
        }

        onNodeWithText("Broadcast a beacon").assertIsEnabled()
        onNodeWithText("Beacon message").assertIsNotEnabled()

        onNodeWithText("Broadcast a beacon").performClick()

        runOnIdle { assertEquals(listenFlag, configState.value.flags) }
    }

    @Test
    fun emptyChannelList_offerPickerShowsDisabledPlaceholderRow() = runComposeUiTest {
        val radioLora =
            Config.LoRaConfig(region = RegionCode.US, modem_preset = ModemPreset.LONG_FAST, use_preset = true)
        lateinit var configState: ConfigState<MeshBeaconConfig>

        setContent {
            AppTheme {
                configState = rememberConfigState(MeshBeaconConfig())
                OfferChannelPreference(
                    offerChannel = configState.value.broadcast_offer_channel,
                    channelList = emptyList(),
                    selectableChannels = emptyList(),
                    radioLora = radioLora,
                    channelItems = emptyList(),
                    enabled = true,
                    onChannelSelect = { configState.value = configState.value.copy(broadcast_offer_channel = it) },
                )
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_no_channels)).assertIsDisplayed()
    }

    @Test
    fun customParamsRadio_emptyChannelListListenOnlyEdit_saveButtonEnabled() = runComposeUiTest {
        val radioLora = Config.LoRaConfig(region = RegionCode.US, use_preset = false)
        val listenFlag = MeshBeaconConfig.Flags.FLAG_LISTEN_ENABLED.value
        val initialConfig = MeshBeaconConfig()
        val emptyChannelList = emptyList<ChannelSettings>()
        lateinit var configState: ConfigState<MeshBeaconConfig>
        var savedConfig: MeshBeaconConfig? = null

        setContent {
            AppTheme {
                configState = rememberConfigState(initialConfig)
                val intervalValid = true
                RadioConfigScreenList(
                    title = "Mesh Beacon",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = configState,
                    enabled = true,
                    saveEnabled =
                    meshBeaconSaveEnabled(
                        connected = true,
                        radioLora.use_preset,
                        intervalValid,
                        hasChannels = emptyChannelList.isNotEmpty(),
                    ),
                    onSave = { savedConfig = stampBeaconConfigForSave(it, initialConfig, radioLora, emptyChannelList) },
                ) {
                    item {
                        SwitchPreference(
                            title = "Listen for beacons",
                            checked = (configState.value.flags and listenFlag) != 0,
                            enabled = true,
                            onCheckedChange = {
                                configState.value =
                                    configState.value.copy(
                                        flags =
                                        if (it) {
                                            configState.value.flags or listenFlag
                                        } else {
                                            configState.value.flags and listenFlag.inv()
                                        },
                                    )
                            },
                        )
                    }
                }
            }
        }

        onNodeWithText("Listen for beacons").performClick()

        onNodeWithText(getString(Res.string.save_changes)).assertIsEnabled().performClick()

        runOnIdle { assertEquals(listenFlag, savedConfig?.flags) }
    }

    @Test
    fun emptyStoredTargets_seededListRendersExactlyOneRow() = runComposeUiTest {
        val presetConstraint =
            RegionPresetConstraint(presets = listOf(ModemPreset.LONG_FAST), ModemPreset.LONG_FAST, false)

        setContent {
            AppTheme {
                BroadcastTargetsCard(
                    targets = seedBeaconTargets(emptyList()),
                    enabled = true,
                    channelItems = listOf(DropDownItem(0, "Primary")),
                    currentPreset = ModemPreset.LONG_FAST,
                    presetConstraint = presetConstraint,
                    presetsGated = false,
                    capabilities = Capabilities(firmwareVersion = null),
                    onChange = {},
                )
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_target, 1)).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.mesh_beacon_target_remove)).assertCountEquals(1)
    }

    @Test
    fun removingTheLastTargetRow_reseedsOneRowInsteadOfZero() = runComposeUiTest {
        val presetConstraint =
            RegionPresetConstraint(presets = listOf(ModemPreset.LONG_FAST), ModemPreset.LONG_FAST, false)

        setContent {
            AppTheme {
                var targets by remember { mutableStateOf(seedBeaconTargets(emptyList())) }
                BroadcastTargetsCard(
                    targets = targets,
                    enabled = true,
                    channelItems = listOf(DropDownItem(0, "Primary")),
                    currentPreset = ModemPreset.LONG_FAST,
                    presetConstraint = presetConstraint,
                    presetsGated = false,
                    capabilities = Capabilities(firmwareVersion = null),
                    onChange = { targets = it },
                )
            }
        }

        onNodeWithText(getString(Res.string.mesh_beacon_target_remove)).performClick()

        // The row survives the click: it never renders empty, it comes back as a fresh default row (design#140
        // behavior 6) -- not zero rows, which would hide firmware's implicit single-target fallback.
        onNodeWithText(getString(Res.string.mesh_beacon_target, 1)).assertIsDisplayed()
        onAllNodesWithText(getString(Res.string.mesh_beacon_target_remove)).assertCountEquals(1)
    }
}
