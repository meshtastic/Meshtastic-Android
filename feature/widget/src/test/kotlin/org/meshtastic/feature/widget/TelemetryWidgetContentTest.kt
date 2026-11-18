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
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.meshtastic.core.common.util.MeasurementSystem
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.common.util.TemperatureUnit
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.humidity
import org.meshtastic.core.resources.iaq_level_excellent
import org.meshtastic.core.resources.lux
import org.meshtastic.core.resources.temperature
import org.meshtastic.core.resources.widget_telemetry_dew
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
    private val humidityLabel: String = getString(Res.string.humidity)
    private val dewLine: String = getString(Res.string.widget_telemetry_dew, "9°C")
    private val dewLineFahrenheit: String = getString(Res.string.widget_telemetry_dew, "51°F")
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
    fun `two to three cells wide under two rows shows two in a row`() = render(WidgetSizes.TELEMETRY_ROW_TALL) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(2)
        onNode(hasText("48%")).assertExists()
        onNode(hasText("BCS1")).assertExists()
    }

    @Test
    fun `four cells wide under two rows shows four in a row`() = render(WidgetSizes.TELEMETRY_WIDE_ROW_TALL) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        onNode(hasText("48%")).assertExists()
        onNode(hasText(pressure)).assertExists()
    }

    @Test
    fun `a one-row cell keeps four compact tiles`() = render(WidgetSizes.TELEMETRY_WIDE_ROW) {
        onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
        onNode(hasText(temperatureLabel)).assertDoesNotExist()
        onNode(hasText("21°C")).assertExists()
    }

    @Test
    fun `two by two shows the first four readings and a page button for the rest`() =
        render(WidgetSizes.TELEMETRY_GRID) {
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
            onNode(hasText("21°C")).assertExists()
            onNode(hasText("48%")).assertExists()
            onNode(hasText(pressure)).assertExists()
            // The Dew Point tile and the humidity tile's labeled "Dew 10°C" line both contain the value.
            onAllNodes(hasText("10°C")).assertCountEquals(2)
            onNode(hasText("42")).assertDoesNotExist()
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
            // The ninth reading, channel 1's current under its label.
            onNode(hasText("Solar")).assertExists()
            onNode(hasText("412.5")).assertExists()
            onNode(hasText("21°C")).assertDoesNotExist()
        }

    @Test
    fun `four readings or fewer show no page button`() {
        val state = createMockTelemetryState().let { it.copy(readings = it.readings.take(4)) }
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
            onNode(hasText("1/1")).assertDoesNotExist()
            onNode(hasRunCallbackClickAction<TelemetryPageAction>(actionParametersOf())).assertDoesNotExist()
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
    fun `readings over two hours old are labeled and every tile keeps its value`() {
        val state = createMockTelemetryState().copy(isStale = true)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText(staleLabel)).assertExists()
            onNode(hasTestTag(TelemetryWidget.STALE_TEST_TAG)).assertExists()
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(4)
            onNode(hasText("21°C")).assertExists()
            onNode(hasText("48%")).assertExists()
        }
    }

    @Test
    fun `a fresh reading carries no stale label`() = render(WidgetSizes.TELEMETRY_GRID) {
        onNode(hasTestTag(TelemetryWidget.STALE_TEST_TAG)).assertDoesNotExist()
    }

    @Test
    fun `no node asks the user to choose one and opens setup for this widget`() =
        render(WidgetSizes.TELEMETRY_GRID, TelemetryWidgetState.NodeNotFound) {
            onNode(hasText(notFoundLabel)).assertExists()
            onNode(
                hasStartActivityClickAction<TelemetryConfigActivity>(
                    actionParametersOf(ActionParameters.Key<Int>(AppWidgetManager.EXTRA_APPWIDGET_ID) to WIDGET_ID),
                ),
            )
                .assertExists()
        }

    @Test
    fun `the humidity tile carries the dew point as its own line`() {
        val node =
            telemetryNode(
                environment =
                environment {
                    temperature = 20f
                    relative_humidity = 50f
                },
            )
        val state = telemetryWidgetState(node.num, emptySet(), emptySet(), inputsOf(node), emptyList(), now = NOW)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText("50%")).assertExists()
            // The Dew Point tile shows the bare value; the humidity tile's own line is labeled and spoken with it.
            onAllNodes(hasText("9°C")).assertCountEquals(2)
            onNode(hasContentDescriptionEqualTo("$humidityLabel 50%, $dewLine")).assertExists()
        }
    }

    @Test
    fun `every breakpoint carries the mark once with readings, without them and with no node`() {
        val withReadings = createMockTelemetryState()
        val states = listOf(withReadings, withReadings.copy(readings = emptyList()), TelemetryWidgetState.NodeNotFound)
        val sizes =
            listOf(
                WidgetSizes.TELEMETRY_ONE,
                WidgetSizes.TELEMETRY_ONE_TALL,
                WidgetSizes.TELEMETRY_ROW,
                WidgetSizes.TELEMETRY_ROW_TALL,
                WidgetSizes.TELEMETRY_WIDE_ROW,
                WidgetSizes.TELEMETRY_WIDE_ROW_TALL,
                WidgetSizes.TELEMETRY_GRID,
                WidgetSizes.TELEMETRY_WIDE_GRID,
            )
        for (state in states) {
            for (size in sizes) {
                render(size, state) {
                    onAllNodes(hasTestTag(WIDGET_MARK_TEST_TAG)).assertCountEquals(1)
                }
            }
        }
    }

    @Test
    fun `a humidity tile with no temperature has no dew point line`() {
        val node = telemetryNode(environment = environment { relative_humidity = 50f })
        val state = telemetryWidgetState(node.num, emptySet(), emptySet(), inputsOf(node), emptyList(), now = NOW)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText("50%")).assertExists()
            onAllNodes(hasTestTag(TelemetryWidget.TILE_TEST_TAG)).assertCountEquals(1)
            onNode(hasContentDescriptionEqualTo("$humidityLabel 50%")).assertExists()
            onNode(hasText(MISSING_VALUE)).assertDoesNotExist()
        }
    }

    @Test
    fun `a Fahrenheit and imperial locale converts temperature and wind`() {
        val node =
            telemetryNode(
                environment =
                environment {
                    temperature = 21.4f
                    relative_humidity = 50f
                    wind_speed = 4.2f
                },
            )
        val inputs =
            inputsOf(node).copy(units = MeasurementSystem.IMPERIAL, temperatureUnit = TemperatureUnit.FAHRENHEIT)
        val state = telemetryWidgetState(node.num, emptySet(), emptySet(), inputs, emptyList(), now = NOW)
        render(WidgetSizes.TELEMETRY_GRID, state) {
            onNode(hasText("71°F")).assertExists()
            // The humidity tile's dew point line follows the same unit.
            onNode(hasContentDescriptionEqualTo("$humidityLabel 50%, $dewLineFahrenheit")).assertExists()
            onNode(hasText("mph")).assertExists()
            onNode(hasText("km/h")).assertDoesNotExist()
        }
    }

    private companion object {
        const val WIDGET_ID = 11
        val TEST_TIMEOUT = 30.seconds
    }
}
