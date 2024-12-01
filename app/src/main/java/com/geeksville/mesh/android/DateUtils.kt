/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.android

import java.util.*

/**
 * Created by kevinh on 1/13/16.
 */
object DateUtils {
    fun dateUTC(year: Int, month: Int, day: Int): Date {
        val cal = GregorianCalendar(TimeZone.getTimeZone("GMT"))
        cal.set(year, month, day, 0, 0, 0);
        return Date(cal.getTime().getTime())
    }
}