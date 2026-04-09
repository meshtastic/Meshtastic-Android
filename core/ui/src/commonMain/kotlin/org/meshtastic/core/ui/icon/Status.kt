/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.SpeakerNotes
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.NoCell
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

// Favorites
val MeshtasticIcons.Favorite: ImageVector
    get() = Icons.Rounded.Star
val MeshtasticIcons.NotFavorite: ImageVector
    get() = Icons.Rounded.StarBorder

// Mute state
val MeshtasticIcons.Muted: ImageVector
    get() = Icons.Rounded.SpeakerNotesOff
val MeshtasticIcons.Unmuted: ImageVector
    get() = Icons.AutoMirrored.Rounded.SpeakerNotes

// Volume
val MeshtasticIcons.VolumeOff: ImageVector
    get() = Icons.AutoMirrored.Rounded.VolumeOff
val MeshtasticIcons.VolumeUp: ImageVector
    get() = Icons.AutoMirrored.Rounded.VolumeUp
val MeshtasticIcons.VolumeMute: ImageVector
    get() = Icons.AutoMirrored.Rounded.VolumeMute

// Time
val MeshtasticIcons.History: ImageVector
    get() = Icons.Rounded.History

// MQTT status
val MeshtasticIcons.MqttConnected: ImageVector
    get() = Icons.Rounded.Cloud
val MeshtasticIcons.MqttDelivered: ImageVector
    get() = Icons.Rounded.CloudDone
val MeshtasticIcons.MqttSyncing: ImageVector
    get() = Icons.Rounded.CloudSync

// Connectivity
val MeshtasticIcons.Unmessageable: ImageVector
    get() = Icons.Rounded.NoCell
val MeshtasticIcons.Udp: ImageVector
    get() = Icons.Rounded.Lan
val MeshtasticIcons.Api: ImageVector
    get() = Icons.Rounded.Terminal
val MeshtasticIcons.Ethernet: ImageVector
    get() = Icons.Rounded.SettingsEthernet

// Update & lifecycle
val MeshtasticIcons.ArrowCircleUp: ImageVector
    get() = Icons.Rounded.ArrowCircleUp
val MeshtasticIcons.Dangerous: ImageVector
    get() = Icons.Rounded.Dangerous

// Result states
val MeshtasticIcons.CheckCircle: ImageVector
    get() = Icons.Rounded.CheckCircleOutline
val MeshtasticIcons.Success: ImageVector
    get() = Icons.Rounded.CheckCircle
val MeshtasticIcons.Error: ImageVector
    get() = Icons.Rounded.Error
val MeshtasticIcons.ErrorOutline: ImageVector
    get() = Icons.Rounded.ErrorOutline
val MeshtasticIcons.Info: ImageVector
    get() = Icons.Rounded.Info

// Acknowledgment
val MeshtasticIcons.Acknowledged: ImageVector
    get() = Icons.Rounded.HowToReg

// Selection state
val MeshtasticIcons.RadioButtonUnchecked: ImageVector
    get() = Icons.Rounded.RadioButtonUnchecked

// Device sleep
val MeshtasticIcons.DeviceSleep: ImageVector
    get() = Icons.Rounded.Bedtime

// Node connection state (non-MQTT)
val MeshtasticIcons.Disconnected: ImageVector
    get() = Icons.Rounded.LinkOff

// Message delivery status
val MeshtasticIcons.MessageEnroute: ImageVector
    get() = Icons.Rounded.Schedule
val MeshtasticIcons.MessageError: ImageVector
    get() = Icons.Rounded.ErrorOutline
