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
import androidx.compose.ui.graphics.vector.ImageVector
import org.meshtastic.core.ui.icon.Altitude
import org.meshtastic.core.ui.icon.Antenna
import org.meshtastic.core.ui.icon.Api
import org.meshtastic.core.ui.icon.BluetoothConnected
import org.meshtastic.core.ui.icon.BugReport
import org.meshtastic.core.ui.icon.Chart
import org.meshtastic.core.ui.icon.ConfigChannels
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.ForkLeft
import org.meshtastic.core.ui.icon.Group
import org.meshtastic.core.ui.icon.Language
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Message
import org.meshtastic.core.ui.icon.Nodes
import org.meshtastic.core.ui.icon.Notes
import org.meshtastic.core.ui.icon.PersonSearch
import org.meshtastic.core.ui.icon.PinDrop
import org.meshtastic.core.ui.icon.Route
import org.meshtastic.core.ui.icon.Rssi
import org.meshtastic.core.ui.icon.Settings
import org.meshtastic.core.ui.icon.SignalCellular3Bar
import org.meshtastic.core.ui.icon.WavingHand
import org.meshtastic.feature.docs.model.DocPage

/** Resolves a [DocPage.iconId] to a [ImageVector] from [MeshtasticIcons]. */
@Composable
@Suppress("CyclomaticComplexMethod")
internal fun DocPage.resolveIcon(): ImageVector = when (iconId) {
    // User Guide
    "onboarding" -> MeshtasticIcons.WavingHand

    "connections" -> MeshtasticIcons.BluetoothConnected

    "messages" -> MeshtasticIcons.Message

    "nodes" -> MeshtasticIcons.Nodes

    "node-metrics" -> MeshtasticIcons.Chart

    "map" -> MeshtasticIcons.PinDrop

    "settings-radio" -> MeshtasticIcons.Settings

    "settings-module" -> MeshtasticIcons.ConfigChannels

    "telemetry" -> MeshtasticIcons.Altitude

    "tak" -> MeshtasticIcons.Antenna

    "mqtt" -> MeshtasticIcons.Rssi

    "discovery" -> MeshtasticIcons.PersonSearch

    "firmware" -> MeshtasticIcons.Device

    "desktop" -> MeshtasticIcons.Device

    "signal-meter" -> MeshtasticIcons.SignalCellular3Bar

    "units-locale" -> MeshtasticIcons.Language

    "translate" -> MeshtasticIcons.Language

    "android-auto" -> MeshtasticIcons.Route

    "app-functions" -> MeshtasticIcons.Api

    "widget" -> MeshtasticIcons.Chart

    "help" -> MeshtasticIcons.Notes

    "debug-logs" -> MeshtasticIcons.BugReport

    // Developer Guide
    "architecture" -> MeshtasticIcons.ForkLeft

    "codebase" -> MeshtasticIcons.ForkLeft

    "adding-features" -> MeshtasticIcons.ForkLeft

    "navigation" -> MeshtasticIcons.ForkLeft

    "transport" -> MeshtasticIcons.Antenna

    "persistence" -> MeshtasticIcons.Chart

    "testing" -> MeshtasticIcons.BugReport

    "contributing" -> MeshtasticIcons.Group

    "measurement" -> MeshtasticIcons.Chart

    else -> MeshtasticIcons.Notes
}
