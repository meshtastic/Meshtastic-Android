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
package org.meshtastic.feature.node.metrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AdaptiveMetricLayoutTest {

    @Test
    fun `panes fit available width and retain selection and scroll across resizing and expansion`() = runComposeUiTest {
        val width = mutableStateOf(360)
        val expanded = mutableStateOf(false)
        val selected = mutableStateOf(-1)
        val listState = LazyListState()
        // Density 0.5 keeps the dp sizes inside the pixel window. The split reads this window, not the parent width.
        val windowInfo = ResizableWindowInfo(IntSize(180, 400))
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(0.5f), LocalWindowInfo provides windowInfo) {
                MaterialTheme {
                    AdaptiveMetricLayout(
                        modifier = Modifier.size(width.value.dp, 800.dp).testTag("parent"),
                        isChartExpanded = expanded.value,
                        chartPart = { modifier -> Box(modifier.widthIn(min = 320.dp).testTag("chart")) },
                        listPart = { modifier ->
                            LazyColumn(
                                modifier.widthIn(min = 320.dp).fillMaxSize().testTag("list"),
                                state = listState,
                            ) {
                                items(40) { index ->
                                    Text(
                                        text = "Metric $index selected ${selected.value == index}",
                                        modifier =
                                        Modifier.fillMaxWidth().height(48.dp).clickable { selected.value = index },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        for (size in listOf(600, 360, 720, 800, 840, 1200, 720)) {
            runOnIdle {
                width.value = size
                windowInfo.resize(IntSize(size / 2, 400))
            }
            val parent = onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
            val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
            val list = onNodeWithTag("list").fetchSemanticsNode().boundsInRoot
            for (pane in listOf(chart, list)) {
                assertTrue(pane.width > 0 && pane.height > 0)
                assertTrue(pane.left >= parent.left && pane.right <= parent.right)
                assertTrue(pane.top >= parent.top && pane.bottom <= parent.bottom)
            }
            if (size < 840) {
                assertEquals(chart.left, list.left)
                assertEquals(chart.bottom, list.top)
                assertEquals(parent.width, list.width)
            } else {
                assertEquals(chart.right, list.left)
                assertEquals(parent.width / 2, chart.width)
                assertEquals(parent.width / 2, list.width)
            }
            runOnIdle {
                if (selected.value == 39) assertTrue(listState.firstVisibleItemIndex > 0)
            }
            onNodeWithTag("list").performScrollToIndex(39)
            onNodeWithText("Metric 39 selected ${selected.value == 39}").assertIsDisplayed().performClick()
            val firstVisible = runOnIdle { listState.firstVisibleItemIndex }
            runOnIdle { expanded.value = true }
            onNodeWithTag("list").assertDoesNotExist()
            assertEquals(parent, onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot)
            runOnIdle { expanded.value = false }
            onNodeWithText("Metric 39 selected true").assertIsDisplayed()
            runOnIdle { assertEquals(firstVisible, listState.firstVisibleItemIndex) }
        }

        // An expanded window splits even when the composable itself is still below 840dp.
        runOnIdle { windowInfo.resize(IntSize(600, 400)) }
        val parent = onNodeWithTag("parent").fetchSemanticsNode().boundsInRoot
        val chart = onNodeWithTag("chart").fetchSemanticsNode().boundsInRoot
        val list = onNodeWithTag("list").fetchSemanticsNode().boundsInRoot
        assertEquals(chart.right, list.left)
        assertEquals(parent.width / 2, chart.width)
        assertEquals(parent.width / 2, list.width)
    }
}

private class ResizableWindowInfo(initial: IntSize) : WindowInfo {
    private val sizePx = mutableStateOf(initial)

    override val isWindowFocused: Boolean = true

    override val containerSize: IntSize
        get() = sizePx.value

    fun resize(size: IntSize) {
        sizePx.value = size
    }
}
