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

package org.meshtastic.feature.settings.util

import android.content.Context

fun FixedUpdateIntervals.toDisplayString(context: Context): String = if (this == FixedUpdateIntervals.UNSET) {
    context.getString(org.meshtastic.core.strings.R.string.unset)
} else {
    name.split('_').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
}
