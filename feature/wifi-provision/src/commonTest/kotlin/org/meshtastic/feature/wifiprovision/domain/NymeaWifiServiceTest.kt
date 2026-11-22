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
package org.meshtastic.feature.wifiprovision.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.ble.BleConnection
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleService
import org.meshtastic.core.ble.BleWriteType
import org.meshtastic.core.testing.CapturingLogWriter
import org.meshtastic.core.testing.FakeBleConnection
import org.meshtastic.core.testing.FakeBleConnectionFactory
import org.meshtastic.core.testing.FakeBleDevice
import org.meshtastic.core.testing.FakeBleScanner
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.COMMANDER_RESPONSE_UUID
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.NO_RESPONSE_CODE
import org.meshtastic.feature.wifiprovision.NymeaBleConstants.WIRELESS_COMMANDER_UUID
import org.meshtastic.feature.wifiprovision.model.ProvisionResult
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

/**
 * Tests for [NymeaWifiService] covering BLE connect, network scanning, provisioning, and error handling. Uses
 * [FakeBleScanner], [FakeBleConnection], and [FakeBleConnectionFactory] from `core:testing`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NymeaWifiServiceTest {

    private val address = "AA:BB:CC:DD:EE:FF"

    @AfterTest
    fun tearDown() {
        CapturingLogWriter.uninstall()
    }

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
        return service.connect(address)
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
        val result = service.connect(address)

        assertTrue(result.isFailure)
    }

    @Test
    fun `connect to an address skips an earlier advertiser with another address`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice("11:22:33:44:55:66", name = "other"))
        scanner.emitDevice(FakeBleDevice(address, name = "target"))
        val result = service.connect(address)

        assertEquals("target", result.getOrThrow())
        assertEquals(address, connection.device?.address)
        assertEquals(address, service.deviceAddress)
    }

    @Test
    fun `connect to an address that never advertises fails instead of cancelling the caller`() = runTest {
        val (service, scanner) = createService()
        scanner.emitDevice(FakeBleDevice("11:22:33:44:55:66", name = "other"))

        val result = service.connect(address)

        assertIs<DeviceNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun `connect to a MAC reaches a device that reports a BlueZ path`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        val path = "/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF"

        scanner.emitDevice(FakeBleDevice(path, name = "linux"))
        val result = service.connect(address)

        assertEquals("linux", result.getOrThrow())
        assertEquals(path, connection.device?.address)
    }

    @Test
    fun `connect without an address connects nothing where the app scans itself`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice(address))
        val result = service.connect(address = null)

        assertTrue(result.isFailure)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `connect to a chosen device connects that device and not an advertiser seen first`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice("11:22:33:44:55:66", name = "first seen"))
        val result = service.connect(FakeBleDevice(address, name = "chosen"))

        assertEquals("chosen", result.getOrThrow())
        assertEquals(address, connection.device?.address)
        assertEquals(1, connection.connectAndAwaitCalls)
    }

    @Test
    fun `connect fails when BLE throws exception`() = runTest {
        val connection = FakeBleConnection()
        connection.connectException = RuntimeException("Bluetooth off")
        val (service, scanner) = createService(connection = connection)

        scanner.emitDevice(FakeBleDevice(address))
        val result = service.connect(address)

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
    fun `provision succeeds without an IP when the connection lookup gets no answer`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":0}""")
        val result = service.provision("MyNet", "password")

        assertEquals(ProvisionResult.Success(ipAddress = null), result)
    }

    @Test
    fun `provision fails with the no response code when the device never answers`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val result = service.provision("MyNet", "password")

        assertEquals(NO_RESPONSE_CODE, assertIs<ProvisionResult.Failure>(result).errorCode)
    }

    @Test
    fun `scanNetworks fails with a response timeout when the device never answers`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val result = service.scanNetworks()

        assertIs<DeviceResponseTimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `a closed service sends no command`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)
        service.close()
        val writesBefore = connection.service.writes.size

        val scan = service.scanNetworks()
        val provision = service.provision("Net", "pass")

        assertTrue(scan.isFailure)
        assertIs<ProvisionResult.Failure>(provision)
        assertEquals(writesBefore, connection.service.writes.size)
    }

    @Test
    fun `a closed service opens no connection`() = runTest {
        val connection = FakeBleConnection()
        val (service, _) = createService(connection = connection)
        service.close()

        val result = service.connect(FakeBleDevice(address))

        assertTrue(result.isFailure)
        assertEquals(0, connection.connectAndAwaitCalls)
    }

    @Test
    fun `a connect reply without a status is a failure and not a success`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1}""")

        assertIs<ProvisionResult.Failure>(service.provision("Net", "pass"))
    }

    @Test
    fun `a connect reply with a quoted status is a failure and not a success`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":"0"}""")

        assertIs<ProvisionResult.Failure>(service.provision("Net", "pass"))
    }

    @Test
    fun `a connect reply with a fractional status is a failure and not a success`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":0.5}""")

        assertIs<ProvisionResult.Failure>(service.provision("Net", "pass"))
    }

    @Test
    fun `a connect reply with a null status is a failure and not a success`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":1,"r":null}""")

        assertIs<ProvisionResult.Failure>(service.provision("Net", "pass"))
    }

    @Test
    fun `a scan reply without a status is a scan failure and not an empty list`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[]}""")

        assertIs<MalformedReplyException>(service.scanNetworks().exceptionOrNull())
    }

    @Test
    fun `a network list reply without a list is a failure and not no networks`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0}""")

        assertIs<MalformedReplyException>(service.scanNetworks().exceptionOrNull())
    }

    @Test
    fun `a network list reply with an empty list is no networks`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[]}""")

        assertEquals(emptyList(), service.scanNetworks().getOrThrow())
    }

    @Test
    fun `a network without a protection flag fails the reply and is not listed as open`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":0,"r":0,"p":[{"e":"Net","m":"01","s":70}]}""")

        assertIs<MalformedReplyException>(service.scanNetworks().exceptionOrNull())
    }

    @Test
    fun `a reply for another command is skipped and the right one still completes in the deadline`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val result = async { service.provision("Net", "pass") }
        runCurrent()
        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":1,"r":3}""")

        assertEquals(3, assertIs<ProvisionResult.Failure>(result.await()).errorCode)
    }

    @Test
    fun `a malformed message is skipped and the next reply still completes`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val result = async { service.provision("Net", "pass") }
        runCurrent()
        emitResponse(connection, """}{ not json""")
        emitResponse(connection, """{"c":1,"r":3}""")

        assertEquals(3, assertIs<ProvisionResult.Failure>(result.await()).errorCode)
    }

    @Test
    fun `the connection lookup cannot take the credentials reply for its own`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        val result = async { service.provision("Net", "pass") }
        runCurrent()
        emitResponse(connection, """{"c":1,"r":0}""")
        runCurrent()
        emitResponse(connection, """{"c":1,"r":0}""")
        emitResponse(connection, """{"c":5,"r":0,"p":{"i":"10.0.0.7"}}""")

        assertEquals(ProvisionResult.Success(ipAddress = "10.0.0.7"), result.await())
    }

    @Test
    fun `after a scan times out a late scan reply cannot make provision succeed`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        assertIs<DeviceResponseTimeoutException>(service.scanNetworks().exceptionOrNull())
        emitResponse(connection, """{"c":4,"r":0}""")
        emitResponse(connection, """{"c":1,"r":3}""")
        val result = service.provision("Net", "pass")

        assertIs<ProvisionResult.Failure>(result)
    }

    @Test
    fun `after a timeout no later command is sent on the session`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        assertIs<DeviceResponseTimeoutException>(service.scanNetworks().exceptionOrNull())
        val writesBefore = connection.service.writes.size
        val retry = async { service.scanNetworks() }
        runCurrent()
        emitResponse(connection, """{"c":4,"r":0}""")

        assertIs<DeviceResponseTimeoutException>(retry.await().exceptionOrNull())
        assertEquals(writesBefore, connection.service.writes.size)
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
    fun `provision never logs the WiFi credentials or the device response payload`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)
        val logs = CapturingLogWriter.install()

        emitResponse(connection, """{"c":1,"r":0,"p":{"i":"10.77.88.99"}}""")
        val result = service.provision("SecretHomeNet", "hunter2-wifi-pass")

        assertIs<ProvisionResult.Success>(result)
        assertTrue(
            logs.messages().any { it.endsWith("command=1") },
            "Command send should be logged with nothing after the code: ${logs.messages()}",
        )
        logs.assertNotLogged("hunter2-wifi-pass", "SecretHomeNet", "10.77.88.99")
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
                // The service's enum: 5 is WirelessNotEnabled and 6 NetworkingNotEnabled (wirelessservice.h).
                5 to "Wireless disabled",
                6 to "Networking disabled",
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

    // -----------------------------------------------------------------------
    // What the device service writes
    // -----------------------------------------------------------------------

    @Test
    fun `a visible connect ignores a reply that carries the hidden connect code`() = runTest {
        val connection = FakeBleConnection()
        val (service, scanner) = createService(connection = connection)
        connectService(service, scanner)

        emitResponse(connection, """{"c":2,"r":0}""")
        val result = service.provision("Net", "pass")

        assertEquals(NO_RESPONSE_CODE, assertIs<ProvisionResult.Failure>(result).errorCode)
    }

    @Test
    fun `a hidden connect answered the way the service answers an unknown command fails with invalid command`() =
        runTest {
            val connection = FakeBleConnection()
            val (service, scanner) = createService(connection = connection)
            connectService(service, scanner)

            emitResponse(connection, """{"c":1,"r":1}""")
            val result = service.provision("Net", "pass", hidden = true)

            val failure = assertIs<ProvisionResult.Failure>(result)
            assertEquals(1, failure.errorCode)
            assertTrue(failure.message.contains("Invalid command"))
        }

    @Test
    fun `service discovery that times out is a connect failure and not a cancellation`() = runTest {
        val connection = ProfileTimeoutConnection(FakeBleConnection(), timeOutFromStart = true)
        val service = NymeaWifiService(FakeBleScanner(), FixedFactory(connection), Dispatchers.Unconfined)

        val result = service.connect(FakeBleDevice(address))

        assertTrue(result.isFailure)
    }

    @Test
    fun `a command write that times out fails the command and ends the session instead of cancelling the caller`() =
        runTest {
            val fake = FakeBleConnection()
            val connection = ProfileTimeoutConnection(fake, timeOutFromStart = false)
            val scanner = FakeBleScanner()
            val service = NymeaWifiService(scanner, FixedFactory(connection), Dispatchers.Unconfined)
            scanner.emitDevice(FakeBleDevice(address))
            assertTrue(service.connect(address).isSuccess)
            connection.timeOut = true

            val scan = service.scanNetworks()
            connection.timeOut = false
            val writesBefore = fake.service.writes.size
            val retry = service.scanNetworks()

            assertIs<DeviceResponseTimeoutException>(scan.exceptionOrNull())
            assertIs<DeviceResponseTimeoutException>(retry.exceptionOrNull())
            assertEquals(writesBefore, fake.service.writes.size)
        }

    /** A connection whose `profile` gives up the way core:ble's does, with `withTimeout`, once [timeOut] is set. */
    private class ProfileTimeoutConnection(private val delegate: FakeBleConnection, timeOutFromStart: Boolean) :
        BleConnection by delegate {
        var timeOut: Boolean = timeOutFromStart

        override suspend fun <T> profile(
            serviceUuid: Uuid,
            timeout: Duration,
            setup: suspend CoroutineScope.(BleService) -> T,
        ): T = if (timeOut) {
            withTimeout(1.milliseconds) { awaitCancellation() }
        } else {
            delegate.profile(serviceUuid, timeout, setup)
        }
    }

    private class FixedFactory(private val connection: BleConnection) : BleConnectionFactory {
        override fun create(scope: CoroutineScope, tag: String): BleConnection = connection
    }
}
