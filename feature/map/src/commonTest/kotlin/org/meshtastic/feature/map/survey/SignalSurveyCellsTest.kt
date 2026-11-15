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
package org.meshtastic.feature.map.survey

import org.meshtastic.core.model.SignalSample
import org.meshtastic.core.model.snrLimit
import org.meshtastic.core.ui.component.Quality
import org.meshtastic.proto.Config.LoRaConfig.ModemPreset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignalSurveyCellsTest {

    private fun sample(lat: Double, lon: Double, rssi: Int, snr: Float? = null, from: Int = 1, time: Long = 0) =
        SignalSample(
            timeMillis = time,
            latitude = lat,
            longitude = lon,
            accuracyM = 5f,
            fromNum = from,
            rssi = rssi,
            snr = snr,
            direct = true,
        )

    private fun cell(snr: Float?, rssi: Int) = listOf(sample(1.0, 1.0, rssi, snr)).toSurveyCells().single()

    @Test
    fun `readings a few metres apart share a cell that keeps the clearest`() {
        val readings =
            listOf(
                sample(30.00001, -95.00001, rssi = -80, snr = 1f, from = 1, time = 10),
                sample(30.00002, -95.00002, rssi = -95, snr = 6f, from = 2, time = 30),
                sample(30.00002, -95.00002, rssi = -90, snr = 2f, from = 2, time = 20),
            )

        val cell = readings.toSurveyCells().single()

        // The best SNR wins even though another reading was louder, and the RSSI shown is that reading's own.
        assertEquals(6f, cell.bestSnr)
        assertEquals(-95, cell.rssi)
        assertEquals(3, cell.samples)
        assertEquals(2, cell.senders)
        assertEquals(30, cell.lastHeardMillis)
    }

    @Test
    fun `a square keeps the clearest direct reception per node and skips relayed copies`() {
        val readings =
            listOf(
                sample(1.0, 1.0, rssi = -100, snr = 1f, from = 1),
                sample(1.0, 1.0, rssi = -110, snr = 4f, from = 1),
                sample(1.0, 1.0, rssi = -90, snr = 9f, from = 2).copy(direct = false),
            )

        val cell = readings.toSurveyCells().single()

        assertEquals(mapOf(1 to SurveyReception(4f, -110)), cell.direct)
        assertEquals(2, cell.senders)
    }

    @Test
    fun `a reading with an snr outranks a louder one without`() {
        val cell =
            listOf(sample(1.0, 1.0, rssi = -60), sample(1.0, 1.0, rssi = -110, snr = -3f)).toSurveyCells().single()

        assertEquals(-3f, cell.bestSnr)
        assertEquals(-110, cell.rssi)
    }

    @Test
    fun `a cell with no snr reading reports none and keeps the loudest`() {
        val cell = listOf(sample(1.0, 1.0, rssi = -100), sample(1.0, 1.0, rssi = -90)).toSurveyCells().single()

        assertNull(cell.bestSnr)
        assertEquals(-90, cell.rssi)
    }

    @Test
    fun `readings a kilometre apart land in separate cells`() {
        assertEquals(2, listOf(sample(30.0, -95.0, -100), sample(30.01, -95.0, -100)).toSurveyCells().size)
    }

    @Test
    fun `a cell contains its readings and is about fifty metres on each side`() {
        val reading = sample(60.1234, 24.5678, -100)

        val cell = listOf(reading).toSurveyCells().single()

        assertTrue(reading.latitude in cell.south..cell.north)
        assertTrue(reading.longitude in cell.west..cell.east)
        val heightM = (cell.north - cell.south) * 111_320.0
        val widthM = (cell.east - cell.west) * 111_320.0 * cos(reading.latitude * PI / 180.0)
        assertTrue(abs(heightM - 50.0) < 0.5, "height $heightM")
        assertTrue(abs(widthM - 50.0) < 0.5, "width $widthM")
    }

    @Test
    fun `readings south of the equator and west of Greenwich stay inside their cell`() {
        val reading = sample(-33.8688, -70.6693, -100)

        val cell = listOf(reading).toSurveyCells().single()

        assertTrue(reading.latitude in cell.south..cell.north)
        assertTrue(reading.longitude in cell.west..cell.east)
    }

    @Test
    fun `a coarser grid gathers the readings of several fine cells`() {
        val readings = listOf(sample(30.0001, -95.0, -100), sample(30.0010, -95.0, -100), sample(30.0020, -95.0, -100))

        assertEquals(3, readings.toSurveyCells(cellMeters = 50.0).size)
        assertEquals(1, readings.toSurveyCells(cellMeters = 800.0).size)
    }

    @Test
    fun `cells stay at fifty metres when zoomed in to street level`() {
        assertEquals(50.0, surveyCellMeters(zoom = 18.0, latitude = 32.0))
        assertEquals(50.0, surveyCellMeters(zoom = 22.0, latitude = 32.0))
    }

    @Test
    fun `cells double as the map zooms out and never get too small to tap`() {
        var previous = surveyCellMeters(zoom = 18.0, latitude = 32.0)
        for (zoom in 17 downTo 8) {
            val meters = surveyCellMeters(zoom.toDouble(), latitude = 32.0)
            assertTrue(meters == previous || meters == previous * 2, "zoom $zoom: $previous to $meters")
            val metersPerPixel = 156_543.03392 * cos(32.0 * PI / 180.0) / (1 shl zoom)
            assertTrue(meters / metersPerPixel >= 14.0, "zoom $zoom draws ${meters / metersPerPixel} px")
            previous = meters
        }
        assertTrue(previous > 50.0)
    }

    @Test
    fun `cells stop growing at the largest size`() {
        assertEquals(12_800.0, surveyCellMeters(zoom = 2.0, latitude = 0.0))
        assertEquals(12_800.0, surveyCellMeters(zoom = 0.0, latitude = 0.0))
    }

    @Test
    fun `quality is the snr margin over the session preset`() {
        val limit = ModemPreset.LONG_FAST.snrLimit

        assertEquals(Quality.GOOD, cell(snr = limit + 1f, rssi = -120).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.FAIR, cell(snr = limit - 3f, rssi = -60).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.BAD, cell(snr = limit - 7f, rssi = -60).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.NONE, cell(snr = limit - 10f, rssi = -60).quality(ModemPreset.LONG_FAST))
    }

    @Test
    fun `the same snr rates differently on a faster preset`() {
        val snr = ModemPreset.LONG_FAST.snrLimit + 1f

        assertEquals(Quality.GOOD, cell(snr, rssi = -100).quality(ModemPreset.LONG_FAST))
        assertTrue(cell(snr, rssi = -100).quality(ModemPreset.SHORT_FAST) != Quality.GOOD)
    }

    @Test
    fun `a cell with no snr falls back to the rssi bands`() {
        assertEquals(Quality.GOOD, cell(snr = null, rssi = -114).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.FAIR, cell(snr = null, rssi = -115).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.BAD, cell(snr = null, rssi = -120).quality(ModemPreset.LONG_FAST))
        assertEquals(Quality.NONE, cell(snr = null, rssi = -126).quality(ModemPreset.LONG_FAST))
    }

    @Test
    fun `a stored preset name resolves and an unknown one does not`() {
        assertEquals(ModemPreset.LONG_FAST, modemPresetNamed("LONG_FAST"))
        assertNull(modemPresetNamed(null))
        assertNull(modemPresetNamed("NOT_A_PRESET"))
    }
}
