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
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.getString
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshNotificationManagerImplTest {

    private lateinit var context: Context
    private lateinit var systemNotificationManager: NotificationManager
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!
        systemNotificationManager.cancelAll()
        clearManagedChannels()
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeInfo?>(null)
    }

    @After
    fun tearDown() {
        systemNotificationManager.cancelAll()
        clearManagedChannels()
    }

    @Test
    fun `initChannels removes legacy categories and creates canonical channels`() = runWithRenderScope { renderScope ->
        NotificationChannels.LEGACY_CATEGORY_IDS.forEach(::createChannel)
        val notifications = createManager(renderScope)
        notifications.initChannels()

        NotificationChannels.LEGACY_CATEGORY_IDS.forEach { legacyId ->
            assertNull(systemNotificationManager.getNotificationChannel(legacyId))
        }

        val canonicalChannelIds =
            listOf(
                NotificationChannels.SERVICE,
                NotificationChannels.MESSAGES,
                NotificationChannels.BROADCASTS,
                NotificationChannels.WAYPOINTS,
                NotificationChannels.ALERTS,
                NotificationChannels.NEW_NODES,
                NotificationChannels.LOW_BATTERY,
                NotificationChannels.LOW_BATTERY_REMOTE,
                NotificationChannels.CLIENT,
            )

        canonicalChannelIds.forEach { channelId ->
            assertNotNull(systemNotificationManager.getNotificationChannel(channelId))
        }
    }

    @Test
    fun `initial foreground notification uses an immediate Android label fallback`() =
        runWithRenderScope { renderScope ->
            val notification = createManager(renderScope).getServiceNotification()
            val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val expected = context.applicationInfo.loadLabel(context.packageManager).toString()

            assertEquals(expected, title)
        }

    @Test
    fun `service state rendering is deferred from the caller`() = runWithRenderScope { renderScope ->
        val notifications = createManager(renderScope)
        notifications.initChannels()

        notifications.updateServiceStateNotification(ConnectionState.Disconnected, populatedTelemetry())

        assertNull(activeServiceNotification())
        advanceUntilIdle()
        assertNotNull(activeServiceNotification())
    }

    @Test
    fun `newer service state cancels a pending render`() = runWithRenderScope { renderScope ->
        val notifications = createManager(renderScope)
        notifications.initChannels()

        notifications.updateServiceStateNotification(ConnectionState.Connecting, populatedTelemetry())
        notifications.updateServiceStateNotification(ConnectionState.Disconnected, populatedTelemetry())

        advanceUntilIdle()
        val title =
            activeServiceNotification()?.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        assertEquals(getString(Res.string.disconnected), title)
    }

    private fun createManager(scope: CoroutineScope) = MeshNotificationManagerImpl(
        context = context,
        packetRepository = lazy<PacketRepository> { error("Not used in this test") },
        nodeRepository = lazy { nodeRepository },
        conversationShortcutPublisher = lazy { error("Not used in this test") },
        radioConfigRepository = lazy { error("Not used in this test") },
        scope = scope,
    )

    private fun populatedTelemetry() =
        Telemetry(local_stats = LocalStats(uptime_seconds = 1, num_online_nodes = 1, num_total_nodes = 1))

    private fun activeServiceNotification() =
        systemNotificationManager.activeNotifications.singleOrNull { it.id == SERVICE_NOTIFY_ID }

    private fun createChannel(id: String) {
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(id, id, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun clearManagedChannels() {
        val channelIds =
            NotificationChannels.LEGACY_CATEGORY_IDS +
                listOf(
                    NotificationChannels.SERVICE,
                    NotificationChannels.MESSAGES,
                    NotificationChannels.BROADCASTS,
                    NotificationChannels.WAYPOINTS,
                    NotificationChannels.ALERTS,
                    NotificationChannels.NEW_NODES,
                    NotificationChannels.LOW_BATTERY,
                    NotificationChannels.LOW_BATTERY_REMOTE,
                    NotificationChannels.CLIENT,
                )

        channelIds.forEach { channelId -> systemNotificationManager.deleteNotificationChannel(channelId) }
    }
}
