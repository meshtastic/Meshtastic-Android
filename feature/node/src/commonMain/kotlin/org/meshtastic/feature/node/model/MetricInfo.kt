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
package org.meshtastic.feature.node.model

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** A single labelled reading rendered as one info card; the icon comes from either a vector or a drawable resource. */
internal sealed interface MetricInfo {
    val label: StringResource
    val value: String
    val rotateIcon: Float
}

internal data class VectorMetricInfo(
    override val label: StringResource,
    override val value: String,
    val icon: ImageVector,
    override val rotateIcon: Float = 0f,
) : MetricInfo

internal data class DrawableMetricInfo(
    override val label: StringResource,
    override val value: String,
    val icon: DrawableResource,
    override val rotateIcon: Float = 0f,
) : MetricInfo
