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

package org.meshtastic.core.prefs.radio

import android.content.SharedPreferences
import org.meshtastic.core.prefs.NullableStringPrefDelegate
import org.meshtastic.core.prefs.di.RadioSharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

interface RadioPrefs {
    var devAddr: String?
}

fun RadioPrefs.isBle() = devAddr?.startsWith("x") == true

fun RadioPrefs.isSerial() = devAddr?.startsWith("s") == true

fun RadioPrefs.isMock() = devAddr?.startsWith("m") == true

fun RadioPrefs.isTcp() = devAddr?.startsWith("t") == true

fun RadioPrefs.isNoop() = devAddr?.startsWith("n") == true

@Singleton
class RadioPrefsImpl @Inject constructor(@RadioSharedPreferences prefs: SharedPreferences) : RadioPrefs {
    override var devAddr: String? by NullableStringPrefDelegate(prefs, "devAddr2", null)
}
