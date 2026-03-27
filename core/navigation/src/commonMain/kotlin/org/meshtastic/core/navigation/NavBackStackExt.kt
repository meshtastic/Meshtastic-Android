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
 * Replaces the last entry in the back stack with the given route. If the back stack is empty, it simply adds the route.
 */
fun MutableList<NavKey>.replaceLast(route: NavKey) {
    if (isNotEmpty()) {
        if (this[lastIndex] != route) {
            this[lastIndex] = route
        }
    } else {
        add(route)
    }
}

/**
 * Replaces the entire back stack with the given routes in a way that minimizes structural changes and prevents the back
 * stack from temporarily becoming empty.
 */
fun MutableList<NavKey>.replaceAll(routes: List<NavKey>) {
    if (routes.isEmpty()) {
        clear()
        return
    }
    for (i in routes.indices) {
        if (i < size) {
            // Only mutate if the route actually changed, protecting Nav3's internal state matching.
            if (this[i] != routes[i]) {
                this[i] = routes[i]
            }
        } else {
            add(routes[i])
        }
    }
    while (size > routes.size) {
        removeAt(lastIndex)
    }
}
