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
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.repository.FirmwareUpdateProgress
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.firmware_update_in_progress
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.local_stats_nodes
import org.meshtastic.core.testing.runUntilSettled
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshNotificationManagerImplTest {

    private lateinit var context: Context
    private lateinit var systemNotificationManager: NotificationManager
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository()

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
    fun `initChannels removes legacy categories and has the service channel ready at once`() =
        runWithRenderScope { renderScope ->
            NotificationChannels.LEGACY_CATEGORY_IDS.forEach(::createChannel)
            val notifications = createManager(renderScope)
            notifications.initChannels()

            NotificationChannels.LEGACY_CATEGORY_IDS.forEach { legacyId ->
                assertNull(systemNotificationManager.getNotificationChannel(legacyId))
            }
            // The foreground-service notification is posted right after initChannels returns, and the platform (not
            // Robolectric) rejects a channel whose group does not exist yet.
            val service = assertNotNull(systemNotificationManager.getNotificationChannel(NotificationChannels.SERVICE))
            assertEquals(NotificationChannelGroupSpec.Device.id, service.group)
            assertNotNull(systemNotificationManager.getNotificationChannelGroup(NotificationChannelGroupSpec.Device.id))
        }

    /**
     * Pins every channel's importance and group. Importance is fixed when a channel is first created, so changing one
     * here only reaches new installs, and lowering one below what shipped also lowers it on unmodified existing
     * installs: an edit to this table is a product decision, not a refactor.
     */
    @Test
    fun `every channel is created with its importance, group and description`() = runWithRenderScope { renderScope ->
        // Labels load on real threads; under virtual time the label timeout would fire at once and fall back.
        withContext(Dispatchers.Default) { createManager(renderScope).ensureChannels() }

        NotificationChannelSpec.entries.forEach { spec ->
            val (importance, group) =
                when (spec) {
                    NotificationChannelSpec.Service ->
                        NotificationManager.IMPORTANCE_LOW to NotificationChannelGroupSpec.Device

                    NotificationChannelSpec.DirectMessages ->
                        NotificationManager.IMPORTANCE_HIGH to NotificationChannelGroupSpec.Messages

                    NotificationChannelSpec.Broadcasts ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Messages

                    NotificationChannelSpec.Waypoints ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Messages

                    NotificationChannelSpec.Alerts ->
                        NotificationManager.IMPORTANCE_HIGH to NotificationChannelGroupSpec.Messages

                    NotificationChannelSpec.NewNodes ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Mesh

                    NotificationChannelSpec.MeshBeacon ->
                        NotificationManager.IMPORTANCE_LOW to NotificationChannelGroupSpec.Mesh

                    NotificationChannelSpec.LowBatteryRemote ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Mesh

                    NotificationChannelSpec.LowBattery ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Device

                    NotificationChannelSpec.Client ->
                        NotificationManager.IMPORTANCE_HIGH to NotificationChannelGroupSpec.Device

                    NotificationChannelSpec.DeviceStatus ->
                        NotificationManager.IMPORTANCE_DEFAULT to NotificationChannelGroupSpec.Device
                }
            val channel = assertNotNull(systemNotificationManager.getNotificationChannel(spec.id), spec.name)
            assertEquals(importance, channel.importance, spec.name)
            assertEquals(group.id, channel.group, spec.name)
            assertEquals(getString(spec.nameRes), channel.name.toString(), spec.name)
            assertEquals(getString(spec.descriptionRes), channel.description, spec.name)
        }
        NotificationChannelGroupSpec.entries.forEach { group ->
            assertNotNull(systemNotificationManager.getNotificationChannelGroup(group.id), group.name)
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
        runUntilSettled { activeServiceNotification() != null }
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

    @Test
    fun `identical service state can repost after notifications are cleared`() = runWithRenderScope { renderScope ->
        val notifications = createManager(renderScope)
        notifications.initChannels()
        val telemetry = populatedTelemetry()

        notifications.updateServiceStateNotification(ConnectionState.Disconnected, telemetry)
        advanceUntilIdle()
        // Align the replayed snapshot with the now-cached rendered message; the post-clear update is then identical.
        notifications.updateServiceStateNotification(ConnectionState.Disconnected, telemetry)
        advanceUntilIdle()

        notifications.clearNotifications()
        assertNull(activeServiceNotification())

        notifications.updateServiceStateNotification(ConnectionState.Disconnected, telemetry)
        advanceUntilIdle()
        assertNotNull(activeServiceNotification())
    }

    @Test
    fun `a running flash makes the service notification a promotable progress notification`() =
        runWithRenderScope { renderScope ->
            val notifications = createManager(renderScope)
            notifications.initChannels()
            notifications.updateServiceStateNotification(ConnectionState.Disconnected, populatedTelemetry())
            runUntilSettled { activeServiceNotification() != null }

            firmwareUpdateStatusRepository.publishProgress(
                FirmwareUpdateProgress(UiText.DynamicString("Writing firmware"), percent = 42),
            )
            runUntilSettled { serviceExtras()?.getInt(Notification.EXTRA_PROGRESS) == 42 }

            val posted = assertNotNull(activeServiceNotification()).notification
            assertEquals(getString(Res.string.firmware_update_in_progress), serviceTitle())
            assertEquals("Writing firmware", serviceExtras()?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            assertTrue(NotificationCompat.isRequestPromotedOngoing(posted))
            assertEquals("42%", posted.extras.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))

            firmwareUpdateStatusRepository.publishProgress(null)
            runUntilSettled {
                activeServiceNotification()?.notification?.let { !NotificationCompat.isRequestPromotedOngoing(it) } ==
                    true
            }
            assertEquals(getString(Res.string.disconnected), serviceTitle())
        }

    @Test
    fun `service state seeds local stats before the local node row is available`() = runWithRenderScope { renderScope ->
        val stats =
            LocalStats.Builder()
                .also { wb ->
                    wb.uptime_seconds = 1
                    wb.num_online_nodes = 2
                    wb.num_total_nodes = 3
                }
                .build()
        every { nodeRepository.localStats } returns MutableStateFlow(stats)
        val notifications = createManager(renderScope)
        notifications.initChannels()

        notifications.updateServiceStateNotification(ConnectionState.Disconnected, telemetry = null)
        runUntilSettled { activeServiceNotification() != null }

        val text = activeServiceNotification()?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)
        assertNotNull(text)
        assertTrue(text.contains(getString(Res.string.local_stats_nodes, 2, 3)))
    }

    private fun createManager(scope: CoroutineScope) = MeshNotificationManagerImpl(
        context = context,
        packetRepository = lazy<PacketRepository> { error("Not used in this test") },
        nodeRepository = lazy { nodeRepository },
        conversationShortcutPublisher = lazy { error("Not used in this test") },
        radioConfigRepository = lazy { error("Not used in this test") },
        radioOperationLock = RadioOperationLock(),
        firmwareUpdateStatusRepository = firmwareUpdateStatusRepository,
        scope = scope.asServiceScope(),
    )

    private fun populatedTelemetry() = Telemetry.Builder()
        .also { wb ->
            wb.local_stats =
                LocalStats.Builder()
                    .also { wb ->
                        wb.uptime_seconds = 1
                        wb.num_online_nodes = 1
                        wb.num_total_nodes = 1
                    }
                    .build()
        }
        .build()

    private fun activeServiceNotification() =
        systemNotificationManager.activeNotifications.singleOrNull { it.id == SERVICE_NOTIFY_ID }

    private fun serviceExtras() = activeServiceNotification()?.notification?.extras

    private fun serviceTitle() = serviceExtras()?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    private fun createChannel(id: String) {
        systemNotificationManager.createNotificationChannel(
            NotificationChannel(id, id, NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun clearManagedChannels() {
        val channelIds = NotificationChannels.LEGACY_CATEGORY_IDS + NotificationChannelSpec.entries.map { it.id }
        channelIds.forEach { channelId -> systemNotificationManager.deleteNotificationChannel(channelId) }
    }
}
