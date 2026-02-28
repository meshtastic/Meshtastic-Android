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
package org.meshtastic.core.ui.timezone

import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test
import org.meshtastic.core.model.util.toPosixString

class ZoneIdExtensionsTest {

    @Test
    fun `test POSIX string generation`() {
        val zoneMap =
            mapOf(
                "US/Hawaii" to "HST10",
                "US/Alaska" to "AKST9AKDT,M3.2.0,M11.1.0",
                "US/Pacific" to "PST8PDT,M3.2.0,M11.1.0",
                "US/Arizona" to "MST7",
                "US/Mountain" to "MST7MDT,M3.2.0,M11.1.0",
                "US/Central" to "CST6CDT,M3.2.0,M11.1.0",
                "US/Eastern" to "EST5EDT,M3.2.0,M11.1.0",
                "America/Sao_Paulo" to "BRT3",
                "UTC" to "UTC0",
                "Europe/London" to "GMT0BST,M3.5.0/1,M10.5.0",
                "Europe/Lisbon" to "WET0WEST,M3.5.0/1,M10.5.0",
                "Europe/Budapest" to "CET-1CEST,M3.5.0,M10.5.0/3",
                "Europe/Kiev" to "EET-2EEST,M3.5.0/3,M10.5.0/4",
                "Africa/Cairo" to "EET-2EEST,M4.5.5/0,M10.5.5/0",
                "Asia/Kolkata" to "IST-5:30",
                "Asia/Hong_Kong" to "HKT-8",
                "Asia/Tokyo" to "JST-9",
                "Australia/Perth" to "AWST-8",
                "Australia/Adelaide" to "ACST-9:30ACDT,M10.1.0,M4.1.0/3",
                "Australia/Sydney" to "AEST-10AEDT,M10.1.0,M4.1.0/3",
                "Pacific/Auckland" to "NZST-12NZDT,M9.5.0,M4.1.0/3",
            )

        zoneMap.forEach { (tz, expected) -> assertEquals(expected, TimeZone.of(tz).toPosixString()) }
    }
}
