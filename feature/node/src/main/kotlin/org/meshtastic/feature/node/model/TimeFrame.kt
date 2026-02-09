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
import org.meshtastic.core.strings.all_time
import org.meshtastic.core.strings.one_hour_short
import org.meshtastic.core.strings.one_month
import org.meshtastic.core.strings.one_week
import org.meshtastic.core.strings.twenty_four_hours
import org.meshtastic.core.strings.two_weeks

enum class TimeFrame(val strRes: StringResource, val seconds: Long) {
    ONE_HOUR(Res.string.one_hour_short, 3600),
    TWENTY_FOUR_HOURS(Res.string.twenty_four_hours, 86400),
    SEVEN_DAYS(Res.string.one_week, 604800),
    TWO_WEEKS(Res.string.two_weeks, 1209600),
    ONE_MONTH(Res.string.one_month, 2592000),
    ALL_TIME(Res.string.all_time, 0);

    fun timeThreshold(now: Long = System.currentTimeMillis() / 1000L): Long {
        if (this == ALL_TIME) return 0
        return now - seconds
    }

    /**
     * Checks if this time frame is relevant given the oldest available data point.
     * We show the option if the data extends at least into this timeframe.
     */
    fun isAvailable(oldestTimestampSeconds: Long, now: Long = System.currentTimeMillis() / 1000L): Boolean {
        if (this == ALL_TIME || this == ONE_HOUR) return true
        val rangeSeconds = now - oldestTimestampSeconds
        return rangeSeconds >= seconds
    }
}
