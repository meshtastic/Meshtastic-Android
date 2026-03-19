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
package org.meshtastic.core.ui.emoji

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.repository.CustomEmojiPrefs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EmojiPickerViewModelTest {

    private lateinit var viewModel: EmojiPickerViewModel
    private val customEmojiPrefs: CustomEmojiPrefs = mock(MockMode.autofill)
    private val frequencyFlow = MutableStateFlow<String?>(null)

    @BeforeTest
    fun setUp() {
        every { customEmojiPrefs.customEmojiFrequency } returns frequencyFlow
        viewModel = EmojiPickerViewModel(customEmojiPrefs)
    }

    @Test
    fun testInitialization() {
        assertNotNull(viewModel)
    }

    @Test
    fun `customEmojiFrequency property delegates to prefs`() {
        frequencyFlow.value = "👍=10"
        assertEquals("👍=10", viewModel.customEmojiFrequency)

        every { customEmojiPrefs.setCustomEmojiFrequency(any()) } returns Unit
        viewModel.customEmojiFrequency = "❤️=5"
        verify { customEmojiPrefs.setCustomEmojiFrequency("❤️=5") }
    }
}
