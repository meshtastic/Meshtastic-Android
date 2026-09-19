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
@file:Suppress("MagicNumber")

package org.meshtastic.screenshot.marketing

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.Channel
import org.meshtastic.core.model.Message
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.channels
import org.meshtastic.core.resources.conversations
import org.meshtastic.core.resources.direct_messages
import org.meshtastic.core.resources.map
import org.meshtastic.core.resources.send
import org.meshtastic.core.resources.type_a_message
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Send
import org.meshtastic.feature.messaging.component.MessageItem
import org.meshtastic.feature.messaging.component.MessageTopBar
import org.meshtastic.feature.messaging.ui.contact.ContactItem

/**
 * The messages tab: the LongTurbo thread, and beside it on an expanded window the conversation list the app shows in
 * the list pane. A compact window shows the thread alone, as the app does once a conversation is open.
 */
@Composable
internal fun MessagesScreen(mesh: SampleMesh) {
    MarketingTheme {
        AppShell(TopLevelDestination.Messages) {
            ListDetail(compactPane = Pane.Detail, list = { ConversationsPane(mesh) }, detail = { ThreadPane(mesh) })
        }
    }
}

/** The conversation list from the messaging feature's own [ContactItem] rows under its own app bar. */
@Composable
private fun ConversationsPane(mesh: SampleMesh) {
    val (channels, direct) = mesh.contacts.partition { it.contactKey.endsWith("^all") }
    val openThread = mesh.contacts.first().contactKey
    Scaffold(
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.conversations),
                ourNode = mesh.baseCamp,
                showNodeChip = true,
                canNavigateUp = false,
                onNavigateUp = {},
                onClickChip = {},
                actions = {},
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader(stringResource(Res.string.channels), channels.size) }
            items(channels, key = { it.contactKey }) { contact ->
                ContactItem(
                    contact = contact.copy(lastMessageText = previewLine(mesh, contact.contactKey)),
                    selected = false,
                    isActive = contact.contactKey == openThread,
                    channels = mesh.channelSet,
                )
            }
            item { SectionHeader(stringResource(Res.string.direct_messages), direct.size) }
            items(direct, key = { it.contactKey }) { contact ->
                ContactItem(
                    contact = contact.copy(lastMessageText = previewLine(mesh, contact.contactKey)),
                    selected = false,
                    channels = mesh.channelSet,
                )
            }
        }
    }
}

/** The app prefixes a received preview with the sender's short name and leaves our own bare. */
@Composable
private fun previewLine(mesh: SampleMesh, contactKey: String): String {
    val (sender, line) = mesh.contactPreview.getValue(contactKey)
    val text = stringResource(line)
    return if (sender == null) text else "${sender.user.short_name}: $text"
}

/** The LongTurbo channel thread, built from the messaging feature's own bubbles, reactions and top bar. */
@Composable
private fun ThreadPane(mesh: SampleMesh) {
    val channelName = Channel(mesh.channelSet.settings.first(), mesh.channelSet.lora_config!!).name
    val messages = mesh.messages.map { it.copy(text = stringResource(mesh.messageText.getValue(it.uuid))) }
    Scaffold(
        topBar = {
            MessageTopBar(
                title = channelName,
                channelIndex = 0,
                mismatchKey = false,
                onNavigateBack = {},
                channels = mesh.channelSet,
                channelIndexParam = 0,
                showQuickChat = false,
                onToggleQuickChat = {},
            )
        },
        bottomBar = { Composer() },
    ) { padding ->
        // The app's list is reversed and anchored at the newest message, so a short window shows the end of the
        // thread; the grouping rule still reads in thread order.
        val newestFirst = messages.asReversed()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            reverseLayout = true,
        ) {
            itemsIndexed(newestFirst, key = { _, message -> message.uuid }) { reversedIndex, message ->
                val index = messages.lastIndex - reversedIndex
                val prevSame = index > 0 && sameGroup(messages[index - 1], message)
                val nextSame = index < messages.lastIndex && sameGroup(message, messages[index + 1])
                MessageItem(
                    message = message,
                    node = message.node,
                    ourNode = mesh.baseCamp,
                    selected = false,
                    showUserName = !prevSame,
                    hasSamePrev = prevSame,
                    hasSameNext = nextSame,
                    emojis = mesh.reactions[message.uuid].orEmpty(),
                )
            }
        }
    }
}

/** The list's own grouping rule: one sender, one direction, within ten minutes. The feature's copy is internal. */
private fun sameGroup(older: Message, newer: Message): Boolean = older.fromLocal == newer.fromLocal &&
    older.node.num == newer.node.num &&
    newer.receivedTime - older.receivedTime < 10 * 60_000L

/**
 * The composer row. The feature's `MessageInput` is internal to `feature:messaging`, so this is the same shape drawn
 * from Material 3 primitives: an outlined field with the real placeholder string and the real send icon.
 */
@Composable
private fun Composer() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = "",
            onValueChange = {},
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(Res.string.type_a_message)) },
            shape = MaterialTheme.shapes.extraLarge,
            singleLine = true,
        )
        IconButton(onClick = {}) {
            Icon(
                imageVector = MeshtasticIcons.Send,
                contentDescription = stringResource(Res.string.send),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
