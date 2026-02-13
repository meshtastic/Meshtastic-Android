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
import androidx.compose.material.icons.automirrored.filled.SpeakerNotes
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.twotone.VolumeMute
import androidx.compose.material.icons.automirrored.twotone.VolumeUp
import androidx.compose.material.icons.rounded.ArrowCircleUp
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.NoCell
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.SpeakerNotesOff
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.material.icons.twotone.HowToReg
import androidx.compose.ui.graphics.vector.ImageVector

val MeshtasticIcons.Favorite: ImageVector
    get() = Icons.Rounded.Star
val MeshtasticIcons.NotFavorite: ImageVector
    get() = Icons.Rounded.StarBorder
val MeshtasticIcons.Muted: ImageVector
    get() = Icons.Rounded.SpeakerNotesOff
val MeshtasticIcons.Unmuted: ImageVector
    get() = Icons.AutoMirrored.Filled.SpeakerNotes
val MeshtasticIcons.VolumeOff: ImageVector
    get() = Icons.AutoMirrored.Filled.VolumeOff
val MeshtasticIcons.VolumeUp: ImageVector
    get() = Icons.AutoMirrored.Filled.VolumeUp
val MeshtasticIcons.History: ImageVector
    get() = Icons.Rounded.History
val MeshtasticIcons.Cloud: ImageVector
    get() = Icons.Rounded.Cloud
val MeshtasticIcons.CloudOff: ImageVector
    get() = Icons.Rounded.CloudOff
val MeshtasticIcons.Unmessageable: ImageVector
    get() = Icons.Rounded.NoCell

val MeshtasticIcons.CloudDone: ImageVector
    get() = Icons.TwoTone.CloudDone
val MeshtasticIcons.CloudSync: ImageVector
    get() = Icons.TwoTone.CloudSync
val MeshtasticIcons.CloudOffTwoTone: ImageVector
    get() = Icons.TwoTone.CloudOff
val MeshtasticIcons.CloudTwoTone: ImageVector
    get() = Icons.TwoTone.Cloud

val MeshtasticIcons.ArrowCircleUp: ImageVector
    get() = Icons.Rounded.ArrowCircleUp
val MeshtasticIcons.Dangerous: ImageVector
    get() = Icons.Rounded.Dangerous

val MeshtasticIcons.VolumeUpTwoTone: ImageVector
    get() = Icons.AutoMirrored.TwoTone.VolumeUp
val MeshtasticIcons.VolumeMuteTwoTone: ImageVector
    get() = Icons.AutoMirrored.TwoTone.VolumeMute

val MeshtasticIcons.CheckCircle: ImageVector
    get() = Icons.Rounded.CheckCircleOutline

val MeshtasticIcons.Acknowledged: ImageVector
    get() = Icons.TwoTone.HowToReg

val MeshtasticIcons.Udp: ImageVector
    get() = Icons.Rounded.Lan
val MeshtasticIcons.Api: ImageVector
    get() = Icons.Rounded.Terminal
val MeshtasticIcons.Ethernet: ImageVector
    get() = Icons.Rounded.SettingsEthernet
