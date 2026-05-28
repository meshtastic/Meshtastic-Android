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
package org.meshtastic.feature.car.service

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.nodeColorsFromNum
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.feature.car.util.PersonIconFactory

/**
 * Publishes dynamic shortcuts for favorited nodes and active channels so that Android Auto can surface Meshtastic
 * conversations as messaging destinations and link notifications to template conversations via [LocusIdCompat].
 */
@Single
class ConversationShortcutManager(
    private val context: Context,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
) {

    private var observeJob: Job? = null

    fun startObserving(scope: CoroutineScope) {
        observeJob?.cancel()
        observeJob =
            scope.launch {
                val favoritesFlow =
                    nodeRepository.nodeDBbyNum
                        .map { nodes ->
                            nodes.values.filter { it.isFavorite && !it.isIgnored }.sortedBy { it.user.long_name }
                        }
                        .distinctUntilChanged()

                val channelsFlow =
                    radioConfigRepository.channelSetFlow
                        .map { channelSet ->
                            channelSet.settings.mapIndexedNotNull { index, settings ->
                                if (index == 0 || settings.name.isNotEmpty()) {
                                    index to settings.name
                                } else {
                                    null
                                }
                            }
                        }
                        .distinctUntilChanged()

                combine(favoritesFlow, channelsFlow) { favorites, channels -> favorites to channels }
                    .collect { (favorites, channels) -> publishShortcuts(favorites, channels) }
            }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun publishShortcuts(favorites: List<Node>, channels: List<Pair<Int, String>>) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val shortcuts =
            favorites.filter { it.num != myNodeNum }.map { buildFavoriteShortcut(it) } +
                channels.map { (index, name) -> buildChannelShortcut(index, name) }

        try {
            val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val currentKeys = shortcuts.map { it.id }.toSet()
            val stale = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.filter { it !in currentKeys }
            if (stale.isNotEmpty()) {
                ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            }
            for (shortcut in shortcuts.take(limit)) {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
            Logger.d(tag = TAG) { "Published ${shortcuts.size.coerceAtMost(limit)} conversation shortcuts" }
        } catch (e: IllegalArgumentException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish conversation shortcuts" }
        } catch (e: IllegalStateException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish conversation shortcuts" }
        }
    }

    private fun buildFavoriteShortcut(node: Node): ShortcutInfoCompat {
        val contactKey = "0${node.user.id}"
        val label = node.user.long_name.ifEmpty { node.user.short_name }
        val (foregroundColor, backgroundColor) = nodeColorsFromNum(node.num)
        val person =
            Person.Builder()
                .setName(label)
                .setKey(node.user.id)
                .setIcon(PersonIconFactory.create(node.user.short_name, backgroundColor, foregroundColor))
                .build()
        return ShortcutInfoCompat.Builder(context, contactKey)
            .setShortLabel(label)
            .setLongLabel(label)
            .setLocusId(LocusIdCompat(contactKey))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(conversationIntent(contactKey))
            .build()
    }

    private fun buildChannelShortcut(index: Int, name: String): ShortcutInfoCompat {
        val contactKey = "${index}${DataPacket.ID_BROADCAST}"
        val channelName = name.ifEmpty { "Primary Channel" }
        val person = Person.Builder().setName(channelName).setKey("channel-$index").build()
        return ShortcutInfoCompat.Builder(context, contactKey)
            .setShortLabel(channelName)
            .setLongLabel(channelName)
            .setLocusId(LocusIdCompat(contactKey))
            .setPerson(person)
            .setLongLived(true)
            .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
            .setIntent(conversationIntent(contactKey))
            .build()
    }

    private fun conversationIntent(contactKey: String): Intent =
        Intent(Intent.ACTION_VIEW, "meshtastic://messages/$contactKey".toUri()).apply {
            setPackage(context.packageName)
        }

    /**
     * Ensures a long-lived conversation shortcut exists for [contactKey]. Called on demand when a notification is about
     * to reference a shortcut ID that may not have been pre-published.
     */
    fun ensureConversationShortcut(contactKey: String, displayName: String) {
        val alreadyPublished = ShortcutManagerCompat.getDynamicShortcuts(context).any { it.id == contactKey }
        if (alreadyPublished) {
            return
        }
        val person = Person.Builder().setName(displayName).setKey(contactKey).build()
        val shortcut =
            ShortcutInfoCompat.Builder(context, contactKey)
                .setShortLabel(displayName)
                .setLongLabel(displayName)
                .setLocusId(LocusIdCompat(contactKey))
                .setPerson(person)
                .setLongLived(true)
                .setCategories(setOf(ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION))
                .setIntent(conversationIntent(contactKey))
                .build()
        try {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        } catch (e: IllegalArgumentException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish on-demand shortcut $contactKey" }
        } catch (e: IllegalStateException) {
            Logger.e(tag = TAG, throwable = e) { "Failed to publish on-demand shortcut $contactKey" }
        }
    }

    companion object {
        private const val TAG = "ConversationShortcuts"
    }
}
