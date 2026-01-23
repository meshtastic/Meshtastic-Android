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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SpeakerNotesOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.rounded.NoCell
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.CloudDone
import androidx.compose.material.icons.twotone.CloudOff
import androidx.compose.material.icons.twotone.CloudSync
import androidx.compose.ui.graphics.vector.ImageVector

val MeshtasticIcons.Favorite: ImageVector
    get() = Icons.Default.Star
val MeshtasticIcons.NotFavorite: ImageVector
    get() = Icons.Default.StarBorder
val MeshtasticIcons.Muted: ImageVector
    get() = Icons.Default.SpeakerNotesOff
val MeshtasticIcons.Unmuted: ImageVector
    get() = Icons.AutoMirrored.Filled.SpeakerNotes
val MeshtasticIcons.VolumeOff: ImageVector
    get() = Icons.AutoMirrored.Filled.VolumeOff
val MeshtasticIcons.VolumeUp: ImageVector
    get() = Icons.AutoMirrored.Filled.VolumeUp
val MeshtasticIcons.History: ImageVector
    get() = Icons.Default.History
val MeshtasticIcons.Cloud: ImageVector
    get() = Icons.Default.Cloud
val MeshtasticIcons.CloudOff: ImageVector
    get() = Icons.Default.CloudOff
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

val MeshtasticIcons.CheckCircle: ImageVector
    get() = Icons.Default.CheckCircle
val MeshtasticIcons.Dangerous: ImageVector
    get() = Icons.Default.Dangerous

val MeshtasticIcons.VolumeUpTwoTone: ImageVector
    get() = Icons.AutoMirrored.TwoTone.VolumeUp
val MeshtasticIcons.VolumeMuteTwoTone: ImageVector
    get() = Icons.AutoMirrored.TwoTone.VolumeMute
