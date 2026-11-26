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
package org.meshtastic.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ChannelItemUiTest {
    private val checkbox = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    @Test
    fun a_selectable_row_toggles_once_per_click_anywhere_on_it() = runComposeUiTest {
        val toggles = mutableListOf<Boolean>()
        setContent {
            AppTheme {
                ChannelItem(
                    index = 3,
                    title = "Field",
                    enabled = true,
                    selection = ChannelItemSelection(selected = false, onSelectedChange = { toggles += it }),
                    supportingContent = { Text("Shares your location") },
                ) {
                    Text("trailing")
                }
            }
        }

        // Unmerged nodes, so each click lands on that child's own bounds rather than on the merged row's center.
        onNodeWithText("Field", useUnmergedTree = true).performClick()
        onNodeWithText("3", useUnmergedTree = true).performClick()
        onNodeWithText("Shares your location", useUnmergedTree = true).performClick()

        assertEquals(listOf(true, true, true), toggles)
    }

    @Test
    fun a_selectable_row_is_one_checkbox_that_reads_its_supporting_content() = runComposeUiTest {
        setContent {
            AppTheme {
                ChannelItem(
                    index = 3,
                    title = "Field",
                    enabled = true,
                    selection = ChannelItemSelection(selected = true, onSelectedChange = {}),
                    supportingContent = { Text("Shares your location") },
                ) {}
            }
        }

        onAllNodes(checkbox).assertCountEquals(1)
        onNode(checkbox and hasText("Field") and hasText("Shares your location")).assertExists()
    }

    @Test
    fun a_disabled_selectable_row_does_not_toggle() = runComposeUiTest {
        var toggles = 0
        setContent {
            AppTheme {
                ChannelItem(
                    index = 0,
                    title = "Field",
                    enabled = false,
                    selection = ChannelItemSelection(selected = true, onSelectedChange = { toggles++ }),
                ) {}
            }
        }

        onNodeWithText("Field", useUnmergedTree = true).performClick()

        assertEquals(0, toggles)
    }

    @Test
    fun a_row_without_a_selection_is_a_button_whose_badge_clicks_the_row() = runComposeUiTest {
        var clicks = 0
        setContent {
            AppTheme { ChannelItem(index = 3, title = "Field", enabled = true, onClick = { clicks++ }) {} }
        }

        onNodeWithText("Field", useUnmergedTree = true).performClick()
        onNodeWithText("3", useUnmergedTree = true).performClick()

        assertEquals(2, clicks)
    }
}
