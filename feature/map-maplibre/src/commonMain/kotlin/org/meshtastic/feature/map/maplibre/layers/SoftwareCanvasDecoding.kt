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
package org.meshtastic.feature.map.maplibre.layers

import coil3.request.ImageRequest

/**
 * Asks Coil for an image a software canvas can draw.
 *
 * MapLibre rasterizes a style image outside the composition, into a software canvas. Android's Coil decodes to a
 * hardware bitmap by default, and drawing one there throws `Software rendering doesn't support hardware bitmaps` — so
 * an imported layer's first icon killed the app. Nowhere else has the notion, hence expect/actual rather than a flag.
 */
internal expect fun ImageRequest.Builder.decodeForSoftwareCanvas(): ImageRequest.Builder
