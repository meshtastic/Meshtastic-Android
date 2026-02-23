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
package org.meshtastic.core.resources

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * A wrapper class for UI text that can be either a dynamic string or a localized string resource. This allows passing
 * text from domain/data layers to the UI without resolving strings early.
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    class Resource(val res: StringResource, vararg val args: Any) : UiText()

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value
        is Resource -> {
            val resolvedArgs =
                args.map { arg ->
                    if (arg is StringResource) {
                        stringResource(arg)
                    } else {
                        arg
                    }
                }
            @Suppress("SpreadOperator")
            stringResource(res, *resolvedArgs.toTypedArray())
        }
    }

    /** Resolves the string in a suspend context. Useful for non-composable code like snackbars. */
    suspend fun resolve(): String = when (this) {
        is DynamicString -> value
        is Resource -> {
            val resolvedArgs =
                args.map { arg ->
                    if (arg is StringResource) {
                        getString(arg)
                    } else {
                        arg
                    }
                }
            @Suppress("SpreadOperator")
            getString(res, *resolvedArgs.toTypedArray())
        }
    }
}
