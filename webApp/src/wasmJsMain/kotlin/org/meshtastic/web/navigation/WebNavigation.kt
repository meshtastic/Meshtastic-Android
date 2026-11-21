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
package org.meshtastic.web.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import co.touchlab.kermit.Logger
import org.meshtastic.core.navigation.TopLevelDestination

/**
 * The entry for a key no web graph registers, which a deep link or a shared screen can still push. Navigation 3's
 * default throws there, and the exception leaves every later click dead until a reload; this logs the key and steps
 * back instead.
 */
fun unknownWebDestination(key: NavKey, backStack: NavBackStack<NavKey>): NavEntry<NavKey> = NavEntry(key) {
    LaunchedEffect(key) {
        Logger.w { "No web destination for ${key::class.simpleName}; going back" }
        if (backStack.size > 1) backStack.remove(key)
    }
}

/** Every [TopLevelDestination] except Map, which has no entries on web. */
val webVisibleDestinations: List<TopLevelDestination> =
    TopLevelDestination.entries.filter { it != TopLevelDestination.Map }

/**
 * Whether the web registers the screen [deepLink] (a `NotificationDeepLinks` path) opens. The map has no wasmJs target,
 * so a waypoint notification opens the app where it was instead.
 */
fun isWebDeepLink(deepLink: String): Boolean =
    deepLink.substringBefore('?').substringBefore('/').lowercase() in webDeepLinkSegments

private val webDeepLinkSegments =
    setOf("messages", "share", "quickchat", "nodes", "settings", "channels", "connections", "discovery", "firmware")
