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
 * Desktop (JVM) does NOT implement the MapLibre Compose sources/layers API in maplibre-compose 0.13.0 — every
 * `Layer`/`Source` is stubbed with `TODO()`. Overlays are gated off so the base map renders without crashing.
 */
actual val mapOverlaysSupported: Boolean = false
