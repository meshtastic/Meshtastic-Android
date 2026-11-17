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
package org.meshtastic.feature.connections.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.meshtastic.core.network.radio.PrimaryRadio
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.schema_lora_modempreset_short_fast
import org.meshtastic.feature.connections.model.BearerRowState
import org.meshtastic.feature.connections.model.BearerRowUi
import org.meshtastic.feature.connections.model.ExtraRadioUi
import org.meshtastic.feature.connections.model.NodeStatusUi
import org.meshtastic.feature.connections.ui.components.NodeStatusCard
import org.meshtastic.node.host.ExtraRadio
import org.meshtastic.node.host.ExtraRadioDevice
import org.meshtastic.node.host.NodePhase
import org.meshtastic.node.transport.Bearer
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class NodeExtraRadiosTest {

    private val switches = mutableListOf<Pair<String, Boolean>>()
    private val shortFast = getString(Res.string.schema_lora_modempreset_short_fast)

    private fun extraRow(
        number: Int,
        details: ExtraRadio?,
        state: BearerRowState = BearerRowState.Active,
        switchedOn: Boolean = true,
        loraOn: Boolean = true,
    ) = BearerRowUi(
        bearer = Bearer.LORA,
        enabled = state != BearerRowState.Off,
        switchable = true,
        state = state,
        received = 4,
        sent = 0,
        relayed = 0,
        extra = ExtraRadioUi("lora-$number", number, details, switchedOn, loraOn),
    )

    private fun ComposeUiTest.setCard(vararg rows: BearerRowUi, failure: String? = null, width: Dp = WIDE) =
        setContent {
            MaterialTheme {
                Box(Modifier.width(width)) {
                    NodeStatusCard(
                        status =
                        NodeStatusUi(
                            phase = NodePhase.Running,
                            nodeId = "!3a5806be",
                            longName = null,
                            shortName = null,
                            peerCount = 0,
                            failure = null,
                            settingsReset = false,
                            bearers = rows.toList(),
                            extraRadioFailure = failure,
                        ),
                        onSetBearer = { _, _ -> },
                        onSetExtraRadio = { name, on -> switches += name to on },
                        onRefreshRequirements = {},
                        onRequestUsbPermission = {},
                    )
                }
            }
        }

    private fun primaryRow(primary: PrimaryRadio?) = BearerRowUi(
        bearer = Bearer.LORA,
        enabled = true,
        switchable = true,
        state = BearerRowState.Active,
        received = 4,
        sent = 0,
        relayed = 0,
        primary = primary,
    )

    @Test
    fun `a described first radio is radio 1 and says what it is ahead of its state`() = runComposeUiTest {
        setCard(
            primaryRow(PrimaryRadio(ExtraRadioDevice.BOARD, id = "lora-aio")),
            extraRow(2, ExtraRadio(ExtraRadioDevice.RTL_SDR, id = "00000001")),
        )

        onNodeWithText("LoRa radio 1").assertIsDisplayed()
        onNodeWithText("Board lora-aio · primary · Active · 4 received · 0 sent · 0 relayed").assertIsDisplayed()
        onNodeWithText("LoRa stick").assertDoesNotExist()
    }

    @Test
    fun `a first radio with no id names its kind alone`() = runComposeUiTest {
        setCard(primaryRow(PrimaryRadio(ExtraRadioDevice.USB_STICK)))

        onNodeWithText("USB stick · primary · Active · 4 received · 0 sent · 0 relayed").assertIsDisplayed()
    }

    @Test
    fun `a first radio the host does not describe keeps the plain LoRa row`() = runComposeUiTest {
        setCard(primaryRow(primary = null))

        onNodeWithText("LoRa stick").assertIsDisplayed()
        onNodeWithText("Active · 4 received · 0 sent · 0 relayed").assertIsDisplayed()
        onNodeWithText("LoRa radio 1").assertDoesNotExist()
    }

    @Test
    fun `each kind of radio says what it is and what it does ahead of its state`() = runComposeUiTest {
        setCard(
            extraRow(2, ExtraRadio(ExtraRadioDevice.RTL_SDR, id = "00000001")),
            extraRow(3, ExtraRadio(ExtraRadioDevice.RTL_SDR, preset = ModemPreset.SHORT_FAST)),
            extraRow(
                4,
                ExtraRadio(
                    ExtraRadioDevice.USB_STICK,
                    id = "3A7F01",
                    preset = ModemPreset.SHORT_FAST,
                    bridge = true,
                    duplex = true,
                ),
            ),
            extraRow(5, ExtraRadio(ExtraRadioDevice.BOARD, id = "lora-hat"), state = BearerRowState.Ready),
            extraRow(6, details = null),
        )

        val counters = "4 received · 0 sent · 0 relayed"
        onNodeWithText("LoRa radio 2").assertIsDisplayed()
        onNodeWithText("RTL-SDR 00000001 · mirrors the primary · receive only · Active · $counters").assertIsDisplayed()
        onNodeWithText("RTL-SDR · listens on $shortFast · receive only · Active · $counters").assertIsDisplayed()
        onNodeWithText("USB stick 3A7F01 · listens on $shortFast · transmits · bridges · Active · $counters")
            .assertIsDisplayed()
        onNodeWithText("Board lora-hat · mirrors the primary · receive only · Ready · $counters").assertIsDisplayed()
        onNodeWithText("Active · $counters").assertIsDisplayed()
    }

    @Test
    fun `a narrow card drops the least important parts instead of taking a third line`() = runComposeUiTest {
        setCard(
            extraRow(
                2,
                ExtraRadio(ExtraRadioDevice.RTL_SDR, id = "00000001", preset = ModemPreset.SHORT_FAST, bridge = true),
            ),
            width = NARROW,
        )
        waitForIdle()

        val counters = "4 received · 0 sent · 0 relayed"
        onNodeWithText("receive only", substring = true).assertDoesNotExist()
        onNodeWithText("RTL-SDR", substring = true).assertIsDisplayed()
        onNodeWithText("listens on $shortFast", substring = true).assertIsDisplayed()
        onNodeWithText("Active · $counters", substring = true).assertIsDisplayed()
    }

    @Test
    fun `tapping a row switches that radio the other way`() = runComposeUiTest {
        setCard(
            extraRow(2, ExtraRadio(ExtraRadioDevice.RTL_SDR)),
            extraRow(3, ExtraRadio(ExtraRadioDevice.RTL_SDR), state = BearerRowState.Off, switchedOn = false),
        )

        onNodeWithText("LoRa radio 2").performClick()
        onNodeWithText("LoRa radio 3").performClick()
        assertEquals(listOf("lora-2" to false, "lora-3" to true), switches)
        onNodeWithText("RTL-SDR · mirrors the primary · receive only · Off").assertIsDisplayed()
    }

    @Test
    fun `LoRa off leaves the row unclickable and says why a radio left on is off`() = runComposeUiTest {
        setCard(
            extraRow(2, ExtraRadio(ExtraRadioDevice.RTL_SDR), state = BearerRowState.Off, loraOn = false),
            extraRow(
                3,
                ExtraRadio(ExtraRadioDevice.BOARD),
                state = BearerRowState.Off,
                switchedOn = false,
                loraOn = false,
            ),
        )

        onNodeWithText("LoRa radio 2").performClick()
        onNodeWithText("LoRa radio 3").performClick()
        assertEquals(emptyList(), switches)
        onNodeWithText("RTL-SDR · mirrors the primary · receive only · Off while LoRa is off").assertIsDisplayed()
        onNodeWithText("Board · mirrors the primary · receive only · Off").assertIsDisplayed()
    }

    @Test
    fun `the reason stored choices are unusable shows once under the extra radios`() = runComposeUiTest {
        setCard(
            extraRow(2, ExtraRadio(ExtraRadioDevice.RTL_SDR), state = BearerRowState.Off, switchedOn = false),
            extraRow(3, ExtraRadio(ExtraRadioDevice.RTL_SDR), state = BearerRowState.Off, switchedOn = false),
            failure = "could not be read: Permission denied",
        )

        onNodeWithText("Extra radios stay off until switched on: could not be read: Permission denied")
            .assertIsDisplayed()
    }

    private companion object {
        val WIDE = 900.dp
        val NARROW = 300.dp
    }
}
