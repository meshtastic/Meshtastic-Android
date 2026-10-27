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
package org.meshtastic.feature.map.maplibre.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Draws NOAA's weather radar smooth where its 1 km cells would otherwise show as squares.
 *
 * Installs a resource provider on MapLibre's process-wide runtime, so it must run before the first map composes; both
 * map entry points call it ahead of `rememberMapState`. Safe to call on every composition.
 */
@Composable
internal fun InstallRadarSmoothing() {
    val client: HttpClient = koinInject()
    val dispatchers: CoroutineDispatchers = koinInject()
    remember(client) { installRadarSmoothing(client, dispatchers.default) }
}

/**
 * Puts the radar provider on MapLibre's default runtime, once per process. Does nothing where the runtime lacks one.
 */
internal expect fun installRadarSmoothing(client: HttpClient, renderContext: CoroutineContext)
