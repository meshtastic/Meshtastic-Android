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

import org.meshtastic.core.model.Node
import org.meshtastic.proto.AirQualityMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.PowerMetrics
import org.meshtastic.proto.Telemetry

internal const val NOW = 1_800_000_000L

internal fun environment(block: EnvironmentMetrics.Builder.() -> Unit): EnvironmentMetrics =
    EnvironmentMetrics.Builder().apply(block).build()

internal fun airQuality(block: AirQualityMetrics.Builder.() -> Unit): AirQualityMetrics =
    AirQualityMetrics.Builder().apply(block).build()

internal fun power(block: PowerMetrics.Builder.() -> Unit): PowerMetrics = PowerMetrics.Builder().apply(block).build()

internal fun telemetryNode(
    num: Int = 1,
    environment: EnvironmentMetrics? = null,
    airQuality: AirQualityMetrics? = null,
    power: PowerMetrics? = null,
    lastHeard: Int = NOW.toInt(),
    powerChannelLabels: List<String> = emptyList(),
): Node {
    val base = testNode(num, shortName = "WX$num", lastHeard = lastHeard)
    return base.copy(
        environmentMetrics = environment ?: base.environmentMetrics,
        airQualityMetrics = airQuality ?: base.airQualityMetrics,
        powerMetrics = power ?: base.powerMetrics,
        powerChannelLabels = powerChannelLabels,
    )
}

internal fun row(
    time: Long,
    environment: EnvironmentMetrics? = null,
    airQuality: AirQualityMetrics? = null,
    power: PowerMetrics? = null,
): Telemetry = Telemetry.Builder()
    .apply {
        this.time = time.toInt()
        environment_metrics = environment
        air_quality_metrics = airQuality
        power_metrics = power
    }
    .build()

internal fun inputsOf(vararg nodes: Node, ourNodeNum: Int? = null) =
    TelemetryWidgetInputs(nodes = nodes.associateBy { it.num }, ourNodeNum = ourNodeNum)
