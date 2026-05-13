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
import org.meshtastic.core.resources.ic_battery_alert
import org.meshtastic.core.resources.ic_battery_horiz_000
import org.meshtastic.core.resources.ic_battery_question_mark

val MeshtasticIcons.BatteryEmpty: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_battery_horiz_000)

val MeshtasticIcons.BatteryUnknown: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_battery_question_mark)

val MeshtasticIcons.BatteryAlert: ImageVector
    @Composable get() = vectorResource(Res.drawable.ic_battery_alert)
