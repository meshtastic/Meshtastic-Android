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
package org.meshtastic.core.ui.component

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("MagicNumber")
class BuildNodeDescriptionTest {

    private val testStrings =
        NodeDescriptionStrings(
            online = "online",
            offline = "offline",
            favorite = "favorite",
            lastHeard = "last heard %s",
            role = "role %s",
            hopsAway = "0 hops away",
            battery = "battery 0%",
            distanceAway = "%s away",
            signal = "signal %s",
        )

    private fun describe(
        name: String = "TestNode",
        isOnline: Boolean = true,
        isFavorite: Boolean = false,
        lastHeard: Int = 0,
        role: String = "CLIENT",
        hopsAway: Int = 0,
        batteryLevel: Int? = null,
        distance: String? = null,
        snr: Float = Float.MAX_VALUE,
        rssi: Int = 0,
        viaMqtt: Boolean = false,
        lastHeardIsRelative: Boolean = true,
    ): String = buildNodeDescription(
        name = name,
        isOnline = isOnline,
        isFavorite = isFavorite,
        lastHeard = lastHeard,
        role = role,
        hopsAway = hopsAway,
        batteryLevel = batteryLevel,
        distance = distance,
        snr = snr,
        rssi = rssi,
        viaMqtt = viaMqtt,
        strings = testStrings,
        lastHeardIsRelative = lastHeardIsRelative,
    )

    // ---- Basic output ----

    @Test
    fun includes_name_and_online_status() {
        val result = describe(name = "Alpha", isOnline = true)
        assertTrue(result.startsWith("Alpha, online"))
    }

    @Test
    fun includes_offline_when_not_online() {
        val result = describe(isOnline = false)
        assertContains(result, "offline")
    }

    @Test
    fun includes_favorite_when_flagged() {
        val result = describe(isFavorite = true)
        assertContains(result, "favorite")
    }

    @Test
    fun omits_favorite_when_not_flagged() {
        val result = describe(isFavorite = false)
        assertFalse(result.contains("favorite"))
    }

    // ---- Last heard ----

    @Test
    fun omits_last_heard_when_zero() {
        val result = describe(lastHeard = 0)
        assertFalse(result.contains("last heard"))
    }

    // Note: lastHeard > 0 tests require DateFormatter/Compose Resources initialization
    // which is not available in headless JVM unit tests. The relative vs absolute time
    // formatting is tested indirectly through integration/screenshot tests.

    // ---- Hops ----

    @Test
    fun includes_hops_when_positive() {
        val result = describe(hopsAway = 3)
        assertContains(result, "3 hops away")
    }

    @Test
    fun omits_hops_when_zero() {
        val result = describe(hopsAway = 0)
        assertFalse(result.contains("hops"))
    }

    // ---- Battery ----

    @Test
    fun includes_battery_when_in_valid_range() {
        val result = describe(batteryLevel = 75)
        assertContains(result, "battery 75%")
    }

    @Test
    fun omits_battery_when_zero() {
        val result = describe(batteryLevel = 0)
        assertFalse(result.contains("battery"))
    }

    @Test
    fun omits_battery_when_over_100() {
        val result = describe(batteryLevel = 101)
        assertFalse(result.contains("battery"))
    }

    @Test
    fun omits_battery_when_null() {
        val result = describe(batteryLevel = null)
        assertFalse(result.contains("battery"))
    }

    // ---- Distance ----

    @Test
    fun includes_distance_when_provided() {
        val result = describe(distance = "2.5 km")
        assertContains(result, "2.5 km away")
    }

    // ---- Signal ----

    @Test
    fun signal_hidden_when_snr_is_max_float() {
        val result = describe(snr = Float.MAX_VALUE, rssi = -100, hopsAway = 0, viaMqtt = false)
        assertFalse(result.contains("signal"))
    }

    @Test
    fun signal_hidden_when_via_mqtt() {
        val result = describe(snr = -5f, rssi = -100, hopsAway = 0, viaMqtt = true)
        assertFalse(result.contains("signal"))
    }

    @Test
    fun signal_hidden_when_hops_greater_than_zero() {
        val result = describe(snr = -5f, rssi = -100, hopsAway = 1, viaMqtt = false)
        assertFalse(result.contains("signal"))
    }

    @Test
    fun signal_hidden_when_rssi_not_negative() {
        val result = describe(snr = -5f, rssi = 0, hopsAway = 0, viaMqtt = false)
        assertFalse(result.contains("signal"))
    }

    @Test
    fun signal_shown_when_direct_and_valid_values() {
        val result = describe(snr = -5f, rssi = -100, hopsAway = 0, viaMqtt = false)
        assertContains(result, "signal")
    }
}
