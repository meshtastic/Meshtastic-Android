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

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.graphics.Color
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.nodeColorsFromNum
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.ChannelSet

/**
 * Single owner of the dynamic conversation shortcuts that link Meshtastic conversations to their notifications.
 *
 * Publishing lives here in `core:service` (rather than in a feature module) so that shortcuts exist whenever the mesh
 * service is running — not only during an Android Auto session. This is what lets [MeshNotificationManagerImpl] attach
 * `setShortcutId`/`setLocusId` to message notifications so Android ranks them in the shade's Conversations section and
 * surfaces them to Android Auto/Wear.
 *
 * Two publishing paths, both keyed by `contactKey`:
 * - [startObserving] keeps a full set of DM + channel shortcuts in sync with the database for the service lifetime.
 * - [ensureConversationShortcut] publishes a single shortcut on demand when a notification is about to reference one
 *   that the observer has not emitted yet (e.g. the very first message of a brand-new conversation).
 */
@Single
class ConversationShortcutPublisher(
    private val context: Context,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioConfigRepository: RadioConfigRepository,
) {

    private var observeJob: Job? = null

    fun startObserving(scope: CoroutineScope) {
        observeJob?.cancel()
        observeJob =
            scope.launch {
                // Combine the message DB (recency + DM peers) with the channel config (names). Ranking both by
                // last-activity time lets launchers / Android Auto surface the *recently active* conversations first
                // (the shortcut display cap is small), instead of an arbitrary slice of stale channels.
                combine(packetRepository.getContacts(), radioConfigRepository.channelSetFlow) { contacts, channelSet ->
                    rankConversationsByRecency(contacts, channelSet)
                }
                    .distinctUntilChanged()
                    .collect { conversations -> publishShortcuts(conversations) }
            }
    }

    private fun rankConversationsByRecency(
        contacts: Map<String, DataPacket>,
        channelSet: ChannelSet,
    ): List<Conversation> {
        val lora = channelSet.lora_config ?: Channel.default.loraConfig
        val dms =
            contacts.entries
                // DM contacts are those whose key does NOT contain the broadcast ID.
                .filter { (key, _) -> !key.contains(NodeAddress.ID_BROADCAST) }
                .map { (key, packet) -> Conversation.Dm(key, packet.from.orEmpty(), packet.time) }
        val channels =
            channelSet.settings.mapIndexedNotNull { index, settings ->
                if (index == 0 || settings.name.isNotEmpty()) {
                    val contactKey = "$index${NodeAddress.ID_BROADCAST}"
                    // Effective name: an empty primary shows its modem-preset name ("LongFast", …). Recency comes from
                    // the channel's broadcast contact entry, if any.
                    Conversation.Channel(
                        contactKey,
                        index,
                        Channel(settings, lora).name,
                        contacts[contactKey]?.time ?: 0L,
                    )
                } else {
                    null
                }
            }
        // Most recently active first; conversations with no messages (time 0) sort last.
        return (dms + channels).sortedByDescending { it.time }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun publishShortcuts(conversations: List<Conversation>) {
        val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
        // rank == list position, so the most recent conversation is rank 0 and shown first.
        val shortcuts =
            conversations.take(limit).mapIndexedNotNull { rank, conversation ->
                when (conversation) {
                    is Conversation.Dm -> buildDmShortcut(conversation, rank)
                    is Conversation.Channel -> buildChannelShortcut(conversation, rank)
                }
            }

        try {
            val currentKeys = shortcuts.map { it.id }.toSet()
            val stale = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.filter { it !in currentKeys }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            }
            for (shortcut in shortcuts) {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
            Logger.d(tag = TAG) { "Published ${shortcuts.size} conversation shortcuts (recency-ranked)" }
        } catch (e: IllegalArgumentException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish conversation shortcuts" }
        } catch (e: IllegalStateException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish conversation shortcuts" }
        }
    }

    private fun buildDmShortcut(dm: Conversation.Dm, rank: Int): ShortcutInfoCompat? {
        val node = nodeRepository.nodeDBbyNum.value.values.find { it.user.id == dm.userId }
        // shortLabel is the compact 4-char short name; longLabel the full node name. Fall back sensibly when a name is
        // missing so the shortcut is never blank.
        val shortName = node?.user?.short_name?.takeIf { it.isNotBlank() }
        val longName = node?.user?.long_name?.takeIf { it.isNotBlank() }
        val shortLabel = shortName ?: longName ?: dm.contactKey
        val longLabel = longName ?: shortName ?: dm.contactKey

        // A node-colored pill avatar showing the short name identifies the person and matches the in-app node chip.
        // Set it on the shortcut itself (not just the Person) so launchers/Android Auto render it instead of a generic
        // head silhouette.
        val icon =
            node?.let {
                val (foregroundColor, backgroundColor) = nodeColorsFromNum(it.num)
                PersonIconFactory.createLabel(shortLabel, backgroundColor, foregroundColor, rounded = false)
            }
        val person =
            Person.Builder()
                .setName(longLabel)
                .setKey(dm.contactKey)
                // Favorite nodes feed the system's conversation-priority ranking.
                .setImportant(node?.isFavorite == true)
                .apply { icon?.let { setIcon(it) } }
                .build()

        return ShortcutInfoCompat.Builder(context, dm.contactKey)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setRank(rank)
            .setLocusId(LocusIdCompat(dm.contactKey))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(conversationIntent(dm.contactKey))
            .apply { icon?.let { setIcon(it) } }
            .build()
    }

    private fun buildChannelShortcut(channel: Conversation.Channel, rank: Int): ShortcutInfoCompat {
        // channel.name is already the effective name (preset-derived for an empty primary), so no placeholder here.
        val channelName = channel.name.ifEmpty { "Channel ${channel.index}" }
        // A black rounded-square badge with the channel number visually separates channels from the node-colored DM
        // pills.
        val icon = channelIcon(channel.index)
        val person = Person.Builder().setName(channelName).setKey("channel-${channel.index}").setIcon(icon).build()
        return ShortcutInfoCompat.Builder(context, channel.contactKey)
            .setShortLabel(channelName)
            .setLongLabel(channelName)
            .setRank(rank)
            .setLocusId(LocusIdCompat(channel.contactKey))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(conversationIntent(channel.contactKey))
            .setIcon(icon)
            .build()
    }

    /** Black chip with a white channel [index] — a consistent, uncolored channel marker. */
    private fun channelIcon(index: Int): IconCompat = PersonIconFactory.createLabel(
        label = index.toString(),
        backgroundColor = Color.BLACK,
        foregroundColor = Color.WHITE,
        rounded = true,
    )

    private fun conversationIntent(contactKey: String): Intent =
        Intent(Intent.ACTION_VIEW, "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri()).apply {
            setPackage(context.packageName)
        }

    /**
     * Ensures a long-lived conversation shortcut exists for [contactKey] before a notification references it. A no-op
     * when [startObserving] has already published the shortcut; otherwise publishes a minimal one so the notification's
     * `setShortcutId`/`setLocusId` resolve immediately (the observer replaces it with a richer version on its next
     * emission).
     */
    fun ensureConversationShortcut(contactKey: String, displayName: String) {
        val alreadyPublished = ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == contactKey }
        if (alreadyPublished) return
        // Match the styling the observer will republish so there is no generic-head flash: rounded channel badge with
        // its number, or a circular initial for a DM. Color is derived from the key (the node object may not be known
        // yet at on-demand time).
        val isChannel = contactKey.contains(NodeAddress.ID_BROADCAST)
        val icon =
            if (isChannel) {
                channelIcon(contactKey.substringBefore(NodeAddress.ID_BROADCAST).toIntOrNull() ?: 0)
            } else {
                val (foregroundColor, backgroundColor) = nodeColorsFromNum(contactKey.hashCode())
                PersonIconFactory.create(displayName, backgroundColor, foregroundColor)
            }
        val person = Person.Builder().setName(displayName).setKey(contactKey).setIcon(icon).build()
        val shortcut =
            ShortcutInfoCompat.Builder(context, contactKey)
                .setShortLabel(displayName)
                .setLongLabel(displayName)
                .setLocusId(LocusIdCompat(contactKey))
                .setPerson(person)
                .setLongLived(true)
                .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                .setIntent(conversationIntent(contactKey))
                .setIcon(icon)
                .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: IllegalArgumentException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish on-demand shortcut $contactKey" }
        } catch (e: IllegalStateException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish on-demand shortcut $contactKey" }
        }
    }

    /** A publishable conversation with its last-activity [time], used to rank shortcuts by recency. */
    private sealed interface Conversation {
        val contactKey: String
        val time: Long

        data class Dm(override val contactKey: String, val userId: String, override val time: Long) : Conversation

        data class Channel(override val contactKey: String, val index: Int, val name: String, override val time: Long) :
            Conversation
    }

    companion object {
        private const val TAG = "ConversationShortcuts"
    }
}
