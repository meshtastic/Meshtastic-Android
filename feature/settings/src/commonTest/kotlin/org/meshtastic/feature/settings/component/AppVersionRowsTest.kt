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
package org.meshtastic.feature.settings.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AppVersionRowsTest {

    @Test
    fun `five taps on the version row reveal Chirpy Hop below it`() = runComposeUiTest {
        var unlocked by mutableStateOf(false)
        var played = false
        setContent {
            AppTheme {
                AppVersionRows(
                    hiddenFeaturesUnlocked = unlocked,
                    appVersionName = "2.8.0",
                    onUnlockHiddenFeatures = { unlocked = true },
                    onPlayChirpyHop = { played = true },
                )
            }
        }
        onNodeWithText("Chirpy Hop").assertDoesNotExist()

        repeat(5) { onNodeWithText("2.8.0").performClick() }

        onNodeWithText("Chirpy Hop").performClick()
        assertTrue(played)
    }

    @Test
    fun `four taps leave Chirpy Hop hidden`() = runComposeUiTest {
        var unlocked by mutableStateOf(false)
        setContent {
            AppTheme {
                AppVersionRows(
                    hiddenFeaturesUnlocked = unlocked,
                    appVersionName = "2.8.0",
                    onUnlockHiddenFeatures = { unlocked = true },
                    onPlayChirpyHop = {},
                )
            }
        }

        repeat(4) { onNodeWithText("2.8.0").performClick() }

        onNodeWithText("Chirpy Hop").assertDoesNotExist()
    }
}
