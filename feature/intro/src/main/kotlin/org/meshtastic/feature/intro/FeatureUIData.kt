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

package org.meshtastic.feature.intro

import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.StringResource

/**
 * Data class representing the UI elements for a feature row in the app introduction.
 *
 * @param icon The vector asset for the feature icon.
 * @param titleRes Optional string resource ID for the feature title.
 * @param subtitleRes String resource ID for the feature subtitle.
 */
internal data class FeatureUIData(
    val icon: ImageVector,
    val titleRes: StringResource? = null,
    val subtitleRes: StringResource,
)
