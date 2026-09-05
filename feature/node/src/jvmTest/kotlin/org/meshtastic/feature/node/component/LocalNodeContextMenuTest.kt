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
package org.meshtastic.feature.node.component

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.update_status
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The local node's own context menu: one entry, the fast path to its status message. */
@OptIn(ExperimentalTestApi::class)
class LocalNodeContextMenuTest {

    @Test
    fun `the update status entry opens the editor and closes the menu`() = runComposeUiTest {
        var updates = 0
        var dismissals = 0
        setContent {
            AppTheme {
                LocalNodeContextMenu(expanded = true, onUpdateStatus = { updates++ }, onDismiss = { dismissals++ })
            }
        }

        onNodeWithText(getString(Res.string.update_status)).performClick()

        assertEquals(1, updates)
        assertTrue(dismissals >= 1)
    }

    @Test
    fun `a collapsed menu shows nothing`() = runComposeUiTest {
        setContent { AppTheme { LocalNodeContextMenu(expanded = false, onUpdateStatus = {}, onDismiss = {}) } }

        onNodeWithText(getString(Res.string.update_status)).assertDoesNotExist()
    }
}
