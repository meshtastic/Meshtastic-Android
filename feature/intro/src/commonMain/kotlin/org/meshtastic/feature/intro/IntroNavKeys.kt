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
package org.meshtastic.feature.intro

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

/** The intro flow's screens; sealed so its saved back stack can serialize them on every target. */
@Serializable sealed interface IntroKey : NavKey

@Serializable data object Welcome : IntroKey

@Serializable data object Bluetooth : IntroKey

@Serializable data object Location : IntroKey

@Serializable data object Notifications : IntroKey

@Serializable data object CriticalAlerts : IntroKey

@OptIn(ExperimentalSerializationApi::class)
internal val IntroSavedStateConfig = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) { subclassesOfSealed<IntroKey>() } }
}
