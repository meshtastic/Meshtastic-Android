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
package org.meshtastic.app.map

import android.content.Context
import co.touchlab.kermit.Logger
import com.google.android.gms.maps.MapsInitializer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized, run-once Google Maps SDK initialization for the google flavor.
 *
 * Two things happen here, deliberately decoupled:
 * 1. **Synchronous init** via the single-arg [MapsInitializer.initialize] overload. This is the only overload
 *    documented as synchronous, and it guarantees `BitmapDescriptorFactory` is ready before any eager Canvas descriptor
 *    is built off a live `GoogleMap` (the node-detail inline map builds its icon before its map loads — see #5709 and
 *    `MarkerBitmapRenderer`). It is idempotent, so repeated calls are no-ops.
 * 2. **Renderer reporting** via the callback overload, registered exactly once. We can no longer *force* a renderer
 *    (the LEGACY renderer was decommissioned in March 2025, so a preference is honored only as a hint), but the
 *    documented "Latest renderer" tile-rendering failures can still leave the base map blank on some devices. Logging
 *    which renderer actually loaded lets us correlate "black map" field reports in Crashlytics/Datadog. Kermit's
 *    [Logger] is the sink because `GooglePlatformAnalytics` wires its Crashlytics/Datadog log writers at startup while
 *    delaying SDK init until consent — so logging through Kermit is the privacy-correct, already-sanctioned path (vs.
 *    touching `Firebase.crashlytics` directly).
 */
object MapsSdkInitializer {

    private val callbackRegistered = AtomicBoolean(false)

    fun ensureInitialized(context: Context) {
        val app = context.applicationContext

        // (1) Synchronous readiness guarantee — see kdoc. Deprecated overload retained intentionally.
        @Suppress("DEPRECATION")
        MapsInitializer.initialize(app)

        // (2) Register the renderer-reporting callback once. The SDK is already initialized above, so the
        // callback fires promptly with the renderer that actually loaded.
        if (callbackRegistered.compareAndSet(false, true)) {
            MapsInitializer.initialize(app, MapsInitializer.Renderer.LATEST) { renderer ->
                Logger.withTag(TAG).i { "Google Maps renderer loaded: $renderer" }
            }
        }
    }

    private const val TAG = "MapsSdkInitializer"
}
