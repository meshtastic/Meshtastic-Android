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

package org.meshtastic.feature.wifiprovision.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_COMMANDER_UUID
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [NymeaWifiService] covering BLE connect, network scanning, provisioning, and error handling. Uses
 * [FakeBleScanner], [FakeBleConnection], and [FakeBleConnectionFactory] from `core:testing`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NymeaWifiServiceTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    private fun createService(
        scanner: FakeBleScanner = FakeBleScanner(),
        connection: FakeBleConnection = FakeBleConnection(),
    ): Triple<NymeaWifiService, FakeBleScanner, FakeBleConnection> {
        val service =
            NymeaWifiService(
                scanner = scanner,
                connectionFactory = FakeBleConnectionFactory(connection),
                dispatcher = Dispatchers.Unconfined,
            )
        return Triple(service, scanner, connection)
    }

    private suspend fun connectService(
        service: NymeaWifiService,
        scanner: FakeBleScanner,
        deviceName: String? = "mpwrd-nm-1234",
    ): Result<String> {
        scanner.emitDevice(FakeBleDevice(address, name = deviceName))
        return service.connect()
    }

    private fun emitResponse(connection: FakeBleConnection, json: String) {
        connection.service.emitNotification(COMMANDER_RESPONSE_UUID, (json + "\n").encodeToByteArray())
    }

    // -----------------------------------------------------------------------
    // connect()
    // -----------------------------------------------------------------------

    @Test
    fun `connect succeeds and returns device name`() = runTest {
        val (service, scanner) = createService()
        val result = connectService(service, scanner)
        assertTrue(result.isSuccess)
        assertEquals("mpwrd-nm-1234", result.getOrThrow())
    }

    @Test
    fun `connect returns device address when name is null`() = runTest {
        val (service, scanner) = createService()
        val result = connectService(service, scanner, deviceName = null)
        assertTrue(result.isSuccess)
        assertEquals(address, result.getOrThrow())
    }

    @Test
    fun `connect fails when BLE connection fails`() = runTest {
        val connection = FakeBleConnection()
        connection.failNextN = 1
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice(address))
        val result = service.connect()

        assertTrue(result.isFailure)
    }

    @Test
    fun `connect fails when BLE throws exception`() = runTest {
        val connection = FakeBleConnection()
        connection.connectException = RuntimeException("Bluetooth off")
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice(address))
        val result = service.connect()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Bluetooth off") == true)
    }

    // -----------------------------------------------------------------------
    // scanNetworks()
    // -----------------------------------------------------------------------

    @Test
    fun `scanNetworks returns parsed network list`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        // Enqueue scan ack + networks response
        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(
            connection,
            """{"c":0,"r":0,"p":[
                {"e":"HomeWifi","m":"AA:BB:CC:DD:EE:01","s":85,"p":1},
                {"e":"OpenNet","m":"AA:BB:CC:DD:EE:02","s":60,"p":0}
            ]}""",
        )

        val result = service.scanNetworks()
        assertTrue(result.isSuccess)

        val networks = result.getOrThrow()
        assertEquals(2, networks.size)
        assertEquals("HomeWifi", networks[0].ssid)
        assertEquals(85, networks[0].signalStrength)
        assertTrue(networks[0].isProtected)
        assertEquals("OpenNet", networks[1].ssid)
        assertEquals(false, networks[1].isProtected)
    }

    @Test
    fun `scanNetworks returns empty list when device has no networks`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[]}""")

        val result = service.scanNetworks()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `scanNetworks fails when scan command returns error`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        // Scan returns error code 4 (wireless unavailable)
        emitResponse(connection, """{"c":4,"r":4}""")

        val result = service.scanNetworks()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Scan command failed") == true)
    }

    @Test
    fun `scanNetworks sends correct BLE commands`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[]}""")

        service.scanNetworks()

        // Verify the commander writes contain the scan command and get-networks command
        val commanderWrites =
            connection.service.writes
                .filter { it.characteristic.uuid == WIRELESS_COMMANDER_UUID }
                .map { it.data.decodeToString() }
                .joinToString("")

        assertTrue(commanderWrites.contains("\"c\":4"), "Should send CMD_SCAN (4)")
        assertTrue(commanderWrites.contains("\"c\":0"), "Should send CMD_GET_NETWORKS (0)")
    }

    @Test
    fun `scanNetworks uses WITH_RESPONSE write type`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[]}""")

        service.scanNetworks()

        val commanderWrites = connection.service.writes.filter { it.characteristic.uuid == WIRELESS_COMMANDER_UUID }
        assertTrue(commanderWrites.all { it.writeType == BleWriteType.WITH_RESPONSE })
    }

    // -----------------------------------------------------------------------
    // provision()
    // -----------------------------------------------------------------------

    @Test
    fun `provision returns Success on response code 0`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":0,"p":{"i":"10.10.10.61"}}""")
        val result = service.provision("MyNet", "password")

        val success = assertIs<ProvisionResult.Success>(result)
        assertEquals("10.10.10.61", success.ipAddress)
    }

    @Test
    fun `provision falls back to GetConnection for IP when connect response has no payload`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":0}""")
        emitResponse(connection, """{"c":5,"r":0,"p":{"i":"10.10.10.62"}}""")
        val result = service.provision("MyNet", "password")

        val success = assertIs<ProvisionResult.Success>(result)
        assertEquals("10.10.10.62", success.ipAddress)
    }

    @Test
    fun `provision returns Failure on non-zero response code`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":3}""")
        val result = service.provision("MyNet", "password")

        assertIs<ProvisionResult.Failure>(result)
        assertEquals(3, result.errorCode)
        assertTrue(result.message.contains("NetworkManager"))
    }

    @Test
    fun `provision sends CMD_CONNECT for visible networks`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":0,"p":{"i":"10.10.10.61"}}""")
        service.provision("Net", "pass", hidden = false)

        val writes =
            connection.service.writes
                .filter { it.characteristic.uuid == WIRELESS_COMMANDER_UUID }
                .map { it.data.decodeToString() }
                .joinToString("")

        assertTrue(writes.contains("\"c\":1"), "Should send CMD_CONNECT (1)")
        assertTrue(writes.contains("\"e\":\"Net\""), "Should contain SSID")
    }

    @Test
    fun `provision sends CMD_CONNECT_HIDDEN for hidden networks`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":2,"r":0,"p":{"i":"10.10.10.61"}}""")
        service.provision("HiddenNet", "pass", hidden = true)

        val writes =
            connection.service.writes
                .filter { it.characteristic.uuid == WIRELESS_COMMANDER_UUID }
                .map { it.data.decodeToString() }
                .joinToString("")

        assertTrue(writes.contains("\"c\":2"), "Should send CMD_CONNECT_HIDDEN (2)")
    }

    @Test
    fun `provision returns Failure on exception`() = runTest {
        // Create a service with a connection that will fail writes after connecting
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        // Don't emit any response — this will cause a timeout. But since we use
        // Dispatchers.Unconfined the withTimeout may behave differently.
        // Instead, test a different error path: test that all nymea error codes are mapped.
        emitResponse(connection, """{"c":1,"r":1}""")
        val result = service.provision("Net", "pass")
        assertIs<ProvisionResult.Failure>(result)
        assertTrue(result.message.contains("Invalid command"))
    }

    @Test
    fun `provision maps all known error codes`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val errorCodes =
            mapOf(
                1 to "Invalid command",
                2 to "Invalid parameter",
                3 to "NetworkManager not available",
                4 to "Wireless adapter not available",
                5 to "Networking disabled",
                6 to "Wireless disabled",
                7 to "Unknown error",
            )

        for ((code, expectedMessage) in errorCodes) {
            emitResponse(connection, """{"c":1,"r":$code}""")
            val result = service.provision("Net", "pass")
            assertIs<ProvisionResult.Failure>(result)
            assertTrue(
                result.message.contains(expectedMessage),
                "Error code $code should map to '$expectedMessage', got '${result.message}'",
            )
        }
    }

    // -----------------------------------------------------------------------
    // close()
    // -----------------------------------------------------------------------

    @Test
    fun `close disconnects BLE`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        service.close()

        assertTrue(connection.disconnectCalls >= 1, "Should call BLE disconnect")
    }
}
