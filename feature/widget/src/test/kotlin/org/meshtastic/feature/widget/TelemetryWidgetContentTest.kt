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
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.iaq_level_excellent
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.widget_telemetry_not_found
import org.meshtastic.core.resources.widget_telemetry_stale
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Unit tests for [TelemetryWidget.WidgetContent] on Glance's own harness, which asserts on the composed tree rather
 * than pixels. As in [LocalStatsWidgetContentTest], any string rendering at all proves the [WidgetResourceEnvironment]
 * bridge still resolves Compose Multiplatform resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class TelemetryWidgetContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // Resolved at construction: getString() wraps runBlocking, which can deadlock inside runGlanceAppWidgetUnitTest.
    private val temperatureLabel: String = getString(Res.string.temperature)
    private val luxLabel: String = getString(Res.string.lux)
    private val excellent: String = getString(Res.string.iaq_level_excellent)
    private val staleLabel: String = getString(Res.string.widget_telemetry_stale)
    private val notFoundLabel: String = getString(Res.string.widget_telemetry_not_found)

    // The locale's grouping, as node details shows it.
    private val pressure = NumberFormatter.format(1016.2f, 0)

    private fun render(
        size: DpSize,
        state: TelemetryWidgetState = createMockTelemetryState(),
        page: Int = 0,
        assertions: GlanceAppWidgetUnitTest.() -> Unit,
    ) = runGlanceAppWidgetUnitTest(timeout = TEST_TIMEOUT) {
        setAppWidgetSize(size)
        setContext(context)
        provideComposable { TelemetryWidget().WidgetContent(state, page, WIDGET_ID) }
        assertions()
    }

    @Test
    fun `narrower than two columns shows the first reading only`() = render(WidgetSizes.TELEMETRY_ONE_TALL) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(1)
        onNode(hasText(temperatureLabel)).assertExists()
        onNode(hasText("21°C")).assertExists()
        onNode(hasText("BCS1")).assertExists()
    }

    @Test
    fun `wide under two rows shows four in a row`() = render(WidgetSizes.TELEMETRY_ROW_TALL) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        onNode(hasText("48%")).assertExists()
        onNode(hasText(pressure)).assertExists()
    }

    @Test
    fun `a one-row cell keeps four compact tiles`() = render(WidgetSizes.TELEMETRY_ROW) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        onNode(hasText(temperatureLabel)).assertDoesNotExist()
        onNode(hasText("21°C")).assertExists()
    }

    @Test
    fun `two by two shows four tiles and a page button for the rest`() = render(WidgetSizes.TELEMETRY_GRID) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        onNode(hasText("1/3")).assertExists()
        onNode(
            hasRunCallbackClickAction<TelemetryPageAction>(
                actionParametersOf(TelemetryPageAction.PAGE_COUNT_KEY to 3),
            ),
        )
            .assertExists()
    }

    @Test
    fun `the second page shows the next readings with their bands`() = render(WidgetSizes.TELEMETRY_GRID, page = 1) {
        onNode(hasText("2/3")).assertExists()
        onNode(hasText("42")).assertExists()
        onNode(hasText(excellent)).assertExists()
        onNode(hasText("640")).assertExists()
        onNode(hasText("ppm · ")).assertExists()
    }

    @Test
    fun `a short last page keeps the grid and wraps a stale stored page`() =
        render(WidgetSizes.TELEMETRY_GRID, page = 5) {
            // Nine readings make three pages; page 5 wraps to the third, which holds one tile in a 2x2 grid.
            onNode(hasText("3/3")).assertExists()
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(1)
        }

    @Test
    fun `four readings or fewer show no page button`() {
        val state = createMockTelemetryState().let { it.copy(readings = it.readings.take(4)) }
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText("1/1")).assertDoesNotExist()
        }
    }

    @Test
    fun `a reading the node stopped sending shows a dash and a zero shows zero`() {
        val node =
            telemetryNode(
                environment = environment { temperature = 0f },
                airQuality = airQuality { pm25_standard = 0 },
            )
        val history = listOf(row(NOW - 60, environment = environment { lux = 300f }))
        val state = telemetryWidgetState(node.num, emptySet(), emptySet(), inputsOf(node), history, now = NOW)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText("0°C")).assertExists()
            onNode(hasText("µg/m³")).assertExists()
            onNode(hasText(luxLabel)).assertExists()
            onNode(hasText(MISSING_VALUE)).assertExists()
        }
    }

    @Test
    fun `readings over two hours old are labeled without dimming`() {
        val state = createMockTelemetryState().copy(isStale = true)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText(staleLabel)).assertExists()
            onNode(hasTestTag(TelemetryWidget.STALE_TEST_TAG)).assertExists()
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        }
    }

    @Test
    fun `a fresh reading carries no stale label`() = render(WidgetSizes.TELEMETRY_GRID) {
        onNode(hasTestTag(TelemetryWidget.STALE_TEST_TAG)).assertDoesNotExist()
    }

    @Test
    fun `no node asks the user to choose one`() =
        render(WidgetSizes.TELEMETRY_GRID, TelemetryWidgetState.NodeNotFound) {
            onNode(hasText(notFoundLabel)).assertExists()
        }

    private companion object {
        const val WIDGET_ID = 11
        val TEST_TIMEOUT = 30.seconds
    }
}
