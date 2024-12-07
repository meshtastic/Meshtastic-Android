/*
 * Copyright (c) 2024 Meshtastic LLC
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

package com.geeksville.mesh.util

import android.content.Context
import androidx.emoji2.emojipicker.RecentEmojiAsyncProvider
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Define a custom recent emoji provider which shows most frequently used emoji
 */
class CustomRecentEmojiProvider(
    context: Context
) : RecentEmojiAsyncProvider {

    private val sharedPreferences =
        context.getSharedPreferences(RECENT_EMOJI_LIST_FILE_NAME, Context.MODE_PRIVATE)

    private val emoji2Frequency: MutableMap<String, Int> by lazy {
        sharedPreferences.getString(PREF_KEY_CUSTOM_EMOJI_FREQ, null)?.split(SPLIT_CHAR)
            ?.associate { entry ->
                entry.split(KEY_VALUE_DELIMITER, limit = 2).takeIf { it.size == 2 }
                    ?.let { it[0] to it[1].toInt() } ?: ("" to 0)
            }?.toMutableMap() ?: mutableMapOf()
    }

    override fun getRecentEmojiListAsync(): ListenableFuture<List<String>> =
        Futures.immediateFuture(emoji2Frequency.toList().sortedByDescending { it.second }
            .map { it.first })

    override fun recordSelection(emoji: String) {
        emoji2Frequency[emoji] = (emoji2Frequency[emoji] ?: 0) + 1
        saveToPreferences()
    }

    private fun saveToPreferences() {
        sharedPreferences
            .edit()
            .putString(PREF_KEY_CUSTOM_EMOJI_FREQ, emoji2Frequency.entries.joinToString(SPLIT_CHAR))
            .apply()
    }

    companion object {
        private const val PREF_KEY_CUSTOM_EMOJI_FREQ = "pref_key_custom_emoji_freq"
        private const val RECENT_EMOJI_LIST_FILE_NAME = "org.geeksville.emoji.prefs"
        private const val SPLIT_CHAR = ","
        private const val KEY_VALUE_DELIMITER = "="
    }
}
