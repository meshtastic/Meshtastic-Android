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
package org.meshtastic.feature.map

/**
 * Whether the map should switch itself onto an offline-capable basemap right now, with no toggle for the user to
 * remember to flip.
 *
 * Ported from the sibling iOS app's `OfflineMapFallbackPolicy`: there, the decision also considers a manual "always use
 * offline" toggle the user can set. Android has no such toggle — the raster-basemap picker already lets a user
 * permanently select a local source, which the Google flavor's `MapViewModel` treats as "the user took the wheel" and
 * never auto-switches away from. So the two live conditions here are the ones iOS combines with a network check: is the
 * network down, and is there anywhere to fall back to.
 */
fun shouldAutoUseOfflineBasemap(networkAvailable: Boolean, hasOfflineBasemap: Boolean): Boolean =
    !networkAvailable && hasOfflineBasemap
