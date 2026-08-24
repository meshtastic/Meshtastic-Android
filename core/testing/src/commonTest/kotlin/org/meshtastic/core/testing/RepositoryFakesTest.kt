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
package org.meshtastic.core.testing

import app.cash.turbine.test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.QuickChatAction
import org.meshtastic.core.model.ConnectionEpochs
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.EditSettingsTransactionException
import org.meshtastic.core.repository.LocalNodeUnavailableException
import org.meshtastic.core.repository.PacketQueueRejectedException
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.Channel
import org.meshtastic.proto.ChannelSettings
import org.meshtastic.proto.Config
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Position
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.meshtastic.core.model.Position as ModelPosition

class RepositoryFakesTest {

    @Test
    fun `FakeDeviceHardwareRepository returns seeded hardware and records calls`() = runTest {
        val repo = FakeDeviceHardwareRepository()
        val hw = DeviceHardware(hwModel = 42, hwModelSlug = "TEST", platformioTarget = "tlora")
        repo.setHardware(hwModel = 42, target = "tlora", device = hw)

        val hit = repo.getDeviceHardwareByModel(hwModel = 42, target = "tlora", forceRefresh = false)
        val miss = repo.getDeviceHardwareByModel(hwModel = 99)

        assertEquals(hw, hit.getOrNull())
        assertNull(miss.getOrNull())
        assertEquals(2, repo.recordedCalls.size)
        assertEquals(Triple(42, "tlora", false), repo.recordedCalls.first())
    }

    @Test
    fun `FakeFirmwareReleaseRepository emits stable and alpha releases`() = runTest {
        val repo = FakeFirmwareReleaseRepository()
        val stable = FirmwareRelease(id = "1.0", title = "1.0", pageUrl = "", zipUrl = "")
        val alpha = FirmwareRelease(id = "1.1-a", title = "1.1-a", pageUrl = "", zipUrl = "")

        repo.setStableRelease(stable)
        repo.setAlphaRelease(alpha)

        assertEquals(stable, repo.stableRelease.first())
        assertEquals(alpha, repo.alphaRelease.first())

        repo.invalidateCache()
        repo.invalidateCache()
        assertEquals(2, repo.invalidateCacheCalls)
    }

    @Test
    fun `FakeQuickChatActionRepository upsert delete and reorder`() = runTest {
        val repo = FakeQuickChatActionRepository()
        val a = QuickChatAction(uuid = 1L, name = "A", message = "hi", position = 0)
        val b = QuickChatAction(uuid = 2L, name = "B", message = "bye", position = 1)

        repo.upsert(a)
        repo.upsert(b)
        assertEquals(listOf(a, b), repo.getAllActions().first())

        repo.setItemPosition(uuid = 1L, newPos = 5)
        assertEquals(listOf(2L, 1L), repo.getAllActions().first().map { it.uuid })

        repo.delete(b)
        assertEquals(1, repo.currentActions.size)

        repo.deleteAll()
        assertTrue(repo.currentActions.isEmpty())
    }

    @Test
    fun `FakeQuickChatActionRepository delete compacts positions`() = runTest {
        val repo = FakeQuickChatActionRepository()
        val a = QuickChatAction(uuid = 1L, name = "A", message = "", position = 0)
        val b = QuickChatAction(uuid = 2L, name = "B", message = "", position = 1)
        val c = QuickChatAction(uuid = 3L, name = "C", message = "", position = 2)
        repo.upsert(a)
        repo.upsert(b)
        repo.upsert(c)

        repo.delete(b)

        // Matches real DAO's decrementPositionsAfter: positions must stay contiguous.
        assertEquals(listOf(1L to 0, 3L to 1), repo.currentActions.map { it.uuid to it.position })
    }

    @Test
    fun `FakeTracerouteSnapshotRepository roundtrips positions keyed by log uuid`() = runTest {
        val repo = FakeTracerouteSnapshotRepository()
        val positions = mapOf(1 to Position(latitude_i = 10), 2 to Position(latitude_i = 20))
        repo.upsertSnapshotPositions(logUuid = "log-1", requestId = 99, positions = positions)

        repo.getSnapshotPositions("log-1").test { assertEquals(positions, awaitItem()) }
        assertEquals(99, repo.lastRequestId("log-1"))
        assertNull(repo.lastRequestId("other"))
    }

