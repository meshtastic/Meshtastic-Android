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
package org.meshtastic.feature.messaging

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/** Nodes are found by tag: string resources do not load in the wasmJs browser tests. */
@OptIn(ExperimentalTestApi::class)
class ComposerOrNoticeTest {

    private fun ComposeUiTest.setBottomBar(retired: Boolean, transparent: Boolean) = setContent {
        ComposerOrNotice(isRetiredChannel = retired, sendBlockedByTransparentMode = transparent) {
            Box(Modifier.size(48.dp).testTag(COMPOSER_TAG))
        }
    }

    @Test
    fun the_transparent_mode_notice_replaces_the_composer() = runComposeUiTest {
        setBottomBar(retired = false, transparent = true)

        onNodeWithTag(TRANSPARENT_MODE_SEND_NOTICE_TAG).assertIsDisplayed()
        onNodeWithTag(COMPOSER_TAG).assertDoesNotExist()
    }

    @Test
    fun a_retired_channel_shows_its_own_notice_while_transparent_mode_is_on() = runComposeUiTest {
        setBottomBar(retired = true, transparent = true)

        onNodeWithTag(RETIRED_CHANNEL_NOTICE_TAG).assertIsDisplayed()
        onNodeWithTag(TRANSPARENT_MODE_SEND_NOTICE_TAG).assertDoesNotExist()
        onNodeWithTag(COMPOSER_TAG).assertDoesNotExist()
    }

    @Test
    fun the_composer_shows_when_nothing_blocks_sending() = runComposeUiTest {
        setBottomBar(retired = false, transparent = false)

        onNodeWithTag(COMPOSER_TAG).assertIsDisplayed()
        onNodeWithTag(TRANSPARENT_MODE_SEND_NOTICE_TAG).assertDoesNotExist()
    }

    private companion object {
        const val COMPOSER_TAG = "composer"
    }
}
