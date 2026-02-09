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
package org.meshtastic.feature.node.model

import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.one_day
import org.meshtastic.core.strings.one_hour
import org.meshtastic.core.strings.one_week

enum class TimeFrame(val strRes: StringResource, val seconds: Long) {
    ONE_HOUR(Res.string.one_hour, 3600),
    TWENTY_FOUR_HOURS(Res.string.one_day, 86400),
    SEVEN_DAYS(Res.string.one_week, 604800);

    fun timeThreshold(now: Long = System.currentTimeMillis() / 1000L): Long {
        return now - seconds
    }
}
