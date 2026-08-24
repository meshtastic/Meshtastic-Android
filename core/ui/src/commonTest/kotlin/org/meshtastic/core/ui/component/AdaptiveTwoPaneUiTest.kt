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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3AdaptiveApi::class)
class AdaptiveTwoPaneUiTest {

    // Crashlytics 788308c5 (fatal, tablets): the split scaffold echoed the LazyColumn item's infinite
    // max height as its size ("Size(1608 x 2147483647) is out of range"). Must render, not crash.
    @Test
    fun splitPaneInsideLazyColumnItemRendersBothPanes() = runComposeUiTest {
        var horizontalPartitions = 0
        setContent {
            AppTheme {
                horizontalPartitions =
                    calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()).maxHorizontalPartitions
                LazyColumn {
                    item { AdaptiveTwoPane(first = { Text("first pane") }, second = { Text("second pane") }) }
                }
            }
        }

        // The default test window is expanded-width; without this the regression path is not exercised.
        assertTrue(horizontalPartitions > 1, "expected an expanded-width test window")
        onNodeWithText("first pane").assertIsDisplayed()
        onNodeWithText("second pane").assertIsDisplayed()
        // No drag handle: the unbounded host must get the Row fallback, not the scaffold.
        onNodeWithTag(ADAPTIVE_TWO_PANE_DRAG_HANDLE_TAG).assertDoesNotExist()
    }

    @Test
    fun splitPaneInBoundedHostRendersBothPanes() = runComposeUiTest {
        setContent {
            AppTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AdaptiveTwoPane(first = { Text("first pane") }, second = { Text("second pane") })
                }
            }
        }

        onNodeWithText("first pane").assertIsDisplayed()
        onNodeWithText("second pane").assertIsDisplayed()
        // The drag handle proves the bounded host kept the SupportingPaneScaffold branch.
        onNodeWithTag(ADAPTIVE_TWO_PANE_DRAG_HANDLE_TAG).assertExists()
    }

    // The pane expansion state is hoisted above the height branch, so a divider the user dragged must
    // survive the host flipping to unbounded constraints (scaffold disposed) and back.
    @Test
    fun dividerPositionSurvivesBoundedUnboundedRoundTrip() = runComposeUiTest {
        var bounded by mutableStateOf(true)
        setContent {
            AppTheme {
                val hostModifier =
                    if (bounded) Modifier.fillMaxSize() else Modifier.verticalScroll(rememberScrollState())
                Box(modifier = hostModifier) {
                    AdaptiveTwoPane(first = { Text("first pane") }, second = { Text("second pane") })
                }
            }
        }

        val handle = onNodeWithTag(ADAPTIVE_TWO_PANE_DRAG_HANDLE_TAG)
        val initialX = handle.fetchSemanticsNode().boundsInRoot.center.x
        handle.performTouchInput {
            down(center)
            moveBy(Offset(-200f, 0f))
            up()
        }
        waitForIdle()
        val draggedX = handle.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(abs(draggedX - initialX) > 50f, "expected the drag to move the divider")

        bounded = false
        waitForIdle()
        handle.assertDoesNotExist()

        bounded = true
        waitForIdle()
        val restoredX = handle.fetchSemanticsNode().boundsInRoot.center.x
        assertTrue(abs(restoredX - draggedX) < 3f, "divider was at $draggedX but came back at $restoredX")
    }
}
