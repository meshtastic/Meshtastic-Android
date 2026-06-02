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
 * Whether the current platform's MapLibre Compose target implements programmatic map sources and layers
 * (`rememberGeoJsonSource`, `CircleLayer`, `SymbolLayer`, `LineLayer`, `HillshadeLayer`, …).
 *
 * As of maplibre-compose 0.13.0 the desktop (JVM) target stubs the **entire** sources/layers API with `TODO()`, so
 * composing any overlay throws `NotImplementedError` ("An operation is not implemented") and tears down the window. The
 * base map style still renders natively from its style URI. Every source/layer composition must therefore be guarded
 * behind this flag; when `false`, only the base map is shown.
 *
 * Re-evaluate on each maplibre-compose upgrade — once the desktop target implements layers/sources, set this `true` for
 * JVM and the guards become no-ops.
 */
expect val mapOverlaysSupported: Boolean
