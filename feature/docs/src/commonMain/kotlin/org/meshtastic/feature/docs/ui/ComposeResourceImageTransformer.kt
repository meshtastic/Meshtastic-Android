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

private const val ASSETS_SEGMENT = "assets/"

/**
 * Maps a markdown image link to its bundled compose resource path, or `null` for external URLs.
 *
 * Authored pages use paths relative to the Jekyll source layout (`../../assets/screenshots/foo.png` from
 * `docs/en/user/page.md`), while the compose resource tree drops the `en/` level (`files/docs/user/page.md` with
 * screenshots at `files/docs/assets/screenshots/`). Relative prefixes therefore cannot be resolved literally; instead,
 * anything from the `assets/` segment onward is anchored at `files/docs/`, which matches where
 * `syncDocsToComposeResources` places the bundled screenshots.
 */
internal fun resolveDocImageResourcePath(link: String): String? {
    if (link.startsWith("http://") || link.startsWith("https://")) return null
    val assetsIndex = link.indexOf(ASSETS_SEGMENT)
    val isSegmentStart = assetsIndex == 0 || (assetsIndex > 0 && link[assetsIndex - 1] == '/')
    return if (assetsIndex >= 0 && isSegmentStart) {
        "files/docs/${link.substring(assetsIndex)}"
    } else {
        "files/docs/${link.removePrefix("/")}"
    }
}

/**
 * Resolves local markdown image references (e.g. `../../assets/screenshots/foo.png`) to bundled Compose resources via
 * [Res.getUri] and loads them asynchronously using Coil 3's [rememberAsyncImagePainter].
 *
 * External URLs (`http://` / `https://`) return `null` so the default renderer behaviour applies (or they are simply
 * skipped). Missing resources are silently skipped (returns `null`) to avoid crashing composition when screenshots have
 * not yet been generated or synced.
 *
 * FR-038: Screenshots synced by `syncDocsToComposeResources` land under
 * `composeResources/files/docs/assets/screenshots/`; [resolveDocImageResourcePath] maps the authored markdown paths
 * onto that location.
 */
class ComposeResourceImageTransformer : ImageTransformer {

    @Composable
    override fun transform(link: String): ImageData? {
        val resourcePath = resolveDocImageResourcePath(link) ?: return null
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
