/*
 * Copyright (c) 2025 Meshtastic LLC
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

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

internal data class VectorMetricInfo(
    val label: StringResource,
    val value: String,
    val icon: ImageVector,
    val rotateIcon: Float = 0f,
)

internal data class DrawableMetricInfo(
    val label: StringResource,
    val value: String,
    @DrawableRes val icon: Int,
    val rotateIcon: Float = 0f,
)
