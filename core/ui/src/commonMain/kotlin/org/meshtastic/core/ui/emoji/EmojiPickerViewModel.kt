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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.MissingResourceException
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.CustomEmojiPrefs

@KoinViewModel
internal class EmojiPickerViewModel(
    private val customEmojiPrefs: CustomEmojiPrefs,
    private val emojiRepository: EmojiRepository,
) : ViewModel() {

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded

    /** Emoji categories, available after [isLoaded] emits true. */
    val categories: List<EmojiCategory>
        get() = emojiRepository.categories

    /** Flat list of all emojis, available after [isLoaded] emits true. */
    val allEmojis: List<Emoji>
        get() = emojiRepository.all

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError

    init {
        viewModelScope.launch {
            try {
                emojiRepository.preload()
                _isLoaded.value = true
            } catch (e: MissingResourceException) {
                Logger.e(tag = "EmojiPickerViewModel", throwable = e) { "Failed to load emoji data" }
                _loadError.value = true
            } catch (e: IllegalStateException) {
                Logger.e(tag = "EmojiPickerViewModel", throwable = e) { "Failed to load emoji data" }
                _loadError.value = true
            }
        }
    }

    /** User's preferred skin tone (persisted across sessions). */
    val preferredSkinToneIndex: StateFlow<Int> = customEmojiPrefs.preferredSkinToneIndex

    fun setPreferredSkinTone(index: Int) {
        customEmojiPrefs.setPreferredSkinToneIndex(index)
    }

    var customEmojiFrequency: String?
        get() = customEmojiPrefs.customEmojiFrequency.value
        set(value) {
            customEmojiPrefs.setCustomEmojiFrequency(value)
        }
}
