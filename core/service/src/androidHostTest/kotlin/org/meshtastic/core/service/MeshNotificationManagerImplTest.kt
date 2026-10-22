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
import android.content.pm.ActivityInfo
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
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
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FirmwareUpdateProgress
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.LiveUpdateMode
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.SERVICE_NOTIFY_ID
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.disconnected
import org.meshtastic.core.resources.firmware_update_in_progress
import org.meshtastic.core.resources.formatDurationSuspend
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.live_update_heard_now
import org.meshtastic.core.resources.live_update_nodes_online
import org.meshtastic.core.resources.live_update_unread
import org.meshtastic.core.resources.local_stats_nodes
import org.meshtastic.core.testing.FakeLiveUpdatePrefs
import org.meshtastic.core.testing.FakeLocaleUnitsProvider
import org.meshtastic.core.testing.FakeMeshPrefs
import org.meshtastic.core.testing.runUntilSettled
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.LocalStats
import org.meshtastic.proto.Telemetry
import org.meshtastic.proto.User
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshNotificationManagerImplTest {

    private lateinit var context: Context
    private lateinit var systemNotificationManager: NotificationManager
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository()
    private val liveUpdatePrefs = FakeLiveUpdatePrefs()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!
        systemNotificationManager.cancelAll()
        clearManagedChannels()
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeInfo?>(null)
        // A Live Update's tap target is a deep link, whose task stack the platform resolves against MainActivity.
        val mainActivity =
            ActivityInfo().apply {
                name = "org.meshtastic.app.MainActivity"
                packageName = context.packageName
                exported = true
            }
        Shadows.shadowOf(context.packageManager).addOrUpdateActivity(mainActivity)
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

                    NotificationChannelSpec.Reactions ->
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

    @Test
    fun `the service notification is not promoted while the live update is off`() = runWithRenderScope { renderScope ->
        val notifications = createManager(renderScope)
        notifications.initChannels()

        notifications.updateServiceStateNotification(ConnectionState.Connected, populatedTelemetry())
        runUntilSettled { activeServiceNotification() != null }

        val posted = assertNotNull(activeServiceNotification()).notification
        assertFalse(NotificationCompat.isRequestPromotedOngoing(posted))
        assertNull(posted.extras.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))
    }

    @Test
    fun `mesh status promotes the service notification until it is turned off`() = runWithRenderScope { renderScope ->
        stubMeshStatus(online = 3, total = 12, unread = 2)
        val notifications = createManager(renderScope)
        notifications.initChannels()
        notifications.updateServiceStateNotification(ConnectionState.Connected, populatedTelemetry())
        runUntilSettled { activeServiceNotification() != null }
        val offTitle = serviceTitle()

        liveUpdatePrefs.setMode(LiveUpdateMode.MeshStatus)
        runUntilSettled {
            activeServiceNotification()?.notification?.let(NotificationCompat::isRequestPromotedOngoing) == true
        }

        val posted = assertNotNull(activeServiceNotification()).notification
        assertEquals("3/12", posted.extras.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))
        assertEquals(getString(Res.string.live_update_nodes_online, 3, 12), serviceBigText()?.lineSequence()?.first())
        assertTrue(serviceBigText().orEmpty().contains(getString(Res.string.live_update_unread, 2)))
        assertEquals(offTitle, serviceTitle())

        liveUpdatePrefs.setMode(LiveUpdateMode.Off)
        runUntilSettled {
            activeServiceNotification()?.notification?.let(NotificationCompat::isRequestPromotedOngoing) == false
        }
        assertNull(serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))
        assertEquals(offTitle, serviceTitle())
    }

    @Test
    fun `firmware progress outranks a running live update`() = runWithRenderScope { renderScope ->
        stubMeshStatus(online = 1, total = 1, unread = 0)
        liveUpdatePrefs.setMode(LiveUpdateMode.MeshStatus)
        val notifications = createManager(renderScope)
        notifications.initChannels()
        notifications.updateServiceStateNotification(ConnectionState.Connected, populatedTelemetry())
        runUntilSettled { serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT) == "1/1" }

        firmwareUpdateStatusRepository.publishProgress(
            FirmwareUpdateProgress(UiText.DynamicString("Writing firmware"), percent = 42),
        )
        runUntilSettled { serviceExtras()?.getInt(Notification.EXTRA_PROGRESS) == 42 }

        assertEquals(getString(Res.string.firmware_update_in_progress), serviceTitle())
        assertEquals("42%", serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))

        firmwareUpdateStatusRepository.publishProgress(null)
        runUntilSettled { serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT) == "1/1" }
    }

    @Test
    fun `a tracked node is shown until it is deleted and then the live update turns off`() =
        runWithRenderScope { renderScope ->
            val tracked =
                Node(
                    num = TRACKED_NUM,
                    user =
                    User.Builder()
                        .also { wb ->
                            wb.long_name = "Base camp"
                            wb.short_name = "BASE"
                        }
                        .build(),
                    deviceMetrics = DeviceMetrics.Builder().also { wb -> wb.battery_level = 77 }.build(),
                )
            val db = MutableStateFlow(mapOf(TRACKED_NUM to tracked))
            every { nodeRepository.nodeDBbyNum } returns db
            every { nodeRepository.ourNodeInfo } returns MutableStateFlow<Node?>(null)
            val notifications = createManager(renderScope)
            notifications.initChannels()
            notifications.updateServiceStateNotification(ConnectionState.Disconnected, populatedTelemetry())
            runUntilSettled { activeServiceNotification() != null }

            liveUpdatePrefs.setMode(LiveUpdateMode.TrackNode(TRACKED_NUM))
            runUntilSettled { serviceTitle() == "Base camp" }

            val posted = assertNotNull(activeServiceNotification()).notification
            assertTrue(NotificationCompat.isRequestPromotedOngoing(posted))
            assertEquals("77%", posted.extras.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))
            // A dropped link keeps the mode and says so.
            assertEquals(
                getString(Res.string.disconnected),
                posted.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            )

            db.value = emptyMap()
            runUntilSettled { liveUpdatePrefs.mode.value == LiveUpdateMode.Off }
            runUntilSettled {
                activeServiceNotification()?.notification?.let(NotificationCompat::isRequestPromotedOngoing) == false
            }
            assertEquals(getString(Res.string.disconnected), serviceTitle())
        }

    @Test
    fun `a tracked node's heard time keeps aging while it sends nothing`() = runWithRenderScope { renderScope ->
        val heardAt = 1_700_000_000L
        var clock = heardAt + 10
        val tracked =
            Node(
                num = TRACKED_NUM,
                user = User.Builder().also { wb -> wb.long_name = "Base camp" }.build(),
                lastHeard = heardAt.toInt(),
            )
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(TRACKED_NUM to tracked))
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow<Node?>(null)
        val notifications = createManager(renderScope).apply { clockSeconds = { clock } }
        notifications.initChannels()
        notifications.updateServiceStateNotification(ConnectionState.Disconnected, populatedTelemetry())
        liveUpdatePrefs.setMode(LiveUpdateMode.TrackNode(TRACKED_NUM))
        runUntilSettled { serviceTitle() == "Base camp" }

        val heardNow = getString(Res.string.live_update_heard_now)
        assertTrue(serviceBigText().orEmpty().contains(heardNow))
        assertNull(serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT))

        clock = heardAt + 120
        advanceTimeBy(1.minutes)
        val aged = formatDurationSuspend(120, DurationUnit.MINUTES)
        runUntilSettled { serviceExtras()?.getString(NotificationCompat.EXTRA_SHORT_CRITICAL_TEXT) == aged }
        assertFalse(serviceBigText().orEmpty().contains(heardNow))
    }

    private fun stubMeshStatus(online: Int, total: Int, unread: Int) {
        every { nodeRepository.onlineNodeCount } returns flowOf(online)
        every { nodeRepository.totalNodeCount } returns flowOf(total)
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow<Node?>(null)
        every { packetRepository.getUnreadCountTotal() } returns flowOf(unread)
    }

    private fun createManager(scope: CoroutineScope) = MeshNotificationManagerImpl(
        context = context,
        packetRepository = lazy { packetRepository },
        nodeRepository = lazy { nodeRepository },
        conversationShortcutPublisher = lazy { error("Not used in this test") },
        radioConfigRepository = lazy { error("Not used in this test") },
        radioOperationLock = RadioOperationLock(),
        firmwareUpdateStatusRepository = firmwareUpdateStatusRepository,
        liveUpdatePrefs = liveUpdatePrefs,
        meshPrefs = FakeMeshPrefs(),
        localeUnitsProvider = FakeLocaleUnitsProvider(),
        scope = scope.asServiceScope(),
    )

    private fun serviceBigText() = serviceExtras()?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

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

    private companion object {
        const val TRACKED_NUM = 42
    }

    private fun clearManagedChannels() {
        val channelIds = NotificationChannels.LEGACY_CATEGORY_IDS + NotificationChannelSpec.entries.map { it.id }
        channelIds.forEach { channelId -> systemNotificationManager.deleteNotificationChannel(channelId) }
    }
}
