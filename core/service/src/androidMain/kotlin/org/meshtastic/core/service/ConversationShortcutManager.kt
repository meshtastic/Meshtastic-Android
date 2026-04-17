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
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
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
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Node
import org.meshtastic.core.navigation.DEEP_LINK_BASE_URI
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.proto.ChannelSettings

/**
 * Publishes dynamic shortcuts for favorited nodes and active channels.
 *
 * These shortcuts enable Android Auto (and the launcher) to surface Meshtastic conversations as share targets and
 * messaging destinations. Each shortcut is linked to a conversation via [LocusIdCompat] so that notifications and the
 * car messaging UI can associate them.
 */
@Single
class ConversationShortcutManager(
    private val context: Context,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val dispatchers: CoroutineDispatchers,
) {

    private var observeJob: Job? = null

    /**
     * Starts observing favorite nodes and active channels, publishing shortcuts whenever the data changes. Call from
     * [MeshService.onCreate].
     */
    fun startObserving(scope: CoroutineScope) {
        observeJob?.cancel()
        observeJob =
            scope.launch(dispatchers.io) {
                val favoritesFlow =
                    nodeRepository.nodeDBbyNum
                        .map { nodes ->
                            nodes.values.filter { it.isFavorite && !it.isIgnored }.sortedBy { it.user.long_name }
                        }
                        .distinctUntilChanged()

                val channelsFlow =
                    radioConfigRepository.channelSetFlow
                        .map { cs ->
                            cs.settings.filterIndexed { index, settings -> settings.name.isNotEmpty() || index == 0 }
                        }
                        .distinctUntilChanged()

                combine(favoritesFlow, channelsFlow) { favorites, channels -> favorites to channels }
                    .collect { (favorites, channels) -> publishShortcuts(favorites, channels) }
            }
    }

    /** Stops the observation coroutine. Call from [MeshService.onDestroy]. */
    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    private fun publishShortcuts(favorites: List<Node>, channels: List<ChannelSettings>) {
        val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum
        val shortcuts =
            favorites.filter { it.num != myNodeNum }.map { buildFavoriteShortcut(it) } +
                channels.mapIndexed { index, settings -> buildChannelShortcut(settings, index) }

        try {
            val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
            val currentKeys = shortcuts.map { it.id }.toSet()
            val stale = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }.filter { it !in currentKeys }
            if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
            for (shortcut in shortcuts.take(limit)) {
                ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
            }
            Logger.d { "Published ${shortcuts.size.coerceAtMost(limit)} conversation shortcuts" }
        } catch (e: IllegalArgumentException) {
            Logger.e(e) { "Failed to publish conversation shortcuts" }
        } catch (e: IllegalStateException) {
            Logger.e(e) { "Failed to publish conversation shortcuts" }
        }
    }

    private fun buildFavoriteShortcut(node: Node): ShortcutInfoCompat {
        val contactKey = "0${node.user.id}"
        val label = node.user.long_name.ifEmpty { node.user.short_name }
        val person =
            Person.Builder()
                .setName(node.user.long_name)
                .setKey(node.user.id)
                .setIcon(createPersonIcon(node.user.short_name, node.colors.second, node.colors.first))
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

    private fun buildChannelShortcut(channelSettings: ChannelSettings, index: Int): ShortcutInfoCompat {
        val contactKey = "${index}${DataPacket.ID_BROADCAST}"
        val channelName = channelSettings.name.ifEmpty { "Primary Channel" }
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
        Intent(Intent.ACTION_VIEW, "$DEEP_LINK_BASE_URI/messages/$contactKey".toUri()).apply {
            setPackage(context.packageName)
        }

    private fun createPersonIcon(name: String, backgroundColor: Int, foregroundColor: Int): IconCompat {
        val size = ICON_SIZE
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = backgroundColor
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.color = foregroundColor
        paint.textSize = size * TEXT_SIZE_RATIO
        paint.textAlign = Paint.Align.CENTER
        val initial =
            if (name.isNotEmpty()) {
                val codePoint = name.codePointAt(0)
                String(Character.toChars(codePoint)).uppercase()
            } else {
                "?"
            }
        val xPos = canvas.width / 2f
        val yPos = (canvas.height / 2f - (paint.descent() + paint.ascent()) / 2f)
        canvas.drawText(initial, xPos, yPos, paint)

        return IconCompat.createWithBitmap(bitmap)
    }

    companion object {
        private const val ICON_SIZE = 128
        private const val TEXT_SIZE_RATIO = 0.5f
    }
}
