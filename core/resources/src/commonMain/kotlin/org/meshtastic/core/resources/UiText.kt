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

    class Resource(val res: StringResource, vararg val args: Any) : UiText() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as Resource

            if (res != other.res) return false
            if (!args.contentEquals(other.args)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = res.hashCode()
            result = 31 * result + args.contentHashCode()
            return result
        }
    }

    @Composable
    fun asString(): String = when (this) {
        is DynamicString -> value

        is Resource -> {
            val resolvedArgs =
                args.map { arg ->
                    when (arg) {
                        is StringResource -> stringResource(arg)
                        is UiText -> arg.asString()
                        else -> arg
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
                    when (arg) {
                        is StringResource -> getString(arg)
                        is UiText -> arg.resolve()
                        else -> arg
                    }
                }
            @Suppress("SpreadOperator")
            getString(res, *resolvedArgs.toTypedArray())
        }
    }
}
