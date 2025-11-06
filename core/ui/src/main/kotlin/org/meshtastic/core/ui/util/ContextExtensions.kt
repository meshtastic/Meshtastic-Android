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

package org.meshtastic.core.ui.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

suspend fun Context.showToast(@StringRes resId: Int) {
    showToast(getString(resId))
}

suspend fun Context.showToast(text: CharSequence) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

suspend fun Context.showToast(@StringRes resId: Int, vararg formatArgs: Any) {
    Toast.makeText(this, getString(resId, formatArgs), Toast.LENGTH_SHORT).show()
}
