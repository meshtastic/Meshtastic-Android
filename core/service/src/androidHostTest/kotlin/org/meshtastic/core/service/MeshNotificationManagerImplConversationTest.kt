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
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.User
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.meshtastic.core.model.Channel as MeshChannel

/**
 * Behavioral coverage for the conversation-notification pipeline: the historic-vs-new MessagingStyle split, per-type
 * (tag, id) namespacing, group-summary alerting/cleanup, and the post-reply refresh flow.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MeshNotificationManagerImplConversationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val systemNotificationManager = context.getSystemService(NotificationManager::class.java)!!

    private val sender = Node(num = 7, user = User(id = "!00000007", long_name = "Hawk Ridge", short_name = "HAWK"))
    private val me = Node(num = 42, user = User(id = "!0000002a", long_name = "Me Node", short_name = "ME"))

    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val nodeRepository: NodeRepository = mock(MockMode.autofill)
    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)

    private fun createManager(scope: CoroutineScope) = MeshNotificationManagerImpl(
        context = context,
        packetRepository = lazy { packetRepository },
        nodeRepository = lazy { nodeRepository },
        conversationShortcutPublisher =
        lazy {
            ConversationShortcutPublisher(
                context,
                nodeRepository,
                packetRepository,
                radioConfigRepository,
                CoroutineDispatchers(
                    io = Dispatchers.Unconfined,
                    main = Dispatchers.Unconfined,
                    default = Dispatchers.Unconfined,
                ),
            )
        },
        radioConfigRepository = lazy { radioConfigRepository },
        scope = scope,
    )

    @Before
    fun setUp() {
        registerStubMainActivity()
        systemNotificationManager.cancelAll()
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(me)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeInfo?>(null)
        every { nodeRepository.localStats } returns MutableStateFlow(org.meshtastic.proto.LocalStats())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(7 to sender, 42 to me))
        everySuspend { nodeRepository.getNode(any()) } returns sender
        every { radioConfigRepository.channelSetFlow } returns
            flowOf(
                ChannelSet(
                    settings = listOf(MeshChannel.default.settings),
                    lora_config = MeshChannel.default.loraConfig,
                ),
            )
    }

    /** Newest-first message history, mirroring the repository's ordering. */
    private fun mockHistory(vararg messages: Message) {
        everySuspend { packetRepository.getMessagesFrom(any(), any(), any(), any()) } returns flowOf(messages.toList())
    }

    private fun message(text: String, read: Boolean, receivedTime: Long): Message = Message(
        uuid = receivedTime,
        receivedTime = receivedTime,
        node = sender,
        text = text,
        fromLocal = false,
        time = "",
        read = read,
        status = null,
        routingError = 0,
        packetId = receivedTime.toInt(),
        emojis = emptyList(),
        snr = 0f,
        rssi = 0,
        hopsAway = 0,
        replyId = null,
    )

    private fun activeByTag(tag: String) = systemNotificationManager.activeNotifications.filter { it.tag == tag }

    /**
     * Registers a stub `org.meshtastic.app.MainActivity` with the Robolectric `PackageManager` so that
     * `TaskStackBuilder.addNextIntentWithParentStack` can resolve the deep-link host activity, which lives in
     * `:androidApp` and is intentionally not on this module's test classpath.
     */
    private fun registerStubMainActivity() {
        val componentName = android.content.ComponentName(context, "org.meshtastic.app.MainActivity")
        val activityInfo =
            android.content.pm.ActivityInfo().apply {
                name = componentName.className
                packageName = componentName.packageName
                exported = true
            }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
    }

    @Test
    fun `read messages become historic context and unread messages stay alerting`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(
            message("new unread", read = false, receivedTime = 3_000),
            message("older read 2", read = true, receivedTime = 2_000),
            message("older read 1", read = true, receivedTime = 1_000),
        )

        manager.updateMessageNotification(
            "0^all",
            "Hawk Ridge",
            "new unread",
            isBroadcast = true,
            channelName = "LongFast",
        )
        advanceUntilIdle()

        val posted = activeByTag("message").single().notification
        val alerting = posted.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val historic = posted.extras.getParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES)
        assertEquals(1, alerting?.size, "only the unread message should be presented as new content")
        assertEquals(2, historic?.size, "read context should be carried as historic messages")
        assertEquals("new unread", posted.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
    }

    @Test
    fun `group summary alerts via children only and is dropped with the last conversation`() =
        runWithRenderScope { scope ->
            val manager = createManager(scope).also { it.initChannels() }
            mockHistory(message("hello", read = false, receivedTime = 1_000))

            manager.updateMessageNotification(
                "0^all",
                "Hawk Ridge",
                "hello",
                isBroadcast = true,
                channelName = "LongFast",
            )
            advanceUntilIdle()

            val summary = activeByTag("message_summary").single().notification
            assertEquals(Notification.GROUP_ALERT_CHILDREN, summary.groupAlertBehavior)
            // The summary line is rebuilt from the child's real MessagingStyle, so it carries the actual sender.
            val summaryLatest =
                androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(summary)
                    ?.messages
                    ?.lastOrNull()
            assertEquals("hello", summaryLatest?.text?.toString())
            assertEquals("Hawk Ridge", summaryLatest?.person?.name?.toString())

            manager.cancelMessageNotification("0^all")

            assertTrue(activeByTag("message").isEmpty(), "conversation should be cancelled")
            assertTrue(activeByTag("message_summary").isEmpty(), "summary must not outlive the last conversation")
        }

    @Test
    fun `notification ids are namespaced per type so a node num cannot clobber the service notification`() =
        runWithRenderScope { scope ->
            val manager = createManager(scope).also { it.initChannels() }
            // SERVICE_NOTIFY_ID is 101; a node whose num is also 101 used to overwrite the foreground notification.
            manager.updateServiceStateNotification(ConnectionState.Connected, telemetry = null)
            manager.showOrUpdateLowBatteryNotification(Node(num = 101), isRemote = false)
            advanceUntilIdle()

            val service = systemNotificationManager.activeNotifications.filter { it.id == 101 && it.tag == null }
            val battery = activeByTag("low_battery").filter { it.id == 101 }
            assertEquals(1, service.size, "service notification should survive")
            assertEquals(1, battery.size, "low-battery notification should coexist under its own tag")
            assertEquals(0xFF67EA94.toInt(), service.single().notification.color, "brand accent color")
        }

    @Test
    fun `refreshConversationAfterReply reposts the conversation with the effective channel name`() =
        runWithRenderScope { scope ->
            val manager = createManager(scope).also { it.initChannels() }
            mockHistory(message("original", read = true, receivedTime = 1_000))

            manager.refreshConversationAfterReply("0^all")
            advanceUntilIdle()

            val posted = activeByTag("message").single().notification
            // Empty primary channel resolves to its modem-preset display name, matching the in-app conversation list.
            assertEquals("LongFast", posted.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString())
            assertNotNull(posted.extras.getParcelableArray(Notification.EXTRA_MESSAGES))
        }

    @Test
    fun `refreshConversationAfterReply resolves a dm peer name for the shortcut label`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("dm text", read = true, receivedTime = 1_000))

        manager.refreshConversationAfterReply("0!00000007")
        advanceUntilIdle()

        val posted = activeByTag("message").single().notification
        // A DM is not a group conversation, so no conversation title is set.
        assertNull(posted.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))
        // The on-demand shortcut published for the notification carries the peer's resolved name.
        val shortcut =
            context.getSystemService(android.content.pm.ShortcutManager::class.java)!!.dynamicShortcuts.single {
                it.id == "0!00000007"
            }
        assertEquals("Hawk Ridge", shortcut.shortLabel)
    }
}
