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
@file:Suppress("MagicNumber")

package org.meshtastic.feature.wifiprovision

import org.meshtastic.feature.wifiprovision.model.WifiNetwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Tests for [WifiProvisionViewModel.deduplicateBySsid]. */
class DeduplicateBySsidTest {

    private fun network(ssid: String, signal: Int, bssid: String = "00:00:00:00:00:00") =
        WifiNetwork(ssid = ssid, bssid = bssid, signalStrength = signal, isProtected = true)

    @Test
    fun `empty list returns empty`() {
        val result = WifiProvisionViewModel.deduplicateBySsid(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single network is returned unchanged`() {
        val input = listOf(network("HomeWifi", 80))
        val result = WifiProvisionViewModel.deduplicateBySsid(input)
        assertEquals(1, result.size)
        assertEquals("HomeWifi", result[0].ssid)
        assertEquals(80, result[0].signalStrength)
    }

    @Test
    fun `duplicate SSIDs keep strongest signal`() {
        val input =
            listOf(
                network("HomeWifi", 50, bssid = "AA:BB:CC:DD:EE:01"),
                network("HomeWifi", 90, bssid = "AA:BB:CC:DD:EE:02"),
                network("HomeWifi", 70, bssid = "AA:BB:CC:DD:EE:03"),
            )
        val result = WifiProvisionViewModel.deduplicateBySsid(input)
        assertEquals(1, result.size)
        assertEquals(90, result[0].signalStrength)
        assertEquals("AA:BB:CC:DD:EE:02", result[0].bssid)
    }

    @Test
    fun `mixed duplicates and unique networks are all handled`() {
        val input =
            listOf(
                network("Alpha", 40),
                network("Beta", 80),
                network("Alpha", 60),
                network("Gamma", 30),
                network("Beta", 50),
            )
        val result = WifiProvisionViewModel.deduplicateBySsid(input)
        assertEquals(3, result.size)
        // Should be sorted by signal strength descending
        assertEquals("Beta", result[0].ssid)
        assertEquals(80, result[0].signalStrength)
        assertEquals("Alpha", result[1].ssid)
        assertEquals(60, result[1].signalStrength)
        assertEquals("Gamma", result[2].ssid)
        assertEquals(30, result[2].signalStrength)
    }

    @Test
    fun `result is sorted by signal strength descending`() {
        val input = listOf(network("Weak", 10), network("Strong", 95), network("Medium", 55))
        val result = WifiProvisionViewModel.deduplicateBySsid(input)
        assertEquals(listOf(95, 55, 10), result.map { it.signalStrength })
    }

    @Test
    fun `preserves isProtected from strongest entry`() {
        val input =
            listOf(
                WifiNetwork(ssid = "Net", bssid = "01", signalStrength = 30, isProtected = false),
                WifiNetwork(ssid = "Net", bssid = "02", signalStrength = 90, isProtected = true),
            )
        val result = WifiProvisionViewModel.deduplicateBySsid(input)
        assertEquals(1, result.size)
        assertTrue(result[0].isProtected, "Should keep isProtected from the strongest-signal entry")
    }
}
