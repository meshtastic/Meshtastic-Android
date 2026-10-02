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
 * Derives the analytics view name for a navigation destination: the route's class name without its package, keeping the
 * enclosing route interface so leaf names stay unique (e.g. `NodesRoute.Nodes`, `SettingsRoute.Bluetooth`).
 */
fun NavKey.rumViewName(): String = rumViewName(this::class.qualifiedName ?: this::class.simpleName ?: toString())

/**
 * Strips the package from [className], treating leading lowercase segments as the package. Minified builds can report
 * the JVM binary name (`NodesRoute$Nodes`), so `$` is normalised to `.` to give every build type the same name.
 */
internal fun rumViewName(className: String): String = className
    .split('.', '$')
    .dropWhile { it.firstOrNull()?.isLowerCase() == true }
    .joinToString(".")
    .ifEmpty {
        className
    }
