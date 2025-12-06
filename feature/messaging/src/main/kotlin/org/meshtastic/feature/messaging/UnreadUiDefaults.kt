/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.messaging

/**
 * Shared configuration for how unread markers behave in the message thread.
 *
 * Keeping these in one place makes it easier to reason about how the unread divider and auto-mark-as-read flows work
 * across `Message` and `MessageList`.
 */
internal object UnreadUiDefaults {
    /**
     * The number of most-recent messages we attempt to keep visible when jumping to unread content.
     *
     * With the list reversed (newest at index 0) this translates to showing up to this many messages *above* the unread
     * divider so the user can read into the conversation with enough context.
     */
    const val VISIBLE_CONTEXT_COUNT = 5

    /**
     * Acceptable pixel offset from the absolute bottom of the list while still treating the user as "caught up".
     * Compose list positioning can drift by a few pixels during fling settles, so this tolerance keeps the auto-scroll
     * behavior feeling buttery when new packets arrive.
     */
    const val AUTO_SCROLL_BOTTOM_OFFSET_TOLERANCE = 8

    /**
     * Delay (in milliseconds) before we persist a new "last read" marker while scrolling.
     *
     * A longer debounce prevents thrashing the database during quick scrubs yet still feels responsive once the user
     * settles on a position.
     */
    const val SCROLL_DEBOUNCE_MILLIS = 3_000L
}
