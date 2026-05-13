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
import org.meshtastic.core.resources.ic_abc
import org.meshtastic.core.resources.ic_admin_panel_settings
import org.meshtastic.core.resources.ic_app_settings_alt
import org.meshtastic.core.resources.ic_bug_report
import org.meshtastic.core.resources.ic_cleaning_services
import org.meshtastic.core.resources.ic_data_usage
import org.meshtastic.core.resources.ic_format_paint
import org.meshtastic.core.resources.ic_language
import org.meshtastic.core.resources.ic_list
import org.meshtastic.core.resources.ic_notifications
import org.meshtastic.core.resources.ic_perm_scan_wifi
import org.meshtastic.core.resources.ic_sensors
import org.meshtastic.core.resources.ic_settings
import org.meshtastic.core.resources.ic_settings_remote
import org.meshtastic.core.resources.ic_storage
import org.meshtastic.core.resources.ic_waving_hand

// Config route icons
val MeshtasticIcons.AdminPanelSettings: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_admin_panel_settings)
val MeshtasticIcons.AppSettingsAlt: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_app_settings_alt)
val MeshtasticIcons.BugReport: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_bug_report)
val MeshtasticIcons.CleaningServices: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_cleaning_services)
val MeshtasticIcons.FormatPaint: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_format_paint)
val MeshtasticIcons.Language: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_language)
val MeshtasticIcons.WavingHand: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_waving_hand)
val MeshtasticIcons.Abc: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_abc)
val MeshtasticIcons.Settings: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings)
val MeshtasticIcons.ConfigChannels: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_list)
val MeshtasticIcons.Notifications: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_notifications)
val MeshtasticIcons.DataUsage: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_data_usage)
val MeshtasticIcons.PermScanWifi: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_perm_scan_wifi)
val MeshtasticIcons.DetectionSensor: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_sensors)
val MeshtasticIcons.SettingsRemote: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_settings_remote)
val MeshtasticIcons.Storage: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_storage)
