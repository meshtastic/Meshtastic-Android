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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.FirmwareUpdateDestination
import org.meshtastic.core.model.FirmwareUpdateNotice
import org.meshtastic.core.model.MeshBeaconOffer
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.notificationId
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.DuplicatedPublicKey
import org.meshtastic.proto.KeyVerificationFinal
import org.meshtastic.proto.KeyVerificationNumberInform
import org.meshtastic.proto.KeyVerificationNumberRequest
import org.meshtastic.proto.LogRecord
import org.meshtastic.proto.LowEntropyKey
import org.meshtastic.proto.MeshBeacon
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.core.repository.Notification as MeshNotification

/** Every notification that is not a conversation: its channel, tray slot, tap target and alerting flags. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshNotificationManagerImplRoutingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!

    @Before
    fun setUp() {
        val main = ComponentName(context, "org.meshtastic.app.MainActivity")
        val activityInfo =
            ActivityInfo().apply {
                name = main.className
                packageName = main.packageName
                exported = true
            }
        shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
        systemNotificationManager.cancelAll()
        NotificationChannelSpec.entries.forEach { systemNotificationManager.deleteNotificationChannel(it.id) }
    }

    @After
    fun tearDown() {
        systemNotificationManager.cancelAll()
    }

    /**
     * Android Auto treats a MessagingStyle notification as a conversation it can read aloud and answer, and requires
     * reply and mark-as-read on it. Only conversations carry those, so nothing else may be MessagingStyle.
     */
    @Test
    fun `nothing but a conversation looks like one to Android Auto`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        manager.updateWaypointNotification("0^all", "Hawk Ridge", "Camp", waypointId = 42)
        manager.showAlertNotification("0!abcd1234", "Hawk Ridge", "Fire at camp")
        manager.showMeshBeaconNotification(
            MeshBeaconOffer(fromNodeNum = 7, beacon = MeshBeacon.Builder().also { wb -> wb.message = "Join" }.build()),
        )
        manager.showNewNodeSeenNotification(Node(num = 101), "New node seen: N101")
        manager.showLowBatteryNotification(Node(num = 2), isRemote = true)
        manager.showClientNotification(
            ClientNotification.Builder().also { wb -> wb.message = "Duplicate key" }.build(),
            "Radio notice",
            MeshNotification.Type.Warning,
        )
        manager.showFirmwareUpdateNotice(FirmwareUpdateDestination.AndroidUpdate)
        manager.showReconnectBlockedNotification("Meshtastic can't reconnect", "Bluetooth is off")

        val posted = systemNotificationManager.activeNotifications
        assertEquals(8, posted.size)
        posted.forEach { sbn ->
            assertNull(
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification),
                "${sbn.tag} must not be MessagingStyle",
            )
        }
    }

    @Test
    fun `a received waypoint posts on the waypoint channel and opens the map at it`() = runWithRenderScope { scope ->
        createManager(scope).updateWaypointNotification("0^all", "Hawk Ridge", "Camp", waypointId = 42)

        val posted = activeByTag("waypoint").single().notification
        assertEquals(NotificationChannels.WAYPOINTS, posted.channelId)
        assertNull(posted.group, "a waypoint is not a conversation and must stay out of the messages summary")
        assertEquals("meshtastic://meshtastic/map?waypointId=42", tapTarget(posted))
    }

    @Test
    fun `a critical alert posts as an alarm that opens its conversation`() = runWithRenderScope { scope ->
        createManager(scope).showAlertNotification("0!abcd1234", "Hawk Ridge", "Fire at camp")

        val posted = activeByTag("alert").single().notification
        assertEquals(NotificationChannels.ALERTS, posted.channelId)
        assertEquals(Notification.CATEGORY_ALARM, posted.category)
        assertEquals("meshtastic://meshtastic/messages/0!abcd1234", tapTarget(posted))
    }

    @Test
    fun `a mesh invitation posts on its own channel and opens discovery`() = runWithRenderScope { scope ->
        val offer =
            MeshBeaconOffer(fromNodeNum = 7, beacon = MeshBeacon.Builder().also { wb -> wb.message = "Join" }.build())

        createManager(scope).showMeshBeaconNotification(offer)

        val posted = activeByTag("mesh_beacon").single()
        assertEquals(7, posted.id)
        assertEquals(NotificationChannels.MESH_BEACON, posted.notification.channelId)
        assertEquals("meshtastic://meshtastic/discovery", tapTarget(posted.notification))
    }

    @Test
    fun `a new node posts under its own tag and is cancelled by node number`() = runWithRenderScope { scope ->
        val manager = createManager(scope)

        manager.showNewNodeSeenNotification(Node(num = 101), "New node seen: N101")

        val posted = activeByTag("new_node").single()
        assertEquals(101, posted.id)
        assertEquals(NotificationChannels.NEW_NODES, posted.notification.channelId)
        assertEquals("New node seen: N101", posted.notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("meshtastic://meshtastic/nodes/101", tapTarget(posted.notification))

        manager.cancelNewNodeNotification(101)
        assertTrue(activeByTag("new_node").isEmpty())
    }

    @Test
    fun `low battery posts on the channel for whose battery it is`() = runWithRenderScope { scope ->
        val manager = createManager(scope)

        manager.showLowBatteryNotification(Node(num = 1), isRemote = false)
        manager.showLowBatteryNotification(Node(num = 2), isRemote = true)

        val byNode = activeByTag("low_battery").associateBy { it.id }
        assertEquals(NotificationChannels.LOW_BATTERY, byNode.getValue(1).notification.channelId)
        assertEquals(NotificationChannels.LOW_BATTERY_REMOTE, byNode.getValue(2).notification.channelId)
        assertEquals("meshtastic://meshtastic/nodes/2", tapTarget(byNode.getValue(2).notification))
        // Ongoing notifications never bridge to a watch.
        assertEquals(0, byNode.getValue(1).notification.flags and Notification.FLAG_ONGOING_EVENT)
    }

    @Test
    fun `a low-battery refresh never brings back a dismissed warning`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        val node = Node(num = 3)

        manager.showLowBatteryNotification(node, isRemote = false)
        manager.cancelLowBatteryNotification(node)
        manager.updateLowBatteryNotification(node, isRemote = false)

        assertTrue(activeByTag("low_battery").isEmpty())
    }

    @Test
    fun `a warning still building when the battery recovers is never posted`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        val node = Node(num = 4)
        manager.beforeLowBatteryPost = { manager.cancelLowBatteryNotification(node) }

        manager.showLowBatteryNotification(node, isRemote = false)

        assertTrue(activeByTag("low_battery").isEmpty())
    }

    @Test
    fun `radio notices post on their own channel and clear by identity`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        val notice = ClientNotification.Builder().also { wb -> wb.message = "Generic warning" }.build()

        manager.showClientNotification(notice, "Radio notice", MeshNotification.Type.Warning)

        val posted = activeByTag("client").single()
        assertEquals(notice.notificationId(), posted.id)
        assertEquals(NotificationChannels.CLIENT, posted.notification.channelId)
        assertEquals(0, posted.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE)

        manager.clearClientNotification(notice)
        assertTrue(activeByTag("client").isEmpty())
    }

    @Test
    fun `repeated position advisories share one tray slot and alert once`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        val first = protectedPositionAdvisory(replyId = 123, time = 1_000)
        val second = protectedPositionAdvisory(replyId = 456, time = 2_000)

        listOf(first, second).forEach { manager.showClientNotification(it, "Radio notice", MeshNotification.Type.Info) }

        val posted = activeByTag("client").single()
        assertTrue(posted.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(manager.suppressClientNotificationModal(first))
    }

    @Test
    fun `only the exact position advisory skips the in-app modal`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        val advisory = protectedPositionAdvisory(replyId = 123, time = 1_000)
        val nearMisses =
            listOf(
                advisory.newBuilder().also { wb -> wb.message = "Location sharing is disabled" }.build(),
                advisory.newBuilder().also { wb -> wb.level = LogRecord.Level.INFO }.build(),
                advisory.newBuilder().also { wb -> wb.reply_id = 0 }.build(),
                advisory.newBuilder().also { wb -> wb.reply_id = null }.build(),
                advisory
                    .newBuilder()
                    .also { wb -> wb.key_verification_number_inform = KeyVerificationNumberInform.Builder().build() }
                    .build(),
                advisory
                    .newBuilder()
                    .also { wb -> wb.key_verification_number_request = KeyVerificationNumberRequest.Builder().build() }
                    .build(),
                advisory
                    .newBuilder()
                    .also { wb -> wb.key_verification_final = KeyVerificationFinal.Builder().build() }
                    .build(),
                advisory
                    .newBuilder()
                    .also { wb -> wb.duplicated_public_key = DuplicatedPublicKey.Builder().build() }
                    .build(),
                advisory.newBuilder().also { wb -> wb.low_entropy_key = LowEntropyKey.Builder().build() }.build(),
                advisory.newBuilder().also { wb -> wb.message = "Rebooting to WiFi OTA" }.build(),
            )

        assertTrue(manager.suppressClientNotificationModal(advisory))
        nearMisses.forEach { assertFalse(manager.suppressClientNotificationModal(it), it.toString()) }
    }

    @Test
    fun `a firmware update opens in-app firmware updates whatever its destination`() = runWithRenderScope { scope ->
        val accepted = createManager(scope).showFirmwareUpdateNotice(FirmwareUpdateDestination.MeshtasticFlasher)

        assertTrue(accepted)
        val posted = activeByTag("firmware_update").single().notification
        assertEquals(NotificationChannels.DEVICE_STATUS, posted.channelId)
        assertEquals("meshtastic://meshtastic/firmware/update", tapTarget(posted))
    }

    @Test
    fun `a firmware update on a blocked channel reports that it was not shown`() = runWithRenderScope { scope ->
        val manager = createManager(scope)
        manager.ensureChannels()
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(NotificationChannels.DEVICE_STATUS, "blocked", NotificationManager.IMPORTANCE_NONE),
        )

        assertFalse(manager.showFirmwareUpdateNotice(FirmwareUpdateDestination.AndroidUpdate))
        assertTrue(activeByTag("firmware_update").isEmpty())
    }

    @Test
    fun `the reconnect-blocked warning replaces itself and opens connections`() = runWithRenderScope { scope ->
        val manager = createManager(scope)

        repeat(2) { manager.showReconnectBlockedNotification("Meshtastic can't reconnect", "Bluetooth is off") }

        val posted = activeByTag("reconnect_blocked").single().notification
        assertEquals(NotificationChannels.DEVICE_STATUS, posted.channelId)
        assertEquals("meshtastic://meshtastic/connections", tapTarget(posted))
    }

    private suspend fun MeshNotificationManagerImpl.showFirmwareUpdateNotice(destination: FirmwareUpdateDestination) =
        showFirmwareUpdateNotification(
            FirmwareUpdateNotice(
                notificationKey = "node|2.8.0",
                currentVersion = "2.7.0",
                stableVersion = "2.8.0",
                destination = destination,
            ),
        )

    private fun createManager(scope: CoroutineScope) = MeshNotificationManagerImpl(
        context = context,
        packetRepository = lazy { error("Not used in this test") },
        nodeRepository = lazy { error("Not used in this test") },
        conversationShortcutPublisher = lazy { error("Not used in this test") },
        radioConfigRepository = lazy { error("Not used in this test") },
        radioOperationLock = RadioOperationLock(),
        firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
        scope = scope.asServiceScope(),
    )

    private fun activeByTag(tag: String): List<StatusBarNotification> =
        systemNotificationManager.activeNotifications.filter { it.tag == tag }

    private fun tapTarget(notification: Notification): String? =
        shadowOf(notification.contentIntent).savedIntent.data?.toString()

    private fun protectedPositionAdvisory(replyId: Int, time: Int) = ClientNotification.Builder()
        .also { wb ->
            wb.message = PROTECTED_POSITION_ADVISORY_MESSAGE
            wb.reply_id = replyId
            wb.time = time
            wb.level = LogRecord.Level.WARNING
        }
        .build()
}
