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

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.NumberFormatter
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ammonium
import org.meshtastic.core.resources.biochemical_oxygen_demand
import org.meshtastic.core.resources.chemical_oxygen_demand
import org.meshtastic.core.resources.dissolved_oxygen
import org.meshtastic.core.resources.electrical_conductivity
import org.meshtastic.core.resources.nitrate
import org.meshtastic.core.resources.nitrogen
import org.meshtastic.core.resources.orp
import org.meshtastic.core.resources.phosphorus
import org.meshtastic.core.resources.potassium
import org.meshtastic.core.resources.salinity
import org.meshtastic.core.resources.soil_ph
import org.meshtastic.core.resources.solar_irradiance
import org.meshtastic.core.resources.turbidity
import org.meshtastic.core.resources.water_ph
import org.meshtastic.core.ui.icon.Humidity
import org.meshtastic.core.ui.icon.LightMode
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.SoilMoisture
import org.meshtastic.core.ui.icon.Voltage
import org.meshtastic.feature.node.model.VectorMetricInfo

/** One reading as a card, or null when the probe did not report it. A reported 0 is a measurement and stays visible. */
private fun reading(
    label: StringResource,
    value: Float?,
    unit: String,
    icon: ImageVector,
    fractionDigits: Int,
): VectorMetricInfo? = value
    ?.takeUnless { it.isNaN() }
    ?.let {
        val number = NumberFormatter.format(it, fractionDigits)
        VectorMetricInfo(label, if (unit.isEmpty()) number else "$number $unit", icon)
    }

/**
 * Displays soil-probe and water-sonde chemistry for a node. Readings one probe reports together share a column: the NPK
 * probe's nutrients, conductivity with the salinity derived from it, the two oxygen demands, and the two nitrogen
 * species. Units are the proto's, so no locale conversion applies.
 */
@Suppress("LongMethod")
@Composable
internal fun SoilWaterMetrics(node: Node) {
    val soil = MeshtasticIcons.SoilMoisture
    val water = MeshtasticIcons.Humidity
    val groups: List<MetricGroup> =
        with(node.soilWaterMetrics) {
            listOf(
                listOfNotNull(reading(Res.string.soil_ph, soil_ph, "", soil, 1)),
                listOfNotNull(
                    reading(Res.string.nitrogen, nitrogen, "mg/kg", soil, 0),
                    reading(Res.string.phosphorus, phosphorus, "mg/kg", soil, 0),
                    reading(Res.string.potassium, potassium, "mg/kg", soil, 0),
                ),
                listOfNotNull(reading(Res.string.water_ph, ph, "", water, 1)),
                listOfNotNull(
                    reading(Res.string.electrical_conductivity, electrical_conductivity, "mS/cm", water, 2),
                    reading(Res.string.salinity, salinity, "mg/l", water, 0),
                ),
                listOfNotNull(reading(Res.string.dissolved_oxygen, dissolved_oxygen, "mg/l", water, 1)),
                listOfNotNull(reading(Res.string.orp, orp, "mV", MeshtasticIcons.Voltage, 0)),
                listOfNotNull(
                    reading(Res.string.chemical_oxygen_demand, chemical_oxygen_demand, "mg/l", water, 0),
                    reading(Res.string.biochemical_oxygen_demand, biochemical_oxygen_demand, "mg/l", water, 0),
                ),
                listOfNotNull(reading(Res.string.turbidity, turbidity, "NTU", water, 1)),
                listOfNotNull(
                    reading(Res.string.nitrate, nitrate, "ppm", water, 1),
                    reading(Res.string.ammonium, ammonium, "ppm", water, 1),
                ),
                listOfNotNull(
                    reading(Res.string.solar_irradiance, solar_irradiance, "W/m²", MeshtasticIcons.LightMode, 0),
                ),
            )
        }

    MetricCardFlow(groups = groups)
}
