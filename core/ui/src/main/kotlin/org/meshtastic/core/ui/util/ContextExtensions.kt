/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

suspend fun Context.showToast(stringResource: StringResource) {
    showToast(getString(stringResource))
}

suspend fun Context.showToast(stringResource: StringResource, vararg formatArgs: Any) {
    Toast.makeText(this, getString(stringResource, formatArgs), Toast.LENGTH_SHORT).show()
}

suspend fun Context.showToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

/** Finds the [Activity] from a [Context]. */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
