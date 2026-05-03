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
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.repository.CustomEmojiPrefs

@KoinViewModel
class EmojiPickerViewModel(private val customEmojiPrefs: CustomEmojiPrefs) : ViewModel() {

    var customEmojiFrequency: String?
        get() = customEmojiPrefs.customEmojiFrequency.value
        set(value) {
            customEmojiPrefs.setCustomEmojiFrequency(value)
        }
}
