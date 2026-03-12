/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.ui.icon.Conversations
import org.meshtastic.core.ui.icon.Map
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.icon.Wifi

/** Maps a shared [TopLevelDestination] to its corresponding icon from [MeshtasticIcons]. */
val TopLevelDestination.icon: ImageVector
    get() =
        when (this) {
            TopLevelDestination.Conversations -> MeshtasticIcons.Conversations
            TopLevelDestination.Nodes -> MeshtasticIcons.Nodes
            TopLevelDestination.Map -> MeshtasticIcons.Map
            TopLevelDestination.Settings -> MeshtasticIcons.Settings
            TopLevelDestination.Connections -> MeshtasticIcons.Wifi
        }
