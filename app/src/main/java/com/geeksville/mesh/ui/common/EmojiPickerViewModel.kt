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

package com.geeksville.mesh.ui.common

import androidx.lifecycle.ViewModel
import com.geeksville.mesh.android.prefs.CustomEmojiPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class EmojiPickerViewModel @Inject constructor(private val customEmojiPrefs: CustomEmojiPrefs) : ViewModel() {

    var customEmojiFrequency: String?
        get() = customEmojiPrefs.customEmojiFrequency
        set(value) {
            customEmojiPrefs.customEmojiFrequency = value
        }
}
