/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector

@Composable
actual fun CastButton(modifier: Modifier) {
    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { context ->
            MediaRouteButton(context).apply {
                val selector = MediaRouteSelector.Builder()
                    .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                    .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                    .build()
                routeSelector = selector
            }
        }
    )
}