    @Test
    fun `FakeDatabaseManager associates only the active address`() = runTest {
        val manager = FakeDatabaseManager()
        manager.switchActiveDatabase("active")

        manager.associateDevice(address = "stale", nodeNum = 1, deviceId = "old", isSessionActive = { true })
        assertNull(manager.lastAssociatedAddress)
        assertNull(manager.lastAssociatedNode)
        assertNull(manager.lastAssociatedDeviceId)

        manager.associateDevice(address = "active", nodeNum = 2, deviceId = "inactive", isSessionActive = { false })
        assertNull(manager.lastAssociatedAddress)
        assertNull(manager.lastAssociatedNode)
        assertNull(manager.lastAssociatedDeviceId)

        manager.associateDevice(address = "active", nodeNum = 3, deviceId = "fresh", isSessionActive = { true })
        assertEquals("active", manager.lastAssociatedAddress)
        assertEquals(3, manager.lastAssociatedNode)
        assertEquals("fresh", manager.lastAssociatedDeviceId)
    }

    @Test
    fun `service repository fake preserves connection epoch semantics`() {
        val repository = FakeServiceRepository()

        repository.setConnectionState(ConnectionState.Connected)
        assertEquals(ConnectionEpochs(completedHandshakes = 1), repository.connectionEpochs.value)

        repository.setConnectionState(ConnectionState.Connected)
        assertEquals(ConnectionEpochs(completedHandshakes = 1), repository.connectionEpochs.value)

        repository.setConnectionState(ConnectionState.Connecting)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 1,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Connecting,
            ),
            repository.connectionEpochs.value,
        )

        repository.setConnectionState(ConnectionState.Connected)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 2,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Connecting,
            ),
            repository.connectionEpochs.value,
        )
    }

    @Test
    fun `FakeRadioController preserves configuration and fixed-position evidence until reset`() = runTest {
        val controller = FakeRadioController()
        val local = Config(device = Config.DeviceConfig())
        val admin = Config(lora = Config.LoRaConfig(hop_limit = 5))
        val module = ModuleConfig(serial = ModuleConfig.SerialConfig(enabled = true))
        val position = ModelPosition(latitude = 1.0, longitude = 2.0, altitude = 3)

        controller.setLocalConfig(local)
        controller.setConfig(destNum = 7, config = admin, packetId = 8)
        controller.setModuleConfig(destNum = 7, config = module, packetId = 9)
        controller.setFixedPosition(destNum = 7, position = position)
        controller.setConnectionState(ConnectionState.Connected)

        assertEquals(
            listOf(
                FakeRadioController.ConfigWrite(destination = null, config = local),
                FakeRadioController.ConfigWrite(destination = 7, config = admin),
            ),
            controller.configWrites,
        )
        assertEquals(listOf(local), controller.localConfigs)
        assertEquals(local, controller.lastLocalConfig)
        assertEquals(
            listOf(FakeRadioController.ModuleConfigWrite(destination = 7, config = module)),
            controller.moduleConfigWrites,
        )
        assertEquals(listOf(module), controller.allModuleConfigs)
        assertEquals(listOf(7), controller.moduleConfigDestinations)
        assertEquals(listOf(position), controller.fixedPositions)
        assertEquals(ConnectionState.Connected, controller.connectionState.value)
        assertEquals(ConnectionEpochs(completedHandshakes = 1), controller.connectionEpochs.value)

        controller.reset()

        assertTrue(controller.configWrites.isEmpty())
        assertTrue(controller.localConfigs.isEmpty())
        assertTrue(controller.moduleConfigWrites.isEmpty())
        assertTrue(controller.allModuleConfigs.isEmpty())
        assertTrue(controller.moduleConfigDestinations.isEmpty())
        assertTrue(controller.fixedPositions.isEmpty())
        assertEquals(ConnectionState.Disconnected, controller.connectionState.value)
        assertEquals(
            ConnectionEpochs(
                departures = 1,
                completedHandshakes = 1,
                handshakesAtLastDeparture = 1,
                lastDepartureState = ConnectionState.Disconnected,
            ),
            controller.connectionEpochs.value,
        )
    }

    @Test
    fun `FakeRadioController uses null destination consistently for local config writes`() = runTest {
        val controller = FakeRadioController()
        val config = Config(device = Config.DeviceConfig())
        val moduleConfig = ModuleConfig(serial = ModuleConfig.SerialConfig(enabled = true))

        controller.setConfig(destNum = 0, config = config, packetId = 1)
        controller.setModuleConfig(destNum = 0, config = moduleConfig, packetId = 2)
        controller.editLocalSettings {
            setConfig(config)
            setModuleConfig(moduleConfig)
        }

        assertEquals(
            List(2) { FakeRadioController.ConfigWrite(destination = null, config = config) },
            controller.configWrites,
        )
        assertEquals(
            List(2) { FakeRadioController.ModuleConfigWrite(destination = null, config = moduleConfig) },
            controller.moduleConfigWrites,
        )
        assertEquals(List(2) { moduleConfig }, controller.localModuleConfigs)
        assertEquals(List<Int?>(2) { null }, controller.moduleConfigDestinations)
    }

    @Test
    fun `FakeCommandSender mirrors queued status after successful admission`() = runTest {
        val sender = FakeCommandSender()
        val packet =
            DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value).apply { errorMessage = "original" }

        sender.sendData(packet)

        assertEquals(MessageStatus.QUEUED, packet.status)
        assertTrue(packet.id != 0)
        assertTrue(packet.time > 0)
        val stored = sender.sentPackets.single()
        assertNotSame(packet, stored)
        assertEquals(packet, stored)
        assertEquals("original", stored.errorMessage)

        val storedId = stored.id
        packet.id = storedId + 1
        packet.errorMessage = "caller mutation"
        stored.status = MessageStatus.ERROR
        assertEquals(storedId, sender.sentPackets.single().id)
        assertEquals(MessageStatus.QUEUED, sender.sentPackets.single().status)
        assertEquals("original", sender.sentPackets.single().errorMessage)
    }

    @Test
    fun `FakeCommandSender rejects data packets without a port number before assigning an id`() = runTest {
        val sender = FakeCommandSender()
        val packet = DataPacket(bytes = null, dataType = 0)
        val nextPacketId = sender.getCurrentPacketId()

        assertFailsWith<IllegalArgumentException> { sender.sendData(packet) }
        assertEquals(0, packet.id)
        assertEquals(nextPacketId, sender.getCurrentPacketId())
        assertTrue(sender.sentPackets.isEmpty())
    }

    @Test
    fun `FakeCommandSender mirrors rejected data admission`() = runTest {
        val sender = FakeCommandSender()
        val rejection = PacketQueueRejectedException("Test packet")
        val packet = DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value, time = 123L)
        sender.sendDataFailure = rejection

        val failure = assertFailsWith<PacketQueueRejectedException> { sender.sendData(packet) }

        assertSame(rejection, failure)
        assertEquals(MessageStatus.ERROR, packet.status)
        assertEquals(123L, packet.time)
        assertTrue(sender.sentPackets.isEmpty())
    }

    @Test
    fun `FakeCommandSender records retryable admin and telemetry requests and clears them on reset`() = runTest {
        val sender = FakeCommandSender()
        val adminMessage = AdminMessage(get_device_metadata_request = true)

        sender.sendAdmin(destNum = 123, requestId = 7, wantResponse = true) { adminMessage }
        sender.requestTelemetry(requestId = 8, destNum = 456, typeValue = 2)

        assertEquals(
            listOf(FakeCommandSender.AdminRequest(123, 7, wantResponse = true, adminMessage)),
            sender.adminRequests,
        )
        assertEquals(listOf(FakeCommandSender.TelemetryRequest(8, 456, 2)), sender.telemetryRequests)

        sender.reset()

        assertTrue(sender.adminRequests.isEmpty())
        assertTrue(sender.telemetryRequests.isEmpty())
    }

    @Test
    fun `FakeCommandSender accepts local-node failures on command paths`() = runTest {
        val sender = FakeCommandSender()
        sender.commandFailure = LocalNodeUnavailableException("Test command")

        assertFailsWith<LocalNodeUnavailableException> { sender.requestUserInfo(123) }
    }

    @Test
    fun `FakeCommandSender applies command failure before data mutation`() = runTest {
        val failure = LocalNodeUnavailableException("Test command")
        val sender = FakeCommandSender().apply { commandFailure = failure }
        val packet = DataPacket(bytes = null, dataType = PortNum.TEXT_MESSAGE_APP.value)

        assertSame(failure, assertFailsWith<LocalNodeUnavailableException> { sender.sendData(packet) })
        assertEquals(0, packet.id)
        assertTrue(sender.sentPackets.isEmpty())
    }

    @Test
    fun `FakeCommandSender applies command failure before immediate command mutation`() {
        val failure = PacketQueueRejectedException("queue closed")
        val sender = FakeCommandSender().apply { commandFailure = failure }
        val message = AdminMessage(set_time_only = 123)

        assertSame(failure, assertFailsWith<PacketQueueRejectedException> { sender.sendAdminImmediate(7) { message } })
        assertSame(
            failure,
            assertFailsWith<PacketQueueRejectedException> {
                sender.sendLockdownPassphrase("secret", boots = 1, hours = 2, maxSessionSeconds = 3, disable = false)
            },
        )
        assertSame(failure, assertFailsWith<PacketQueueRejectedException> { sender.sendLockNow() })

        assertTrue(sender.adminRequests.isEmpty())
        assertNull(sender.lastPassphrase)
        assertFalse(sender.lockNowCalled)
    }

    @Test
    fun `FakeCommandSender records connection ownership on post-handshake requests`() = runTest {
        val sender = FakeCommandSender()

        sender.sendAdminForConnection(destNum = 123, expectedConnectionVersion = 41, requestId = 7) { AdminMessage() }
        sender.requestTelemetryForConnection(
            requestId = 8,
            destNum = 123,
            typeValue = 2,
            expectedConnectionVersion = 41,
        )

        assertEquals(41L, sender.adminRequests.single().expectedConnectionVersion)
        assertEquals(41L, sender.telemetryRequests.single().expectedConnectionVersion)
    }

    @Test
    fun `FakeRadioTransport is terminal after close`() = runTest {
        val transport = FakeRadioTransport()
        val outbound = byteArrayOf(1, 2, 3)

        assertTrue(transport.handleSendToRadio(outbound))
        outbound[0] = 9
        val recorded = transport.sentData.single()
        recorded[1] = 9
        transport.close()
        transport.clearKeepAlive()

        assertFalse(transport.handleSendToRadio(byteArrayOf(4, 5, 6)))
        transport.keepAlive()
        assertFalse(transport.keepAliveCalled)
        assertEquals(1, transport.sentData.size)
        assertContentEquals(byteArrayOf(1, 2, 3), transport.sentData.single())
    }

    @Test
    fun `FakeRadioController scopes standalone hooks per write`() = runTest {
        val controller = FakeRadioController()
        val transactionStarted = CompletableDeferred<Unit>()
        val finishTransaction = CompletableDeferred<Unit>()
        val standaloneHooks = mutableListOf<String>()
        controller.onStandaloneConfig = { standaloneHooks += "config" }
        controller.onStandaloneModuleConfig = { standaloneHooks += "module" }

        val transaction = async {
            controller.editSettings(destNum = 7) {
                transactionStarted.complete(Unit)
                finishTransaction.await()
                setConfig(Config(device = Config.DeviceConfig()))
                setModuleConfig(ModuleConfig(serial = ModuleConfig.SerialConfig(enabled = true)))
            }
        }
        transactionStarted.await()

        controller.setConfig(destNum = 8, config = Config(power = Config.PowerConfig()), packetId = 1)
        controller.setModuleConfig(
            destNum = 8,
            config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true)),
            packetId = 2,
        )
        finishTransaction.complete(Unit)
        transaction.await()

        assertEquals(listOf("config", "module"), standaloneHooks)
        assertEquals(
            listOf(
                FakeRadioController.ConfigWrite(destination = 8, config = Config(power = Config.PowerConfig())),
                FakeRadioController.ConfigWrite(destination = 7, config = Config(device = Config.DeviceConfig())),
            ),
            controller.configWrites,
        )
        assertEquals(
            listOf(
                FakeRadioController.ModuleConfigWrite(
                    destination = 8,
                    config = ModuleConfig(mqtt = ModuleConfig.MQTTConfig(enabled = true)),
                ),
                FakeRadioController.ModuleConfigWrite(
                    destination = 7,
                    config = ModuleConfig(serial = ModuleConfig.SerialConfig(enabled = true)),
                ),
            ),
            controller.moduleConfigWrites,
        )
        assertEquals(
            listOf("begin", "config:update", "module:update", "config:update", "module:update", "commit"),
            controller.adminOperations,
        )
    }

    @Test
    fun `FakeRadioController records owner destinations consistently`() = runTest {
        val controller = FakeRadioController()
        val localOwner = User(id = "!00000001", long_name = "Local")
        val remoteOwner = User(id = "!00000002", long_name = "Remote")

        controller.setOwner(destNum = 0, user = localOwner, packetId = 1)
        controller.setOwner(destNum = 7, user = remoteOwner, packetId = 2)

        assertEquals(
            listOf(
                FakeRadioController.OwnerWrite(destination = null, user = localOwner),
                FakeRadioController.OwnerWrite(destination = 7, user = remoteOwner),
            ),
            controller.ownerWrites,
        )

        controller.reset()
        assertTrue(controller.ownerWrites.isEmpty())
    }

    @Test
    fun `FakeRadioController rejects edit settings before running writes when begin fails`() = runTest {
        val controller = FakeRadioController().apply { failEditSettingsBegin = true }
        var blockRan = false

        assertFailsWith<EditSettingsTransactionException> { controller.editLocalSettings { blockRan = true } }

        assertFalse(blockRan)
        assertTrue(controller.editSettingsCalled)
        assertEquals(listOf(0), controller.editSettingsDestinations)
        assertTrue(controller.adminOperations.isEmpty())
    }

    @Test
    fun `FakeRadioController reset clears begin-boundary failure hook`() {
        val controller = FakeRadioController().apply { failEditSettingsBegin = true }

        controller.reset()

        assertFalse(controller.failEditSettingsBegin)
    }

    @Test
    fun `FakeRadioController commits after block failure and preserves failure precedence`() = runTest {
        val controller = FakeRadioController()
        val blockFailure = IllegalStateException("write failed")
        val commitFailure = IllegalArgumentException("commit failed")
        controller.onEditSettingsCommitted = { throw commitFailure }

        val failure = assertFailsWith<IllegalStateException> { controller.editLocalSettings { throw blockFailure } }

        assertSame(blockFailure, failure)
        assertEquals(listOf(commitFailure.message), failure.suppressedExceptions.map(Throwable::message))
        assertIs<IllegalArgumentException>(failure.suppressedExceptions.single())
        assertEquals(listOf("begin", "commit"), controller.adminOperations)
    }

    @Test
    fun `FakeRadioController preserves a distinct same-looking commit failure`() = runTest {
        val controller = FakeRadioController()
        val blockFailure = IllegalStateException("same recovered failure")
        controller.onEditSettingsCommitted = { throw IllegalStateException("same recovered failure") }

        val failure = assertFailsWith<IllegalStateException> { controller.editLocalSettings { throw blockFailure } }

        assertSame(blockFailure, failure)
        assertEquals(1, failure.suppressedExceptions.size)
        assertNotSame(blockFailure, failure.suppressedExceptions.single())
        assertEquals(blockFailure.message, failure.suppressedExceptions.single().message)
    }

    @Test
    fun `FakeRadioController does not suppress a recovered copy of the block failure`() = runTest {
        val controller = FakeRadioController()
        val blockFailure = IllegalStateException("same recovered failure")
        controller.onEditSettingsCommitted = { throw blockFailure }

        val failure = assertFailsWith<IllegalStateException> { controller.editLocalSettings { throw blockFailure } }

        assertSame(blockFailure, failure)
        assertTrue(failure.suppressedExceptions.isEmpty())
    }

    @Test
    fun `FakeRadioConfigRepository tracks channel set and module config`() = runTest {
        val repo = FakeRadioConfigRepository()
        val a = ChannelSettings(name = "A")
        val b = ChannelSettings(name = "B")

        repo.replaceAllSettings(listOf(a, b))
        assertEquals(listOf(a, b), repo.currentChannelSet.settings)

        repo.updateChannelSettings(Channel(index = 1, settings = ChannelSettings(name = "B2")))
        assertEquals("B2", repo.currentChannelSet.settings[1].name)

        repo.clearChannelSet()
        assertTrue(repo.currentChannelSet.settings.isEmpty())
    }
}
