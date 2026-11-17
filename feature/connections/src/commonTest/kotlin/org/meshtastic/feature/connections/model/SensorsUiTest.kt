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
package org.meshtastic.feature.connections.model

import org.meshtastic.node.InternalNodeApi
import org.meshtastic.node.sensors.I2cBusState
import org.meshtastic.node.sensors.I2cSensorState
import org.meshtastic.node.sensors.SensorKind
import org.meshtastic.node.sensors.SensorReads
import org.meshtastic.node.sensors.SensorUnread
import org.meshtastic.node.sensors.i2cBusFailed
import org.meshtastic.node.sensors.i2cBusScanned
import org.meshtastic.node.sensors.i2cSensorState
import org.meshtastic.node.sensors.sensorReads
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

@OptIn(InternalNodeApi::class)
class SensorsUiTest {

    private val clock = TestTimeSource()

    /** A good read that ended [ago] before now. */
    private fun goodRead(ago: Duration, failed: Long = 0, lastError: String? = null): SensorReads {
        val mark = clock.markNow()
        clock += ago
        return sensorReads(succeeded = 1, failed = failed, lastError = lastError, lastSuccess = mark)
    }

    private fun sensor(
        address: Int = SHT,
        name: String? = "SHTXX",
        sensorName: String? = "SHTC3",
        kinds: Set<SensorKind> = setOf(SensorKind.ENVIRONMENT),
        unread: SensorUnread? = null,
        reads: Map<SensorKind, SensorReads> = kinds.associateWith { sensorReads() },
        pollError: String? = null,
    ): I2cSensorState = i2cSensorState(address, name, sensorName, kinds, unread, reads, pollError)

    private fun rows(vararg sensors: I2cSensorState): List<SensorRowUi> =
        (i2cBusScanned(sensors.toList()).toSensorsUi() as SensorsUi.Found).sensors

    private fun row(sensor: I2cSensorState): SensorRowUi = rows(sensor).single()

    @Test
    fun `a bus still being scanned says so`() {
        assertEquals(SensorsUi.Scanning, I2cBusState.Scanning.toSensorsUi())
    }

    @Test
    fun `a bus that failed carries its reason`() {
        assertEquals(SensorsUi.BusFailed("no /dev/i2c-1"), i2cBusFailed("no /dev/i2c-1").toSensorsUi())
    }

    @Test
    fun `a scan that found nothing is not an empty list of rows`() {
        assertEquals(SensorsUi.NoneFound, i2cBusScanned(emptyList()).toSensorsUi())
    }

    @Test
    fun `a row is named by its driver then by the scan and otherwise not at all`() {
        assertEquals("SHTC3", row(sensor()).name)
        assertEquals("SHTXX", row(sensor(sensorName = null, unread = SensorUnread.NO_DRIVER)).name)
        assertEquals(null, row(sensor(name = null, sensorName = null, unread = SensorUnread.UNIDENTIFIED)).name)
    }

    @Test
    fun `the address is two hex digits`() {
        assertEquals("0x70", row(sensor()).address)
        assertEquals("0x08", row(sensor(address = 8)).address)
    }

    @Test
    fun `what a sensor feeds is listed in telemetry order`() {
        val kinds = setOf(SensorKind.POWER, SensorKind.AIR_QUALITY, SensorKind.ENVIRONMENT)
        assertEquals(
            listOf(SensorKind.ENVIRONMENT, SensorKind.AIR_QUALITY, SensorKind.POWER),
            row(sensor(kinds = kinds)).feeds,
        )
    }

    @Test
    fun `a sensor with no read yet is waiting`() {
        assertEquals(SensorRowState.Waiting, row(sensor()).state)
    }

    @Test
    fun `a sensor with a good read is reading and shows no age under a minute`() {
        val reads = mapOf(SensorKind.ENVIRONMENT to goodRead(ago = 59.seconds))
        assertEquals(SensorRowState.Reading(sinceGoodRead = null), row(sensor(reads = reads)).state)
    }

    @Test
    fun `a reading sensor's age is whole minutes since its newest good read`() {
        val kinds = setOf(SensorKind.ENVIRONMENT, SensorKind.AIR_QUALITY)
        val older = goodRead(ago = 7.minutes)
        val newer = goodRead(ago = 150.seconds)
        val reads = mapOf(SensorKind.ENVIRONMENT to older, SensorKind.AIR_QUALITY to newer)
        assertEquals(SensorRowState.Reading(2.minutes), row(sensor(kinds = kinds, reads = reads)).state)
    }

    @Test
    fun `one kind read and another still pending is reading`() {
        val kinds = setOf(SensorKind.ENVIRONMENT, SensorKind.AIR_QUALITY)
        val reads = mapOf(SensorKind.ENVIRONMENT to goodRead(ago = 1.seconds), SensorKind.AIR_QUALITY to sensorReads())
        assertEquals(SensorRowState.Reading(null), row(sensor(kinds = kinds, reads = reads)).state)
    }

    @Test
    fun `a failed read is failing with its error and the age of the last good one`() {
        val reads = mapOf(SensorKind.ENVIRONMENT to goodRead(ago = 5.minutes, failed = 3, lastError = "NACK"))
        assertEquals(SensorRowState.Failing("NACK", 5.minutes), row(sensor(reads = reads)).state)
    }

    @Test
    fun `a sensor that has never read well is failing with no age`() {
        val reads = mapOf(SensorKind.ENVIRONMENT to sensorReads(failed = 1, lastError = "timeout"))
        assertEquals(SensorRowState.Failing("timeout", null), row(sensor(reads = reads)).state)
    }

    @Test
    fun `a failed ask between readings is failing though every read is good`() {
        val reads = mapOf(SensorKind.ENVIRONMENT to goodRead(ago = 1.seconds))
        assertEquals(
            SensorRowState.Failing("no interrupt", null),
            row(sensor(reads = reads, pollError = "no interrupt")).state,
        )
    }

    @Test
    fun `a device nothing reads says why`() {
        SensorUnread.entries.forEach { reason ->
            val unread = sensor(sensorName = null, unread = reason, reads = emptyMap())
            assertEquals(SensorRowState.Unread(reason), row(unread).state)
        }
    }

    @Test
    fun `rows keep the scan's order`() {
        val found = rows(sensor(address = INA, sensorName = "INA219"), sensor(address = SHT))
        assertEquals(listOf("0x40", "0x70"), found.map { it.address })
    }

    private companion object {
        const val SHT = 0x70
        const val INA = 0x40
    }
}
