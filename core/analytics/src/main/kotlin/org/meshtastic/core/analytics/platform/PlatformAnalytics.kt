/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.analytics.platform

import androidx.navigation.NavHostController
import org.meshtastic.core.analytics.DataPair

/**
 * Interface to abstract platform-specific functionalities, primarily for analytics and related services that differ
 * between product flavors.
 */
interface PlatformAnalytics {

    fun track(event: String, vararg properties: DataPair)

    /**
     * Sets device-specific attributes (e.g., firmware version, hardware model) for analytics.
     *
     * @param firmwareVersion The firmware version of the connected device.
     * @param model The hardware model of the connected device.
     */
    fun setDeviceAttributes(firmwareVersion: String, model: String)

    /**
     * A Composable function to set up navigation tracking for the current platform.
     *
     * @param navController The [NavHostController] to track.
     */
    fun addNavigationTrackingEffect(navController: NavHostController): () -> Unit

    /**
     * Indicates whether platform-specific services (like Google Play Services or Datadog) are available and
     * initialized.
     */
    val isPlatformServicesAvailable: Boolean
}
