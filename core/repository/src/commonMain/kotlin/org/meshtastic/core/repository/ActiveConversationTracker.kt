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
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How an arriving message for a conversation should be announced, given where the user currently is. */
enum class MessageAnnouncement {
    /** The conversation is on screen — the message itself is the notification. Post nothing. */
    Suppress,

    /** App is in front but on another screen — post without sound or vibration so the shade still has it. */
    Silent,

    /** App is backgrounded or the screen is off — announce normally. */
    Alert,
}

/**
 * Tracks which conversation the user is currently looking at, so the notification path does not buzz the phone for a
 * message whose bubble is already animating onto the screen.
 *
 * Only the message screen writes [setActive]/[clearActive], on resume and pause. That is deliberately the only signal
 * [MessageAnnouncement.Suppress] needs: backgrounding the app pauses the screen, which clears the key on its way out.
 * [appForeground] is driven from the process lifecycle and only separates [MessageAnnouncement.Silent] from
 * [MessageAnnouncement.Alert].
 */
class ActiveConversationTracker {

    private val _activeContactKey = MutableStateFlow<String?>(null)

    /** Contact key of the conversation currently on screen, or null when no conversation is visible. */
    val activeContactKey: StateFlow<String?> = _activeContactKey.asStateFlow()

    private val _appForeground = MutableStateFlow(false)

    /** True while any part of the app is visible to the user. */
    val appForeground: StateFlow<Boolean> = _appForeground.asStateFlow()

    /** Call when a conversation becomes visible. */
    fun setActive(contactKey: String) {
        _activeContactKey.value = contactKey
    }

    /**
     * Call when a conversation stops being visible. Compares before clearing so that navigating straight from one
     * conversation to another cannot have the outgoing screen's pause erase the incoming screen's key.
     */
    fun clearActive(contactKey: String) {
        _activeContactKey.compareAndSet(contactKey, null)
    }

    fun setAppForeground(foreground: Boolean) {
        _appForeground.value = foreground
    }

    fun announcementFor(contactKey: String): MessageAnnouncement = when {
        appForeground.value && activeContactKey.value == contactKey -> MessageAnnouncement.Suppress
        appForeground.value -> MessageAnnouncement.Silent
        else -> MessageAnnouncement.Alert
    }
}
