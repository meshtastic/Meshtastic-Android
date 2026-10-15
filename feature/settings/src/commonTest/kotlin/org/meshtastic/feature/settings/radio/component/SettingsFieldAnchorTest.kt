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

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.component.TitledCard
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.search.LocalSettingsFieldFocus
import org.meshtastic.feature.settings.search.SettingsFieldFocus
import org.meshtastic.proto.Config
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsFieldAnchorTest {

    private val rowKeys = (0 until ROWS).map { "field_$it" }

    @Composable
    private fun LongCardScreen(focus: SettingsFieldFocus, midRowPlaced: () -> Boolean = { true }) {
        AppTheme {
            CompositionLocalProvider(LocalSettingsFieldFocus provides focus) {
                RadioConfigScreenList(
                    title = "Telemetry",
                    onBack = {},
                    responseState = ResponseState.Empty,
                    onDismissPacketResponse = {},
                    configState = rememberConfigState(Config.LoRaConfig.Builder().build()),
                    enabled = true,
                    onSave = {},
                ) {
                    fieldItem(rowKeys.toSet()) {
                        TitledCard(title = "Card") {
                            rowKeys.forEachIndexed { i, key ->
                                val row =
                                    @Composable {
                                        Text("Row $i", Modifier.settingsField(key).height(ROW_HEIGHT.dp))
                                    }
                                if (i == MID_ROW) PlacementGate(midRowPlaced, row) else row()
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun searchCentersARowDeepInsideASingleCard() = runComposeUiTest {
        val focus = SettingsFieldFocus()
        focus.request("field_$MID_ROW")

        setContent { LongCardScreen(focus) }
        waitForIdle()

        assertMidRowCentered()
        runOnIdle { assertEquals(null, focus.pending.value) }
    }

    @Test
    fun aRowComposedBeforeItIsPlacedIsCenteredOncePlaced() = runComposeUiTest {
        val focus = SettingsFieldFocus()
        focus.request("field_$MID_ROW")
        var placed by mutableStateOf(false)
        mainClock.autoAdvance = false

        setContent { LongCardScreen(focus, midRowPlaced = { placed }) }
        repeat(UNPLACED_FRAMES) { mainClock.advanceTimeByFrame() }
        runOnIdle { assertEquals(null, focus.pending.value) }

        placed = true
        repeat(SETTLE_FRAMES) { mainClock.advanceTimeByFrame() }

        assertMidRowCentered()
    }

    private fun ComposeUiTest.assertMidRowCentered() {
        onNodeWithText("Row $MID_ROW").assertIsDisplayed()
        onNodeWithText("Row 0").assertIsNotDisplayed()
        val row = onNodeWithText("Row $MID_ROW").getBoundsInRoot()
        val list = onNode(hasScrollAction()).getBoundsInRoot()
        val rowCenter = (row.top + row.bottom) / 2
        val listCenter = (list.top + list.bottom) / 2
        assertTrue(abs((rowCenter - listCenter).value) <= CENTER_TOLERANCE_DP, "row at $rowCenter, list at $listCenter")
    }

    @Test
    fun aRowNotYetPlacedHasNoOffset() {
        val highlight = FieldHighlight(mutableStateOf(Color.Red))
        highlight.register(
            setOf("a"),
            object : FieldAnchor {
                override val placedCoordinates: LayoutCoordinates? = null
            },
        )

        assertNull(highlight.offsetToCenter("a"))
        assertNull(highlight.offsetToCenter("missing"))
    }

    @Test
    fun searchForTheLastRowScrollsOnlyAsFarAsTheListAllows() = runComposeUiTest {
        val focus = SettingsFieldFocus()
        focus.request("field_${ROWS - 1}")

        setContent { LongCardScreen(focus) }
        waitForIdle()

        onNodeWithText("Row ${ROWS - 1}").assertIsDisplayed()
        onNodeWithText("Row 0").assertIsNotDisplayed()
        runOnIdle { assertEquals(null, focus.pending.value) }
    }

    @Test
    fun onlyTheTargetRowTakesTheTint() {
        val highlight = FieldHighlight(mutableStateOf(Color.Red))
        highlight.key = "b"

        assertEquals(Color.Red, highlight.tintFor(setOf("a", "b")))
        assertEquals(Color.Transparent, highlight.tintFor(setOf("a")))
    }

    @Test
    fun aCardTintsOnlyWhenNoRowClaimsTheTarget() {
        val highlight = FieldHighlight(mutableStateOf(Color.Red))
        highlight.key = "b"

        assertEquals(Color.Red, highlight.fallbackTintFor(setOf("a", "b")))
        assertEquals(Color.Transparent, highlight.fallbackTintFor(setOf("a")))
    }

    private companion object {
        const val ROWS = 40
        const val ROW_HEIGHT = 56
        const val MID_ROW = 20
        const val CENTER_TOLERANCE_DP = 8f
        const val UNPLACED_FRAMES = 20
        const val SETTLE_FRAMES = 60
    }
}

/** Measures [content] but places it only once [placed], so a row can be composed and laid out yet unplaced. */
@Composable
private fun PlacementGate(placed: () -> Boolean, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        layout(placeables.maxOfOrNull { it.width } ?: 0, placeables.sumOf { it.height }) {
            if (placed()) placeables.forEach { it.place(0, 0) }
        }
    }
}
