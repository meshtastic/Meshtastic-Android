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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The design standards require the app to support system font scaling up to 200% without clipping text. These render
 * Connections controls on a 360dp phone and read each label's text layout, so a regression shows up as a failing
 * assertion rather than a screenshot someone has to notice.
 */
@OptIn(ExperimentalTestApi::class)
class ConnectionsLargeFontTest {

    @Test
    fun transportSelectorShowsLabelsThatFitAtDefaultFontScale() = runComposeUiTest {
        setPhoneContent(fontScale = 1f) { TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {}) }

        assertLabelsFitTheirSegments()
    }

    // The fallback is chosen per layout, not once: a selector that starts too narrow for its labels shows icons, and
    // gets its labels back when it widens.
    @Test
    fun transportSelectorLabelsReturnWhenTheSelectorWidens() = runComposeUiTest {
        var width by mutableStateOf(200.dp)
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = 1f)) {
                AppTheme {
                    Box(modifier = Modifier.width(width)) {
                        TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {})
                    }
                }
            }
        }

        for (label in TRANSPORT_LABELS) {
            onNodeWithText(label).assertDoesNotExist()
            onNodeWithContentDescription(label).assertIsDisplayed()
        }

        width = 328.dp
        waitForIdle()

        assertLabelsFitTheirSegments()
    }

    @Test
    fun transportSelectorFallsBackToNamedIconsAtDoubleFontScale() = runComposeUiTest {
        setPhoneContent(fontScale = 2f) { TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {}) }

        for (label in TRANSPORT_LABELS) {
            onNodeWithText(label).assertDoesNotExist()
            onNodeWithContentDescription(label).assertIsDisplayed()
        }
    }

    // SegmentedButton measures its icon slot at the label's height; the icon must still sit on the label's center
    // line rather than at the top of that slot once the label grows taller than the 18dp icon.
    @Test
    fun transportSelectorIconsStayCenteredOnTheirLabelsAtDoubleFontScale() = runComposeUiTest {
        setPhoneContent(fontScale = 2f, width = 600.dp) {
            TransportSelector(activeTransport = DeviceType.BLE, onSelectTransport = {})
        }

        for (label in listOf("Network", "USB")) {
            val icon = onNodeWithTag("$TRANSPORT_ICON_TAG:$label", useUnmergedTree = true).fetchSemanticsNode()
            val text = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            val offset = abs(icon.boundsInRoot.center.y - text.boundsInRoot.center.y)
            assertTrue(offset <= 1f, "\"$label\" icon is $offset px off its label's center line")
        }
    }

    @Test
    fun addDeviceFormIsNotClippedAtDoubleFontScale() = runComposeUiTest {
        setPhoneContent(fontScale = 2f) { AddDeviceForm(onCancel = {}, onClickAdd = { _, _ -> }) }

        for (label in listOf("Address", "Port:", "Cancel", "Add")) {
            assertTextFits(onNodeWithText(label).fetchSemanticsNode(), label)
        }
        // The port field shows the default service port; every digit of it must be inside the field.
        val portValue = onAllNodesWithText("4403").fetchSemanticsNodes().first()
        val textWidth = textLayout(portValue).size.width
        assertTrue(
            textWidth <= portValue.boundsInRoot.width - FIELD_HORIZONTAL_PADDING_PX,
            "port value is $textWidth px wide in a ${portValue.boundsInRoot.width} px field",
        )
    }

    @Test
    fun sectionHeaderWithScanActionIsNotClippedAtDoubleFontScale() = runComposeUiTest {
        setPhoneContent(fontScale = 2f) {
            DeviceSectionHeader(
                title = "Bluetooth",
                trailing = { TextButton(onClick = {}) { Text("Scan for Bluetooth devices") } },
            )
        }

        assertTextFits(onNodeWithText("Bluetooth").fetchSemanticsNode(), "Bluetooth")
        assertTextFits(onNodeWithText("Scan for Bluetooth devices").fetchSemanticsNode(), "Scan for Bluetooth devices")
        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
    }

    // 328dp is a 360dp phone minus the screen's 16dp side padding.
    private fun ComposeUiTest.setPhoneContent(fontScale: Float, width: Dp = 328.dp, content: @Composable () -> Unit) =
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 1f, fontScale = fontScale)) {
                AppTheme { Box(modifier = Modifier.width(width)) { content() } }
            }
        }

    // Line geometry, not hasVisualOverflow: the latter also fires when a font's ascent overhangs its line height, which
    // is a rendering detail, not a clipped label.
    private fun assertTextFits(node: SemanticsNode, label: String) {
        val layout = textLayout(node)
        val lines = 0 until layout.lineCount
        val truncated = layout.multiParagraph.didExceedMaxLines || lines.any { layout.isLineEllipsized(it) }
        val widest = lines.maxOf { layout.getLineRight(it) }
        assertTrue(!truncated, "\"$label\" is truncated: ${describe(layout)}")
        assertTrue(widest <= layout.size.width + 1f, "\"$label\" runs $widest px in a ${describe(layout)} box")
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val atSpace = text.getOrNull(end - 1)?.isWhitespace() == true || text.getOrNull(end)?.isWhitespace() == true
            assertTrue(atSpace, "\"$label\" breaks inside a word after \"${text.take(end)}\"")
        }
    }

    // Each label sits inside its own segment with at least the selector's 4dp inset, so it can't spill into the
    // padding or under the divider, which the text layout alone would not show.
    private fun ComposeUiTest.assertLabelsFitTheirSegments() {
        for (label in TRANSPORT_LABELS) {
            val text = onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            assertTextFits(text, label)
            val segment =
                generateSequence(text.parent) { it.parent }.first { SemanticsActions.OnClick in it.config }.boundsInRoot
            val bounds = text.boundsInRoot
            assertTrue(
                bounds.left >= segment.left + SEGMENT_INSET_PX && bounds.right <= segment.right - SEGMENT_INSET_PX,
                "\"$label\" at $bounds is not inset in its segment $segment",
            )
        }
    }

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        val action =
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
                ?: fail("no text layout on ${node.config.getOrNull(SemanticsProperties.Text)}")
        action(results)
        return results.single()
    }

    private fun describe(layout: TextLayoutResult) =
        "lines=${layout.lineCount} size=${layout.size} overflow=${layout.hasVisualOverflow}"
}

private val TRANSPORT_LABELS = listOf("Bluetooth", "Network", "USB")

private const val FIELD_HORIZONTAL_PADDING_PX = 32f

// The selector's minimum label inset, at the test's density of 1.
private const val SEGMENT_INSET_PX = 4f
