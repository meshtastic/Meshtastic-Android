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
package org.meshtastic.feature.settings

import org.meshtastic.core.navigation.DiscoveryRoute
import org.meshtastic.core.navigation.FirmwareRoute
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.WifiProvisionRoute

/**
 * What the app shell running Settings can offer. Settings links only to destinations the shell registers, so a feature
 * module the shell leaves out never becomes a dead row.
 *
 * @property localMeshDiscovery the shell registers `feature:discovery`.
 * @property wifiProvisioning the shell registers `feature:wifi-provision`.
 * @property helpDocs the shell registers `feature:docs`.
 * @property firmware the shell registers `feature:firmware`, which also hosts ChirpyHop.
 * @property tak the TAK module screen and the local TAK server exist; a page cannot run a server.
 * @property databasePerDevice each radio keeps its own app database; false where one database holds every radio.
 * @property reloadApp restarts the app in place, or null where the user restarts it themselves.
 */
class SettingsPlatform(
    val localMeshDiscovery: Boolean,
    val wifiProvisioning: Boolean,
    val helpDocs: Boolean,
    val firmware: Boolean,
    val tak: Boolean,
    val databasePerDevice: Boolean,
    val reloadApp: (() -> Unit)?,
) {
    /** False for a [route] whose destination this shell does not register. */
    fun supports(route: Route): Boolean = when (route) {
        is DiscoveryRoute -> localMeshDiscovery

        is WifiProvisionRoute -> wifiProvisioning

        is FirmwareRoute -> firmware

        SettingsRoute.HelpDocs -> helpDocs

        SettingsRoute.TAK,
        SettingsRoute.TakServer,
        -> tak

        else -> true
    }
}

expect val settingsPlatform: SettingsPlatform
