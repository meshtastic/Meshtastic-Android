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
package org.meshtastic.desktop.radio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.node.MeshLogLevel
import org.meshtastic.node.MeshLogger
import org.meshtastic.node.sensors.I2cBusState
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DesktopNodeSensorsTest {

    @Test
    fun `no bus named is no sensors`() {
        val scope = CoroutineScope(SupervisorJob())
        assertNull(i2cSensors(null, scope, MeshLogger.None))
        assertNull(i2cSensors("  ", scope, MeshLogger.None))
        scope.cancel()
    }

    @Test
    fun `a bus that cannot be opened is said once and reads nothing`() = runBlocking {
        val said = CompletableDeferred<String>()
        val logger = MeshLogger.to(MeshLogLevel.WARN) { _, _, message -> said.complete(message) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sensors = assertNotNull(i2cSensors("/dev/i2c-no-such-bus", scope, logger))
        val message = withTimeout(10.seconds) { said.await() }
        assertTrue("/dev/i2c-no-such-bus" in message, message)
        assertNull(sensors.environment?.read())
        assertNull(sensors.airQuality?.read())
        assertNull(sensors.powerMetrics?.read())
        scope.cancel()
    }

    @Test
    fun `a bus that cannot be opened reports itself failed for the node card`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val bus = assertNotNull(i2cSensors("/dev/i2c-no-such-bus", scope, MeshLogger.None)?.bus)
        val failed = withTimeout(10.seconds) { bus.first { it is I2cBusState.Failed } }
        assertIs<I2cBusState.Failed>(failed)
        assertTrue(failed.reason.isNotBlank())
        scope.cancel()
    }
}
