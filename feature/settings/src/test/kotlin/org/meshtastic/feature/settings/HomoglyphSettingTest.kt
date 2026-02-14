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
package org.meshtastic.feature.settings

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.meshtastic.core.strings.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.use_homoglyph_characters_encoding
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomoglyphSettingTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun homoglyphSetting_isVisible_forRussianLocale() {
        val russianConfig = Configuration().apply { setLocale(Locale.forLanguageTag("ru")) }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides russianConfig) {
                HomoglyphSetting(homoglyphEncodingEnabled = false, onToggle = {})
            }
        }

        val expectedText = getString(Res.string.use_homoglyph_characters_encoding)
        composeTestRule.onNodeWithText(expectedText).assertIsDisplayed()
    }

    @Test
    fun homoglyphSetting_isNotVisible_forEnglishLocale() {
        val englishConfig = Configuration().apply { setLocale(Locale.forLanguageTag("en")) }

        composeTestRule.setContent {
            CompositionLocalProvider(LocalConfiguration provides englishConfig) {
                HomoglyphSetting(homoglyphEncodingEnabled = false, onToggle = {})
            }
        }

        val expectedText = getString(Res.string.use_homoglyph_characters_encoding)
        composeTestRule.onNodeWithText(expectedText).assertDoesNotExist()
    }
}
