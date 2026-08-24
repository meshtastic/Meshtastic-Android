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
package org.meshtastic.core.ui.util

import androidx.compose.material3.SnackbarDuration
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SnackbarManagerTest {

    private val snackbarManager = SnackbarManager()

    @Test
    fun showSnackbar_emits_event_with_message() = runTest {
        snackbarManager.events.test {
            snackbarManager.showSnackbar(message = "Hello")

            val event = awaitItem()
            assertEquals("Hello", event.message)
            assertNull(event.actionLabel)
            assertEquals(SnackbarDuration.Short, event.duration)
        }
    }

    @Test
    fun showSnackbar_with_action_defaults_to_indefinite_duration() = runTest {
        snackbarManager.events.test {
            snackbarManager.showSnackbar(message = "Deleted", actionLabel = "Undo")

            val event = awaitItem()
            assertEquals("Deleted", event.message)
            assertEquals("Undo", event.actionLabel)
            assertEquals(SnackbarDuration.Indefinite, event.duration)
        }
    }

    @Test
    fun showSnackbar_with_explicit_duration_overrides_default() = runTest {
        snackbarManager.events.test {
            snackbarManager.showSnackbar(message = "Saved", actionLabel = "View", duration = SnackbarDuration.Long)

            val event = awaitItem()
            assertEquals(SnackbarDuration.Long, event.duration)
        }
    }

    @Test
    fun multiple_events_are_queued_and_consumed_in_order() = runTest {
        snackbarManager.events.test {
            snackbarManager.showSnackbar(message = "First")
            snackbarManager.showSnackbar(message = "Second")
            snackbarManager.showSnackbar(message = "Third")

            assertEquals("First", awaitItem().message)
            assertEquals("Second", awaitItem().message)
            assertEquals("Third", awaitItem().message)
        }
    }

    @Test
    fun onAction_callback_is_preserved_in_event() = runTest {
        var actionTriggered = false
        snackbarManager.events.test {
            snackbarManager.showSnackbar(
                message = "Item removed",
                actionLabel = "Undo",
                onAction = { actionTriggered = true },
            )

            val event = awaitItem()
            event.onAction?.invoke()
            assertTrue(actionTriggered)
        }
    }

    @Test
    fun withDismissAction_is_passed_through() = runTest {
        snackbarManager.events.test {
            snackbarManager.showSnackbar(message = "Notice", withDismissAction = true)

            val event = awaitItem()
            assertTrue(event.withDismissAction)
        }
    }
}
