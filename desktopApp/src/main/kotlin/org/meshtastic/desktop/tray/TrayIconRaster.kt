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
package org.meshtastic.desktop.tray

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Data
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface
import org.jetbrains.skia.svg.SVGDOM

/** Bytes per pixel in StatusNotifierItem's ARGB32 pixmap payload. */
private const val BYTES_PER_PIXEL = 4

// Where each channel sits in an incoming 0xAARRGGBB pixel, and where it goes in the outgoing payload.
// Naming both halves is what makes the byte order reviewable instead of a row of bare shifts.
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val ALPHA_BYTE = 0
private const val RED_BYTE = 1
private const val GREEN_BYTE = 2
private const val BLUE_BYTE = 3

/** Panel-sized raster: what a tray at typical scaling actually draws. */
private const val PANEL_ICON_SIZE = 22

/** Larger raster for notification popups and overview grids that scale the item up. */
private const val LARGE_ICON_SIZE = 48

/**
 * Icon sizes offered to the StatusNotifierItem host.
 *
 * The spec lets an item publish several and leaves the choice to the host, so give it a panel-sized raster and a larger
 * one for notification popups and overview grids.
 */
internal val TRAY_ICON_SIZES: List<Int> = listOf(PANEL_ICON_SIZE, LARGE_ICON_SIZE)

/** A rasterized tray icon in the form StatusNotifierItem's `(iiay)` pixmap struct expects. */
internal class TrayIconRaster(val width: Int, val height: Int, val argbBigEndian: ByteArray)

/**
 * Packs unpremultiplied `0xAARRGGBB` pixels into StatusNotifierItem's pixmap payload: ARGB32 in network byte order,
 * row-major, no row padding.
 *
 * Kept separate from the Skia call below so the byte order — the part that is easy to get wrong and impossible to see
 * in a screenshot — is unit-testable without a GPU or a display.
 */
internal fun argbPixelsToSniBytes(pixels: IntArray): ByteArray {
    val out = ByteArray(pixels.size * BYTES_PER_PIXEL)
    pixels.forEachIndexed { index, pixel ->
        val offset = index * BYTES_PER_PIXEL
        out[offset + ALPHA_BYTE] = (pixel ushr ALPHA_SHIFT).toByte()
        out[offset + RED_BYTE] = (pixel ushr RED_SHIFT).toByte()
        out[offset + GREEN_BYTE] = (pixel ushr GREEN_SHIFT).toByte()
        out[offset + BLUE_BYTE] = pixel.toByte()
    }
    return out
}

/**
 * Rasterizes [svg] to a [size]x[size] tray icon.
 *
 * Pixels come back one at a time through Skia's `Bitmap.getColor`, which yields an unpremultiplied `0xAARRGGBB` value.
 * That sidesteps both the host byte order of Skia's N32 buffer and its premultiplied alpha, neither of which
 * StatusNotifierItem wants, at a cost that does not matter for two icon-sized rasters produced once per launch.
 */
internal fun rasterizeSvgForTray(svg: ByteArray, size: Int): TrayIconRaster {
    val dom = SVGDOM(Data.makeFromBytes(svg))
    val surface = Surface.makeRasterN32Premul(size, size)
    val bitmap = Bitmap()
    try {
        // The tray glyphs declare absolute width/height and carry no viewBox, so Skia has no mapping from the
        // art's coordinate space onto a smaller container: setContainerSize alone renders them at 512px and a
        // 22px surface captures only their transparent top-left corner, i.e. a blank tray icon. Installing a
        // viewBox after parsing does not help either — measured, still fully transparent. Rendering at the
        // intrinsic size and scaling the canvas is what fits the art to the icon, and it stays correct for an
        // SVG that does carry a viewBox, because the container then matches the size it maps onto.
        val root = dom.root
        val intrinsicWidth = root?.width?.value ?: 0f
        val intrinsicHeight = root?.height?.value ?: 0f
        if (intrinsicWidth > 0f && intrinsicHeight > 0f) {
            dom.setContainerSize(intrinsicWidth, intrinsicHeight)
            surface.canvas.scale(size / intrinsicWidth, size / intrinsicHeight)
        } else {
            // Percentage or missing dimensions: nothing to scale from, so let the container drive it.
            dom.setContainerSize(size.toFloat(), size.toFloat())
        }
        dom.render(surface.canvas)
        bitmap.allocPixels(ImageInfo.makeN32Premul(size, size))
        surface.readPixels(bitmap, 0, 0)
        val pixels = IntArray(size * size) { index -> bitmap.getColor(index % size, index / size) }
        return TrayIconRaster(size, size, argbPixelsToSniBytes(pixels))
    } finally {
        bitmap.close()
        surface.close()
        dom.close()
    }
}

/**
 * Rasterizes a classpath SVG at every size in [TRAY_ICON_SIZES], or returns an empty list if the resource is missing or
 * Skia cannot decode it.
 *
 * An icon failure must not take the tray — and with it the only way to quit — down with it, so this swallows the
 * failure and lets the caller publish an item with no pixmap rather than none at all.
 */
internal fun loadTrayIconRasters(resourcePath: String): List<TrayIconRaster> = runCatching {
    val classLoader =
        requireNotNull(Thread.currentThread().contextClassLoader) {
            "Missing context class loader while loading tray icon: $resourcePath"
        }
    val svg =
        requireNotNull(classLoader.getResourceAsStream(resourcePath)) {
            "Missing classpath resource: $resourcePath"
        }
            .use { it.readAllBytes() }
    TRAY_ICON_SIZES.map { size -> rasterizeSvgForTray(svg, size) }
}
    .getOrDefault(emptyList())
