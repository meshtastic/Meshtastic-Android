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
package org.meshtastic.core.ui.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.vectorResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.ic_arrow_circle_up
import org.meshtastic.core.resources.ic_bedtime
import org.meshtastic.core.resources.ic_check_circle_fill0
import org.meshtastic.core.resources.ic_check_circle_fill1
import org.meshtastic.core.resources.ic_cloud
import org.meshtastic.core.resources.ic_cloud_done
import org.meshtastic.core.resources.ic_cloud_download
import org.meshtastic.core.resources.ic_cloud_sync
import org.meshtastic.core.resources.ic_cloud_upload
import org.meshtastic.core.resources.ic_dangerous
import org.meshtastic.core.resources.ic_error_fill0
import org.meshtastic.core.resources.ic_error_fill1
import org.meshtastic.core.resources.ic_history
import org.meshtastic.core.resources.ic_how_to_reg
import org.meshtastic.core.resources.ic_info
import org.meshtastic.core.resources.ic_lan
import org.meshtastic.core.resources.ic_link_off
import org.meshtastic.core.resources.ic_no_cell
import org.meshtastic.core.resources.ic_radio_button_unchecked
import org.meshtastic.core.resources.ic_schedule
import org.meshtastic.core.resources.ic_settings_ethernet
import org.meshtastic.core.resources.ic_speaker_notes
import org.meshtastic.core.resources.ic_speaker_notes_off
import org.meshtastic.core.resources.ic_star
import org.meshtastic.core.resources.ic_star_border
import org.meshtastic.core.resources.ic_terminal
import org.meshtastic.core.resources.ic_volume_mute
import org.meshtastic.core.resources.ic_volume_off
import org.meshtastic.core.resources.ic_warning

// Favorites
val MeshtasticIcons.Favorite: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_star)
val MeshtasticIcons.NotFavorite: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_star_border)

// Mute state
val MeshtasticIcons.Muted: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_speaker_notes_off)
val MeshtasticIcons.Unmuted: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_speaker_notes)

// Volume
val MeshtasticIcons.VolumeOff: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_volume_off)
val MeshtasticIcons.VolumeMute: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_volume_mute)

// Time
val MeshtasticIcons.History: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_history)

// MQTT status
val MeshtasticIcons.MqttDelivered: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_done)
val MeshtasticIcons.MqttSyncing: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_sync)

// Connectivity
val MeshtasticIcons.Unmessageable: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_no_cell)
val MeshtasticIcons.Udp: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_lan)
val MeshtasticIcons.Api: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_terminal)
val MeshtasticIcons.Ethernet: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings_ethernet)

// Update & lifecycle
val MeshtasticIcons.ArrowCircleUp: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_arrow_circle_up)
val MeshtasticIcons.Dangerous: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_dangerous)

// Result states
val MeshtasticIcons.CheckCircle: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_check_circle_fill0)
val MeshtasticIcons.Success: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_check_circle_fill1)
val MeshtasticIcons.Error: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_error_fill1)
val MeshtasticIcons.ErrorOutline: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_error_fill0)
val MeshtasticIcons.Info: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_info)

// Acknowledgment
val MeshtasticIcons.Acknowledged: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_how_to_reg)

// Selection state
val MeshtasticIcons.RadioButtonUnchecked: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_radio_button_unchecked)

// Device sleep
val MeshtasticIcons.DeviceSleep: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bedtime)

// Node connection state (non-MQTT)
val MeshtasticIcons.Disconnected: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_link_off)

// Message delivery status
val MeshtasticIcons.MessageEnroute: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_schedule)
val MeshtasticIcons.MessageError: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_error_fill0)
val MeshtasticIcons.Warning: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_warning)
val MeshtasticIcons.MqttConnected: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud)
val MeshtasticIcons.CloudUpload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_upload)
val MeshtasticIcons.CloudDownload: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cloud_download)
