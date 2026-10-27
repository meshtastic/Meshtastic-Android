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
package org.meshtastic.feature.map.tiles

/** A decoded image as straight (non-premultiplied) ARGB, one packed `Int` per pixel, top row first. */
internal class RasterPixels(val width: Int, val height: Int, val argb: IntArray)

/** Decodes PNG [bytes], or returns null when they are not a PNG this platform can read. */
internal expect fun decodePngPixels(bytes: ByteArray): RasterPixels?

/** Encodes [pixels] as PNG. */
internal expect fun encodePngPixels(pixels: RasterPixels): ByteArray
