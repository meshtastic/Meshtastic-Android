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
package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclassesOfSealed

/**
 * Shared polymorphic serialization configuration for Navigation 3 saved-state support. Uses sealed interface
 * hierarchies so that new routes are automatically registered at compile time — no manual `subclass()` calls needed.
 */
@OptIn(ExperimentalSerializationApi::class)
val MeshtasticNavSavedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclassesOfSealed<ChannelsRoute>()
            subclassesOfSealed<ConnectionsRoute>()
            subclassesOfSealed<ContactsRoute>()
            subclassesOfSealed<MapRoute>()
            subclassesOfSealed<NodesRoute>()
            subclassesOfSealed<NodeDetailRoute>()
            subclassesOfSealed<SettingsRoute>()
            subclassesOfSealed<FirmwareRoute>()
            subclassesOfSealed<WifiProvisionRoute>()
        }
    }
}
