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
package org.meshtastic.app

/**
 * F-Droid-only process globals.
 *
 * Nothing to configure since the map moved to MapLibre: OSMdroid needed a per-app user agent set
 * before its first tile fetch, whereas maplibre-native manages its own HTTP stack. Kept as a
 * flavor-dispatched no-op so [MeshUtilApplication] does not need to know which flavor it is in.
 */
@Suppress("UnusedParameter")
internal fun configureFlavorApplication(applicationId: String) = Unit
