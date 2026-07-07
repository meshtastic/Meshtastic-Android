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
package org.meshtastic.app

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-slot handoff for a map file (GeoJSON/KML) received via an "Open in / Send to Meshtastic" intent (e.g. the
 * Meshtastic Site Planner's "Send to App" share). The shared [MainActivity] receives the intent; only the Google-flavor
 * map renders overlays, so its `MapViewModel` drains this and imports the layer while the activity still holds the URI
 * read grant.
 *
 * ponytail: process-global single slot — fine for one shared file at a time; promote to a Koin single if this ever
 * needs a second consumer or unit testing.
 */
object MapFileImportBus {
    val pending = MutableStateFlow<Uri?>(null)
}
