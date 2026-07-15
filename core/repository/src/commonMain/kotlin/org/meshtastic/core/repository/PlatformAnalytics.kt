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
package org.meshtastic.core.repository

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
     * Tracks a successful device connection as a custom RUM action, aligned with the Meshtastic-Apple DataDog
     * integration for cross-platform analytics comparison.
     *
     * @param firmwareVersion The firmware version of the connected device (major.minor).
     * @param transportType The transport used for the connection (e.g., "BLE", "TCP", "USB").
     * @param hardwareModel The hardware model name of the connected device.
     * @param nodes The total number of nodes in the mesh network.
     * @param connectionRestored True if this connection was restored from device sleep rather than a fresh connect.
     */
    fun trackConnect(
        firmwareVersion: String?,
        transportType: String?,
        hardwareModel: String?,
        nodes: Int,
        connectionRestored: Boolean,
    ) {
        // Default no-op for platforms that don't support RUM (fdroid, desktop)
    }

    /**
     * Starts tracking a screen as a RUM view, aligned with the Meshtastic-Apple Datadog integration (which auto-tracks
     * SwiftUI views) so per-screen RUM data lines up across platforms.
     *
     * Starting a view implicitly stops any previously active view. Callers should pair each call with a matching
     * [stopScreenView] using the same [key] when the screen is left.
     *
     * @param key A stable identifier that pairs this start with its matching [stopScreenView].
     * @param name The route-derived view name (e.g. `org.meshtastic.core.navigation.NodesRoute.Nodes`).
     */
    fun startScreenView(key: String, name: String) {
        // Default no-op for platforms that don't support RUM (fdroid, desktop)
    }

    /**
     * Stops the RUM view previously started with [startScreenView] for the given [key].
     *
     * @param key The same identifier passed to the matching [startScreenView].
     */
    fun stopScreenView(key: String) {
        // Default no-op for platforms that don't support RUM (fdroid, desktop)
    }

    /**
     * Indicates whether platform-specific services (like Google Play Services or Datadog) are available and
     * initialized.
     */
    val isPlatformServicesAvailable: Boolean
}
