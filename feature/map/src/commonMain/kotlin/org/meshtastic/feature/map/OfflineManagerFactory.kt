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

import androidx.compose.runtime.Composable

/**
 * Returns `true` if the platform supports offline map tile management.
 * - Android: `true` (backed by MapLibre Native).
 * - iOS: `true` (backed by MapLibre Native).
 * - Desktop/JS: `false` (no offline support).
 */
@Composable expect fun isOfflineManagerAvailable(): Boolean

/**
 * Renders platform-specific offline map management UI if the platform supports it. The composable receives the current
 * style URI and [cameraState] for downloading the visible region.
 *
 * On unsupported platforms, this is a no-op.
 */
@Composable expect fun OfflineMapContent(styleUri: String, cameraState: org.maplibre.compose.camera.CameraState)
