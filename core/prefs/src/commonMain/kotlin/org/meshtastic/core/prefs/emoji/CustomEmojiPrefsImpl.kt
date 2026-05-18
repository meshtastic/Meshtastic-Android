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
package org.meshtastic.core.prefs.emoji

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.CustomEmojiPrefs

@Single
class CustomEmojiPrefsImpl(
    @Named("CustomEmojiDataStore") private val dataStore: DataStore<Preferences>,
    dispatchers: CoroutineDispatchers,
) : CustomEmojiPrefs {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val customEmojiFrequency: StateFlow<String?> =
        dataStore.data.map { it[KEY_EMOJI_FREQ_PREF] }.stateIn(scope, SharingStarted.Eagerly, null)

    override val preferredSkinToneIndex: StateFlow<Int> =
        dataStore.data.map { it[KEY_SKIN_TONE_PREF] ?: 0 }.stateIn(scope, SharingStarted.Eagerly, 0)

    override fun setCustomEmojiFrequency(frequency: String?) {
        scope.launch {
            dataStore.edit { prefs ->
                if (frequency == null) {
                    prefs.remove(KEY_EMOJI_FREQ_PREF)
                } else {
                    prefs[KEY_EMOJI_FREQ_PREF] = frequency
                }
            }
        }
    }

    override fun setPreferredSkinToneIndex(index: Int) {
        scope.launch { dataStore.edit { prefs -> prefs[KEY_SKIN_TONE_PREF] = index } }
    }

    companion object {
        const val KEY_EMOJI_FREQ = "pref_key_custom_emoji_freq"
        val KEY_EMOJI_FREQ_PREF = stringPreferencesKey(KEY_EMOJI_FREQ)
        val KEY_SKIN_TONE_PREF = intPreferencesKey("pref_key_skin_tone")
    }
}
