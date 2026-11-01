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
package org.meshtastic.feature.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.traceroute_direct
import org.meshtastic.core.resources.widget_node_tracker_not_found
import org.meshtastic.core.resources.widget_node_tracker_title
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for [NodeTrackerWidget.WidgetContent] on Glance's own harness, which asserts on the composed tree rather
 * than pixels. As in [LocalStatsWidgetContentTest], any string rendering at all proves the [WidgetResourceEnvironment]
 * bridge still resolves Compose Multiplatform resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class NodeTrackerWidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Resolved at construction: getString() wraps runBlocking, which can deadlock inside runGlanceAppWidgetUnitTest.
    private val title: String = getString(Res.string.widget_node_tracker_title)
    private val notFoundLabel: String = getString(Res.string.widget_node_tracker_not_found)
    private val directLabel: String = getString(Res.string.traceroute_direct)

    private fun render(
        size: DpSize,
        state: NodeTrackerWidgetState = createMockTrackerState(),
        assertions: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
        setAppWidgetSize(size)
        setContext(context)
        provideComposable { NodeTrackerWidget().WidgetContent(state, WIDGET_ID) }
        assertions()
    }

    @Test
    fun `tall layout carries the brand header identity and the first four readings`() = render(WidgetSizes.TALL) {
        onNode(hasText(title)).assertExists()
        onNode(hasText("BCS1")).assertExists()
        onNode(hasText("76%")).assertExists()
        onNode(hasText("Basecamp Solar")).assertExists()
        onNode(hasText(directLabel)).assertExists()
        onNode(hasText("1.2 km")).assertExists()
        onNode(hasText("9.5 dB")).assertExists()
        onNode(hasText("-85 dBm")).assertDoesNotExist()
        onNode(hasTestTag(NodeTrackerWidget.STALE_TEST_TAG)).assertDoesNotExist()
    }

    @Test
    fun `three-row layout adds the fifth reading`() = render(WidgetSizes.EXTRA_TALL) {
        onNode(hasText("-85 dBm")).assertExists()
    }

    @Test
    fun `square layout keeps the compact header identity and last heard only`() = render(WidgetSizes.SQUARE) {
        onNode(hasText(title)).assertExists()
        onNode(hasText("76%")).assertExists()
        onNode(hasText("Basecamp Solar")).assertExists()
        onNode(hasText(directLabel)).assertDoesNotExist()
        onNode(hasText("1.2 km")).assertDoesNotExist()
    }

    @Test
    fun `wide square layout adds hops beside last heard`() = render(WidgetSizes.WIDE) {
        onNode(hasText(directLabel)).assertExists()
        onNode(hasText("1.2 km")).assertDoesNotExist()
    }

    @Test
    fun `one-row layout has no header text but keeps chip battery and name`() = render(WidgetSizes.ROW) {
        onNode(hasText(title)).assertDoesNotExist()
        onNode(hasText("BCS1")).assertExists()
        onNode(hasText("76%")).assertExists()
        onNode(hasText("Basecamp Solar")).assertExists()
        onNode(hasText(directLabel)).assertDoesNotExist()
    }

    @Test
    fun `wide one-row layout adds hops`() = render(WidgetSizes.WIDE_ROW) {
        onNode(hasText(directLabel)).assertExists()
    }

    @Test
    fun `one-row unknown node still asks to choose one`() =
        render(WidgetSizes.ROW, NodeTrackerWidgetState.NodeNotFound) {
            onNode(hasText(notFoundLabel)).assertExists()
        }

    @Test
    fun `unknown node keeps the header and opens the picker for this widget`() =
        render(WidgetSizes.WIDE, NodeTrackerWidgetState.NodeNotFound) {
            onNode(hasText(title)).assertExists()
            onNode(hasText(notFoundLabel)).assertExists()
            onNode(
                hasStartActivityClickAction<NodeTrackerConfigActivity>(
                    actionParametersOf(ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) to WIDGET_ID),
                ),
            )
                .assertExists()
        }

    @Test
    fun `stale node still renders its readings and is marked`() =
        render(WidgetSizes.TALL, createMockTrackerState().copy(isStale = true)) {
            onNode(hasText("BCS1")).assertExists()
            onNode(hasText("76%")).assertExists()
            onNode(hasTestTag(NodeTrackerWidget.STALE_TEST_TAG)).assertExists()
        }

    private companion object {
        const val WIDGET_ID = 7

        /** Same budget as LocalStatsWidgetContentTest, for the same reason: Robolectric plus resource loading. */
        val TEST_TIMEOUT = 30.seconds
    }
}
