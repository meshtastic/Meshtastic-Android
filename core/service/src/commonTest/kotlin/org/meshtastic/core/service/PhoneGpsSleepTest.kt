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
package org.meshtastic.core.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.repository.Location
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config.DeviceConfig.Role
import org.meshtastic.proto.ModuleSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PhoneGpsSleepTest {
    private fun channel(bits: Int, privateKey: Boolean = true) = ChannelSettings.Builder()
        .also { wb ->
            wb.psk = (if (privateKey) ByteArray(32) { 7 } else byteArrayOf(1)).toByteString()
            wb.module_settings = ModuleSettings.Builder().also { it.position_precision = bits }.build()
        }
        .build()

    @Test
    fun theFirstChannelThatSharesAPositionSetsTheCell() {
        assertEquals(16, stillnessCellBits(listOf(channel(0), channel(16)), Role.CLIENT))
    }

    @Test
    fun aPublicKeyChannelIsJudgedAtTheFifteenBitsItGoesOutAt() {
        assertEquals(15, stillnessCellBits(listOf(channel(18, privateKey = false)), Role.CLIENT))
    }

    @Test
    fun aTrackerJudgesAtItsOwnPrecision() {
        assertEquals(18, stillnessCellBits(listOf(channel(18, privateKey = false)), Role.TRACKER))
    }

    @Test
    fun noCellMeansNoSleep() {
        assertEquals(0, stillnessCellBits(listOf(channel(0)), Role.CLIENT))
        assertEquals(0, stillnessCellBits(listOf(channel(32)), Role.CLIENT))
        assertEquals(0, stillnessCellBits(listOf(channel(16)), Role.LOST_AND_FOUND))
        assertEquals(0, stillnessCellBits(emptyList(), Role.CLIENT))
    }

    private fun fix(latitude: Double) = Location(latitude = latitude, longitude = -0.12, timeMillis = 0)

    private class Feed(private vararg val rounds: List<Location>) {
        var opened = 0
        var closed = 0

        fun next(): Flow<Location> = rounds[opened++].asFlow().onCompletion { closed++ }
    }

    @Test
    fun threeFixesInTheCellStopTheFeedAndMotionStartsItAgain() = runTest {
        val still = listOf(fix(51.5), fix(51.50001), fix(51.5), fix(51.5))
        val feed = Feed(still, listOf(fix(52.0)))
        val sent = mutableListOf<Location>()
        var motions = 0
        feedWhileMoving(
            feed::next,
            cellBits = { 16 },
            awaitMotion = {
                motions++
                true
            },
        ) {
            sent += it
        }
        assertEquals(1, motions)
        assertEquals(2, feed.opened)
        // The third fix in the cell is still sent; the fourth never is, the request is gone.
        assertEquals(listOf(fix(51.5), fix(51.50001), fix(51.5), fix(52.0)), sent)
    }

    @Test
    fun aFixThatLeavesTheCellStartsTheCountAgain() = runTest {
        val feed = Feed(listOf(fix(51.5), fix(51.50001), fix(52.0), fix(52.00001)))
        var motions = 0
        feedWhileMoving(
            feed::next,
            cellBits = { 16 },
            awaitMotion = {
                motions++
                true
            },
        ) {}
        assertEquals(0, motions)
    }

    @Test
    fun noCellNeverSleeps() = runTest {
        val feed = Feed(List(6) { fix(51.5) })
        var motions = 0
        feedWhileMoving(
            feed::next,
            cellBits = { 0 },
            awaitMotion = {
                motions++
                true
            },
        ) {}
        assertEquals(0, motions)
    }

    @Test
    fun aPhoneWithNoMotionSensorKeepsItsGpsOn() = runTest {
        val feed = Feed(List(3) { fix(51.5) }, List(6) { fix(51.5) })
        var motions = 0
        feedWhileMoving(
            feed::next,
            cellBits = { 16 },
            awaitMotion = {
                motions++
                false
            },
        ) {}
        assertEquals(1, motions)
        assertEquals(2, feed.opened)
    }
}
