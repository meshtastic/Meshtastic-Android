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
package org.meshtastic.feature.docs.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import meshtasticandroid.feature.docs.generated.resources.Res
import org.jetbrains.compose.resources.MissingResourceException

/**
 * Resolves local markdown image references (e.g. `assets/screenshots/foo.png`) to bundled Compose resources via
 * [Res.getUri] and loads them asynchronously using Coil 3's [rememberAsyncImagePainter].
 *
 * External URLs (`http://` / `https://`) return `null` so the default renderer behaviour applies (or they are simply
 * skipped). Missing resources are silently skipped (returns `null`) to avoid crashing composition when screenshots have
 * not yet been generated or synced.
 *
 * FR-038: Screenshots synced by `syncDocsToComposeResources` land under
 * `composeResources/files/docs/assets/screenshots/`, matching the relative paths used in the authored markdown.
 */
class ComposeResourceImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        if (link.startsWith("http://") || link.startsWith("https://")) return null

        // Markdown uses root-relative paths (/assets/screenshots/foo.png) for Jekyll compatibility.
        // Strip the leading slash to build the compose resource path.
        val relativePath = link.removePrefix("/")
        val resourcePath = "files/docs/$relativePath"
        val uri =
            try {
                Res.getUri(resourcePath)
            } catch (_: MissingResourceException) {
                null
            }

        return uri?.let { resolvedUri ->
            val painter =
                rememberAsyncImagePainter(
                    model =
                    ImageRequest.Builder(LocalPlatformContext.current)
                        .data(resolvedUri)
                        .size(coil3.size.Size.ORIGINAL)
                        .build(),
                )
            ImageData(painter)
        }
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size {
        var size by remember(painter) { mutableStateOf(painter.intrinsicSize) }
        if (painter is AsyncImagePainter) {
            val painterState = painter.state.collectAsState()
            painterState.value.painter?.intrinsicSize?.also { size = it }
        }
        return size
    }
}
