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
package org.meshtastic.screenshot.marketing

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** Bounded settle: frames rendered until the scene reports no pending invalidation, or this many at most. */
private const val MAX_FRAMES = 30
private const val FRAME_NANOS = 16_000_000L

/**
 * Step one of the pipeline, form-factor agnostic: composes [content] offscreen at the given pixel size and density and
 * returns the settled frame. A phone screen, a desktop window and a store frame all come through here.
 */
internal fun renderScreen(width: Int, height: Int, density: Float, content: @Composable () -> Unit): ImageBitmap =
    ImageComposeScene(width = width, height = height, density = Density(density), content = content).use { scene ->
        var nanos = 0L
        var frames = 1
        var image = scene.render(nanos)
        while (scene.hasInvalidations() && frames < MAX_FRAMES) {
            nanos += FRAME_NANOS
            image = scene.render(nanos)
            frames++
        }
        image.toComposeImageBitmap()
    }

internal fun ImageBitmap.writePng(file: File) {
    val data = checkNotNull(Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG))
    file.writeBytes(data.bytes)
}
