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
package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.co2
import org.meshtastic.core.resources.micrograms_per_cubic_meter
import org.meshtastic.core.resources.pm10
import org.meshtastic.core.resources.pm1_0
import org.meshtastic.core.resources.pm2_5
import org.meshtastic.core.resources.ppm
import org.meshtastic.core.ui.component.Co2Severity
import org.meshtastic.core.ui.icon.AirQuality
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.feature.node.model.VectorMetricInfo

/**
 * Displays air quality info cards for a node showing PM1.0, PM2.5, PM10 and CO₂ values. Cards with zero values are
 * hidden. CO₂ value text is color-coded by severity.
 */
@Composable
internal fun AirQualityInfoCards(node: Node) {
    val metrics = node.airQualityMetrics
    val ugm3 = stringResource(Res.string.micrograms_per_cubic_meter)
    val ppmUnit = stringResource(Res.string.ppm)

    val cards = buildList {
        metrics.pm10_standard?.let { pm ->
            if (pm != 0) {
                add(VectorMetricInfo(Res.string.pm1_0, "$pm $ugm3", MeshtasticIcons.AirQuality))
            }
        }
        metrics.pm25_standard?.let { pm ->
            if (pm != 0) {
                add(VectorMetricInfo(Res.string.pm2_5, "$pm $ugm3", MeshtasticIcons.AirQuality))
            }
        }
        metrics.pm100_standard?.let { pm ->
            if (pm != 0) {
                add(VectorMetricInfo(Res.string.pm10, "$pm $ugm3", MeshtasticIcons.AirQuality))
            }
        }
        metrics.co2?.let { co2 ->
            if (co2 != 0) {
                add(VectorMetricInfo(Res.string.co2, "$co2 $ppmUnit", MeshtasticIcons.AirQuality))
            }
        }
    }

    if (cards.isEmpty()) return

    val co2Value = metrics.co2 ?: 0
    val co2Color = Co2Severity.fromPpm(co2Value)?.color

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        cards.forEach { metric ->
            val valueColor =
                if (metric.label == Res.string.co2 && co2Color != null) {
                    co2Color
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            InfoCard(
                icon = metric.icon,
                text = stringResource(metric.label),
                value = metric.value,
                valueColor = valueColor,
            )
        }
    }
}
