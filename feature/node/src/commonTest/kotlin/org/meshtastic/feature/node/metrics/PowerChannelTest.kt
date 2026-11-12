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
@file:Suppress("DEPRECATION")

package org.meshtastic.feature.node.metrics

import org.meshtastic.proto.PowerMetrics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PowerChannelTest {

    @Test
    fun aCustomLabelWinsOverTheDefault() {
        assertEquals("Solar", powerChannelLabel("Solar", "Channel 1"))
    }

    @Test
    fun aMissingOrBlankLabelFallsBackToChannelN() {
        assertEquals("Channel 2", powerChannelLabel(null, "Channel 2"))
        assertEquals("Channel 2", powerChannelLabel("", "Channel 2"))
        assertEquals("Channel 2", powerChannelLabel("   ", "Channel 2"))
    }

    @Test
    fun aChannelWithOnlyVoltageRendersOnlyVoltage() {
        val texts = powerChannelTexts(voltage = 4.1f, current = null)

        assertEquals("4.10 V", texts.voltage)
        assertNull(texts.current)
    }

    @Test
    fun aChannelWithOnlyCurrentRendersOnlyCurrent() {
        val texts = powerChannelTexts(voltage = null, current = 12.5f)

        assertNull(texts.voltage)
        assertEquals("12.5 mA", texts.current)
    }

    @Test
    fun aReportedZeroStillShows() {
        val texts = powerChannelTexts(voltage = 0f, current = 0f)

        assertEquals("0.00 V", texts.voltage)
        assertEquals("0.0 mA", texts.current)
    }

    @Test
    fun everyChannelTheNodeReportsIsListedNotJustTheFirstThree() {
        val pm =
            PowerMetrics.Builder()
                .also {
                    it.ch1_voltage = 1f
                    it.ch3_current = 2f
                    it.ch5_voltage = 3f
                    it.ch8_current = 4f
                }
                .build()

        assertEquals(
            listOf(PowerChannel.ONE, PowerChannel.THREE, PowerChannel.FIVE, PowerChannel.EIGHT),
            reportingPowerChannels(pm),
        )
    }

    @Test
    fun aCurrentOnlyChannelIsListedAndItsSilentNeighborIsNot() {
        val pm = PowerMetrics.Builder().also { it.ch2_current = 12.5f }.build()

        assertEquals(listOf(PowerChannel.TWO), reportingPowerChannels(pm))
        val texts = powerChannelTexts(PowerChannel.TWO.voltage(pm), PowerChannel.TWO.current(pm))
        assertNull(texts.voltage)
        assertEquals("12.5 mA", texts.current)
    }

    @Test
    fun aNodeThatReportsNothingListsNoChannel() {
        assertEquals(emptyList(), reportingPowerChannels(PowerMetrics.Builder().build()))
        assertEquals(emptyList(), reportingPowerChannels(null))
    }

    @Test
    fun channelAccessorsReadTheirOwnFields() {
        val pm =
            PowerMetrics.Builder()
                .also {
                    it.ch2_voltage = 5f
                    it.ch7_current = 9f
                }
                .build()

        assertEquals(5f, PowerChannel.TWO.voltage(pm))
        assertNull(PowerChannel.TWO.current(pm))
        assertEquals(9f, PowerChannel.SEVEN.current(pm))
        assertNull(PowerChannel.SEVEN.voltage(pm))
    }
}
