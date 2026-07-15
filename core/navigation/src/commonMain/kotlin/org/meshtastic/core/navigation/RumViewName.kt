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

/**
 * Derives the analytics view name for a navigation destination.
 *
 * The name is the route's fully-qualified class name (e.g. `org.meshtastic.core.navigation.NodesRoute.Nodes`), matching
 * the convention historically recorded by Datadog RUM before the Navigation 3 migration, so new per-screen data lines
 * up with existing dashboards. Falls back to the simple name (and finally `toString()`) on the rare platform where
 * [kotlin.reflect.KClass.qualifiedName] is unavailable.
 */
fun NavKey.rumViewName(): String = this::class.qualifiedName ?: this::class.simpleName ?: toString()
