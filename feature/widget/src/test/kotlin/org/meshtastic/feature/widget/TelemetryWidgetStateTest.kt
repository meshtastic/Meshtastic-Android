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

import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelemetryWidgetStateTest {

    private val weather = environment {
        temperature = 21.5f
        relative_humidity = 48f
        barometric_pressure = 1016f
    }

    private fun showing(
        node: org.meshtastic.core.model.Node,
        history: List<org.meshtastic.proto.Telemetry> = emptyList(),
        hidden: Set<TelemetryReading> = emptySet(),
        seen: Set<TelemetryReading> = emptySet(),
    ) = assertIs<TelemetryWidgetState.Showing>(
        telemetryWidgetState(node.num, hidden, seen, inputsOf(node), history, now = NOW),
    )

    @Test
    fun `readings follow the design order across groups`() {
        val node =
            telemetryNode(
                environment = weather,
                airQuality = airQuality { co2 = 650 },
                power = power { ch1_voltage = 12.8f },
            )
        assertEquals(
            listOf(
                TelemetryReading.Temperature,
                TelemetryReading.Humidity,
                TelemetryReading.Pressure,
                TelemetryReading.DewPoint,
                TelemetryReading.Co2,
                TelemetryReading.PowerVoltage(0),
            ),
            showing(node).readings,
        )
    }

    @Test
    fun `a hidden reading is left out and the rest keep their order`() {
        val state = showing(telemetryNode(environment = weather), hidden = setOf(TelemetryReading.Humidity))
        assertEquals(
            listOf(TelemetryReading.Temperature, TelemetryReading.Pressure, TelemetryReading.DewPoint),
            state.readings,
        )
    }

    @Test
    fun `a reading the node stopped sending keeps its tile with no value`() {
        val now = telemetryNode(environment = weather)
        val earlier = row(NOW - 600, environment = environment { lux = 320f })
        val state = showing(now, history = listOf(earlier))
        assertTrue(TelemetryReading.Lux in state.readings)
        assertFalse(state.snapshot.hasValue(TelemetryReading.Lux))
    }

    @Test
    fun `a reading remembered by the instance keeps its tile after the log drops it`() {
        val state = showing(telemetryNode(environment = weather), seen = setOf(TelemetryReading.Rain))
        assertTrue(TelemetryReading.Rain in state.readings)
        assertFalse(state.snapshot.hasValue(TelemetryReading.Rain))
    }

    @Test
    fun `a reported zero is a value`() {
        val node =
            telemetryNode(
                environment = environment { soil_moisture = 0 },
                airQuality = airQuality { pm25_standard = 0 },
                power = power { ch1_current = 0f },
            )
        val snapshot = showing(node).snapshot
        assertEquals(0, snapshot.value(TelemetryReading.SoilMoisture))
        assertEquals(0, snapshot.value(TelemetryReading.Pm25))
        assertEquals(0f, snapshot.value(TelemetryReading.PowerCurrent(0)))
    }

    @Test
    fun `the log's sentinels read as absent`() {
        val logged = environment {
            temperature = Float.NaN
            iaq = Int.MIN_VALUE
        }
        assertEquals(emptySet(), everReported(listOf(row(NOW, environment = logged))))
    }

    @Test
    fun `the newest matching log row dates the reading`() {
        val state =
            showing(telemetryNode(environment = weather), history = listOf(row(NOW - 300, environment = weather)))
        assertEquals((NOW - 300).toInt(), state.updatedAt)
        assertFalse(state.isStale)
    }

    @Test
    fun `a log row that does not match the node's values dates nothing`() {
        val older = environment { temperature = 19f }
        val state = showing(telemetryNode(environment = weather), history = listOf(row(NOW - 300, environment = older)))
        assertNull(state.updatedAt)
    }

    @Test
    fun `a reading over two hours old is stale`() {
        val history = listOf(row(NOW - STALE_AFTER.inWholeSeconds - 1, environment = weather))
        assertTrue(showing(telemetryNode(environment = weather), history = history).isStale)
    }

    @Test
    fun `without a logged time a node unheard for two hours is stale`() {
        val quiet = telemetryNode(environment = weather, lastHeard = (NOW - STALE_AFTER.inWholeSeconds - 60).toInt())
        assertTrue(showing(quiet).isStale)
        assertFalse(showing(telemetryNode(environment = weather)).isStale)
    }

    @Test
    fun `an empty node DB shows the last known readings for the same node`() {
        val node = telemetryNode(num = 5, environment = weather)
        val known = TelemetryLastKnown.of(node, telemetrySnapshot(node, emptyList()))
        val state = telemetryWidgetState(5, emptySet(), emptySet(), TelemetryWidgetInputs(), emptyList(), known, NOW)
        assertEquals(weather, assertIs<TelemetryWidgetState.Showing>(state).snapshot.environment)
        assertEquals(
            TelemetryWidgetState.NodeNotFound,
            telemetryWidgetState(6, emptySet(), emptySet(), TelemetryWidgetInputs(), emptyList(), known, NOW),
        )
    }

    @Test
    fun `a node missing from a populated DB is not found`() {
        val other = telemetryNode(num = 2, environment = weather)
        assertEquals(
            TelemetryWidgetState.NodeNotFound,
            telemetryWidgetState(9, emptySet(), emptySet(), inputsOf(other), emptyList(), now = NOW),
        )
    }

    @Test
    fun `last known readings survive the round trip through widget state`() {
        val node =
            telemetryNode(
                environment = weather,
                airQuality = airQuality { co2 = 0 },
                power = power { ch2_voltage = 5.1f },
                powerChannelLabels = listOf("Solar", "Battery"),
            )
        val known = TelemetryLastKnown.of(node, telemetrySnapshot(node, listOf(row(NOW, environment = weather))))
        val prefs = mutablePreferencesOf().apply { storeLastKnown(known) }
        assertEquals(known, prefs.lastKnownTelemetry())
    }

    @Test
    fun `a node with no channel labels reads back unchanged`() {
        val node = telemetryNode(power = power { ch1_voltage = 3.3f }, powerChannelLabels = listOf(""))
        val known = TelemetryLastKnown.of(node, telemetrySnapshot(node, emptyList()))
        val prefs = mutablePreferencesOf().apply { storeLastKnown(known) }
        assertEquals(known, prefs.lastKnownTelemetry())
    }

    @Test
    fun `the picker offers telemetry nodes with ours first and defaults to ours`() {
        val ours = telemetryNode(num = 1, environment = weather, lastHeard = 10)
        val newer = telemetryNode(num = 2, power = power { ch1_voltage = 3.3f }, lastHeard = 20)
        val silent = testNode(3, lastHeard = 30)
        val nodes = listOf(silent, newer, ours)
        assertEquals(listOf(1, 2), telemetryCandidates(nodes, ourNodeNum = 1).map { it.num })
        assertEquals(1, defaultTelemetryNode(nodes, ourNodeNum = 1)?.num)
        assertNull(defaultTelemetryNode(nodes, ourNodeNum = 3))
    }

    @Test
    fun `a value splits from its unit only after a number`() {
        assertEquals("1,016" to "hPa", splitUnit("1,016 hPa"))
        assertEquals("9.4" to "mph", splitUnit("9.4\u00A0mph"))
        assertEquals("21°C" to null, splitUnit("21°C"))
        assertEquals(MISSING_VALUE to null, splitUnit(MISSING_VALUE))
    }

    @Test
    fun `the page button wraps to the first page`() {
        assertEquals(1, TelemetryPageAction.nextPage(0, 3))
        assertEquals(0, TelemetryPageAction.nextPage(2, 3))
        // A page stored before readings were hidden counts against the pages there are now.
        assertEquals(2, TelemetryPageAction.nextPage(4, 3))
        assertEquals(0, TelemetryPageAction.nextPage(null, 1))
    }
}
