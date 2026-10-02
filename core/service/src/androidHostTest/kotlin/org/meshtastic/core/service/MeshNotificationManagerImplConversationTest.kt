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
import android.app.PendingIntent
import android.content.Context
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.os.BundleCompat
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
import org.meshtastic.core.common.di.asServiceScope
import org.meshtastic.core.common.state.RadioOperationLock
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MyNodeInfo
import org.meshtastic.core.model.Node
import org.meshtastic.core.repository.FirmwareUpdateStatusRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.testing.runUntilSettled
import org.meshtastic.core.testing.runWithRenderScope
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.User
import org.robolectric.Shadows.shadowOf
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

    private val sender =
        Node(
            num = 7,
            user =
            User.Builder()
                .also { wb ->
                    wb.id = "!00000007"
                    wb.long_name = "Hawk Ridge"
                    wb.short_name = "HAWK"
                }
                .build(),
        )
    private val me =
        Node(
            num = 42,
            user =
            User.Builder()
                .also { wb ->
                    wb.id = "!0000002a"
                    wb.long_name = "Me Node"
                    wb.short_name = "ME"
                }
                .build(),
        )

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
        radioOperationLock = RadioOperationLock(),
        firmwareUpdateStatusRepository = FirmwareUpdateStatusRepository(),
        scope = scope.asServiceScope(),
    )

    @Before
    fun setUp() {
        registerStubMainActivity()
        systemNotificationManager.cancelAll()
        every { nodeRepository.ourNodeInfo } returns MutableStateFlow(me)
        every { nodeRepository.myNodeInfo } returns MutableStateFlow<MyNodeInfo?>(null)
        every { nodeRepository.localStats } returns MutableStateFlow(org.meshtastic.proto.LocalStats.Builder().build())
        every { nodeRepository.nodeDBbyNum } returns MutableStateFlow(mapOf(7 to sender, 42 to me))
        everySuspend { nodeRepository.getNode(any()) } returns sender
        every { radioConfigRepository.channelSetFlow } returns
            flowOf(
                ChannelSet.Builder()
                    .also { wb ->
                        wb.settings = listOf(MeshChannel.default.settings)
                        wb.lora_config = MeshChannel.default.loraConfig
                    }
                    .build(),
            )
    }

    /** Newest-first message history, mirroring the repository's ordering. */
    private fun mockHistory(vararg messages: Message) {
        every { packetRepository.getMessagesFrom(any(), any(), any(), any()) } returns flowOf(messages.toList())
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
        registerStubActivity("org.meshtastic.app.MainActivity")
        // The conversation notification also builds bubble metadata pointing at the bubble host.
        registerStubActivity("org.meshtastic.app.BubbleActivity")
    }

    private fun registerStubActivity(className: String) {
        val componentName = android.content.ComponentName(context, className)
        val activityInfo =
            android.content.pm.ActivityInfo().apply {
                name = componentName.className
                packageName = componentName.packageName
                exported = true
            }
        org.robolectric.Shadows.shadowOf(context.packageManager).addOrUpdateActivity(activityInfo)
    }

    /**
     * Bubble eligibility is a checklist the platform silently fails: it needs a long-lived conversation shortcut, a
     * Person on the notification itself, and an activity target that is not launched with a NEW_TASK-style flag —
     * `FLAG_ACTIVITY_NEW_DOCUMENT` included, which launches outside the bubble and collapses it.
     */
    @Test
    fun `conversation notifications carry bubble metadata the platform will accept`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("hello", read = false, receivedTime = 1_000))

        manager.updateMessageNotification("0^all", "Hawk Ridge", "hello", isBroadcast = true, channelName = "LongFast")
        advanceUntilIdle()

        val posted = activeByTag("message").single().notification
        val bubble = posted.bubbleMetadata
        assertNotNull(bubble, "conversation notifications must offer a bubble")
        assertNotNull(bubble.icon, "a bubble without an icon is rejected")
        assertEquals(
            android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP,
            bubble.icon?.type,
            "Android 10 rejects a plain bitmap bubble icon",
        )
        assertEquals("0^all", posted.shortcutId, "the bubble needs its long-lived conversation shortcut")
        assertTrue(
            posted.extras.containsKey(Notification.EXTRA_PEOPLE_LIST),
            "eligibility is judged on the notification's own person list, not MessagingStyle's",
        )

        val shadowIntent = org.robolectric.Shadows.shadowOf(bubble.intent)
        val savedIntent = shadowIntent.savedIntent
        assertEquals(
            "org.meshtastic.app.BubbleActivity",
            savedIntent.component?.className,
            "bubbles must target the embeddable host, not the launcher activity",
        )
        assertEquals(
            0,
            savedIntent.flags and
                (android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT),
            "a NEW_TASK-style flag launches outside the bubble and collapses it",
        )
        // The other half of that rule: the platform can only add those flags itself if the PendingIntent is mutable.
        assertTrue(
            shadowIntent.flags and android.app.PendingIntent.FLAG_IMMUTABLE == 0,
            "an immutable PendingIntent stops the platform applying the bubble's own launch flags",
        )
        assertTrue(
            shadowIntent.flags and android.app.PendingIntent.FLAG_MUTABLE != 0,
            "bubble PendingIntents are the documented exception to preferring FLAG_IMMUTABLE",
        )
    }

    @Test
    fun `conversation actions work from a watch without opening the phone`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("hello", read = false, receivedTime = 1_000))

        manager.updateMessageNotification("0^all", "Hawk Ridge", "hello", isBroadcast = true, channelName = "LongFast")
        advanceUntilIdle()

        val actions = activeByTag("message").single().notification.actions.orEmpty()
        val reply = actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
        assertTrue(reply.allowGeneratedReplies, "Smart Reply suggestions on a watch need generated replies allowed")
        val thumbsUp = actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_THUMBS_UP }
        assertEquals(false, thumbsUp.extras.getBoolean(SHOWS_USER_INTERFACE, true))
        assertTrue(actions.any { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ })
    }

    /**
     * The contract Android Auto checks before it shows a conversation in the car: a MessagingStyle naming the device
     * user, a reply action with exactly one RemoteInput whose mutable PendingIntent reaches a Service without opening
     * UI, and a mark-as-read action that opens no UI either.
     */
    @Test
    fun `a direct message meets Android Auto's messaging contract`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("hello", read = false, receivedTime = 1_000))

        manager.updateMessageNotification("0!abcd1234", "Hawk Ridge", "hello", isBroadcast = false, channelName = null)
        advanceUntilIdle()

        val posted = activeByTag("message").single().notification
        assertEquals(Notification.CATEGORY_MESSAGE, posted.category)
        val style = assertNotNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(posted))
        assertNotNull(style.user.name, "the device user is named, and read aloud in the car")
        assertNull(style.conversationTitle, "a one-to-one chat has no title; a title marks a group")
        assertEquals(false, style.isGroupConversation)
        assertEquals("Hawk Ridge", style.messages.single().person?.name?.toString())

        val actions = posted.actions.orEmpty()
        val reply = actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
        val markAsRead = actions.single { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }
        listOf(reply, markAsRead).forEach { action ->
            assertEquals(false, action.extras.getBoolean(SHOWS_USER_INTERFACE, true))
            val pendingIntent = shadowOf(action.actionIntent)
            assertTrue(pendingIntent.isService, "Android Auto has these actions handled by a Service")
            assertEquals(ConversationActionService::class.java.name, pendingIntent.savedIntent.component?.className)
        }
        assertEquals(1, reply.remoteInputs?.size)
        assertTrue(shadowOf(reply.actionIntent).flags and PendingIntent.FLAG_MUTABLE != 0, "Auto fills the reply in")
        assertTrue(shadowOf(markAsRead.actionIntent).flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertTrue(reply.actionIntent != markAsRead.actionIntent)

        // Fire the reply the way the car does: the RemoteInput text filled into the mutable PendingIntent.
        val fillIn = android.content.Intent()
        val results =
            android.os.Bundle().apply { putCharSequence(reply.remoteInputs!!.single().resultKey, "on my way") }
        android.app.RemoteInput.addResultsToIntent(reply.remoteInputs, fillIn, results)
        reply.actionIntent.send(context, 0, fillIn)
        val started = assertNotNull(shadowOf(context as android.app.Application).nextStartedService)
        assertEquals(ConversationActionService.ACTION_REPLY, started.action)
        assertEquals("0!abcd1234", started.getStringExtra(ConversationActionService.EXTRA_CONTACT_KEY))
        assertEquals(
            "on my way",
            RemoteInput.getResultsFromIntent(started)
                ?.getCharSequence(ConversationActionService.KEY_TEXT_REPLY)
                ?.toString(),
        )
    }

    @Test
    fun `a channel message is a titled group conversation`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("hello", read = false, receivedTime = 1_000))

        manager.updateMessageNotification("0^all", "Hawk Ridge", "hello", isBroadcast = true, channelName = "LongFast")
        advanceUntilIdle()

        val style =
            assertNotNull(
                NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(
                    activeByTag("message").single().notification,
                ),
            )
        assertEquals("LongFast", style.conversationTitle?.toString())
        assertEquals(true, style.isGroupConversation)
    }

    @Test
    @Config(sdk = [29])
    fun `conversation notifications post on Android 10`() = runWithRenderScope { scope ->
        val manager = createManager(scope).also { it.initChannels() }
        mockHistory(message("hello", read = false, receivedTime = 1_000))

        manager.updateMessageNotification("0^all", "Hawk Ridge", "hello", isBroadcast = true, channelName = "LongFast")
        advanceUntilIdle()

        assertNotNull(activeByTag("message").single().notification.bubbleMetadata)
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
        val alerting =
            BundleCompat.getParcelableArray(posted.extras, Notification.EXTRA_MESSAGES, Parcelable::class.java)
        val historic =
            BundleCompat.getParcelableArray(posted.extras, Notification.EXTRA_HISTORIC_MESSAGES, Parcelable::class.java)
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
            // The summary line is rebuilt from the child's real MessagingStyle, so it carries the actual sender, but
            // the
            // summary itself is not a conversation Android Auto could try to answer.
            assertNull(NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(summary))
            val lines = summary.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }
            assertEquals(listOf("Hawk Ridge: hello"), lines)

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
            manager.showLowBatteryNotification(Node(num = 101), isRemote = false)
            runUntilSettled {
                systemNotificationManager.activeNotifications.any { it.id == 101 && it.tag == null } &&
                    activeByTag("low_battery").any { it.id == 101 }
            }

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
            assertNotNull(
                BundleCompat.getParcelableArray(posted.extras, Notification.EXTRA_MESSAGES, Parcelable::class.java),
            )
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

/** NotificationCompat stores an action's showsUserInterface flag in its extras under this key. */
private const val SHOWS_USER_INTERFACE = "android.support.action.showsUserInterface"
