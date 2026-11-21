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
package org.meshtastic.core.data.manager

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.MeshNotificationManager
import org.meshtastic.core.repository.RadioSessionContext
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.User
import org.meshtastic.sdk.MeshEvent
import org.meshtastic.sdk.NodeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RadioSessionEventHandlerTest {

    private class Harness(scope: CoroutineScope, alertScope: CoroutineScope = scope) {
        val radio =
            FakeRadioInterfaceService(scope).apply {
                setDeviceAddress("tcp:events")
                connect()
            }
        val session: RadioSessionContext = checkNotNull(radio.activeSession.value)
        val notifications = mock<MeshNotificationManager>(MockMode.autofill)
        val serviceRepository = FakeServiceRepository()
        val nodeManager = NodeManagerImpl(FakeNodeRepository(), notifications, radio, scope.asServiceScope())
        val handler =
            RadioSessionEventHandler(
                serviceStateWriter = serviceRepository,
                configFlowManager = lazy { error("file manifest is not under test") },
                mqttManager = mock(MockMode.autofill),
                nodeManager = nodeManager,
                serviceNotifications = notifications,
                firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
                radioInterfaceService = radio,
                scope = alertScope.asServiceScope(),
            )

        fun holdNodeWithKey(key: ByteString) {
            nodeManager.updateNode(REMOTE) {
                Node(num = REMOTE, user = User.Builder().also { u -> u.public_key = key }.build(), publicKey = key)
            }
        }
    }

    @Test
    fun `a refused key flags the node and keeps the offered key for comparison`() = runTest {
        val harness = Harness(backgroundScope)
        harness.holdNodeWithKey(heldKey)

        harness.handler.handle(MeshEvent.SecurityWarning.PublicKeyMismatch(NodeId(REMOTE), offeredKey), harness.session)

        val node = harness.nodeManager.nodeDBbyNodeNum.getValue(REMOTE)
        assertFalse(node.keyMatch)
        assertEquals(offeredKey, node.newPublicKey)
        assertEquals(heldKey, node.publicKey)
    }

    @Test
    fun `a refused announcement without a 32-byte key leaves the node alone`() = runTest {
        val harness = Harness(backgroundScope)
        harness.holdNodeWithKey(heldKey)

        harness.handler.handle(
            MeshEvent.SecurityWarning.PublicKeyMismatch(NodeId(REMOTE), ByteString.EMPTY),
            harness.session,
        )

        val node = harness.nodeManager.nodeDBbyNodeNum.getValue(REMOTE)
        assertTrue(node.keyMatch)
        assertNull(node.newPublicKey)
    }

    @Test
    fun `platform-suppressed client notification skips modal state`() = runTest {
        withAlertScope { alerts ->
            val harness = Harness(backgroundScope, alerts)
            val notification = protectedPositionAdvisory()
            every { harness.notifications.suppressClientNotificationModal(notification) } returns true

            harness.handler.handle(MeshEvent.Notification(notification), harness.session)
            runCurrent()

            assertNull(harness.serviceRepository.clientNotification.value)
        }
    }

    @Test
    fun `default platform policy publishes the client notification modal`() = runTest {
        withAlertScope { alerts ->
            val harness = Harness(backgroundScope, alerts)
            val notification = protectedPositionAdvisory()
            every { harness.notifications.suppressClientNotificationModal(notification) } returns false

            harness.handler.handle(MeshEvent.Notification(notification), harness.session)
            runCurrent()

            assertEquals(notification, harness.serviceRepository.clientNotification.value)
        }
    }

    /**
     * The system alert resolves compose resources, which cannot load on a headless JVM; its failure is not under test.
     */
    private suspend fun TestScope.withAlertScope(block: suspend (CoroutineScope) -> Unit) {
        val alerts =
            CoroutineScope(
                SupervisorJob() + StandardTestDispatcher(testScheduler) + CoroutineExceptionHandler { _, _ -> },
            )
        try {
            block(alerts)
        } finally {
            alerts.cancel()
        }
    }

    private fun protectedPositionAdvisory() = ClientNotification.Builder()
        .also { wb ->
            wb.message = "Location sharing is disabled on this channel"
            wb.reply_id = 100
            wb.time = 1_000
            wb.level = LogRecord.Level.WARNING
        }
        .build()

    private companion object {
        const val REMOTE = 0x1234
        val heldKey = ByteArray(Node.PUBLIC_KEY_SIZE) { 0x11 }.toByteString()
        val offeredKey = ByteArray(Node.PUBLIC_KEY_SIZE) { 0x22 }.toByteString()
    }
}
