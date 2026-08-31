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

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as composeGetPluralString
import org.jetbrains.compose.resources.getString as composeGetString

// runBlocking doesn't exist on wasmJs (no way to synchronously block a browser's single event-loop
// thread), so the blocking overloads below are expect/actual: real on android/jvm/iOS, an error on
// wasmJs (see wasmJsMain's GetString.wasmJs.kt) rather than a silently wrong return value.

/** Retrieves a string from the [StringResource] in a blocking manner. Use primarily in non-composable code. */
expect fun getString(stringResource: StringResource): String

/** Retrieves a formatted string from the [StringResource] in a blocking manner. */
expect fun getString(stringResource: StringResource, vararg formatArgs: Any): String

/** Retrieves a string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource): String = composeGetString(stringResource)

/** Retrieves a formatted string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource, vararg formatArgs: Any): String {
    val resolvedArgs =
        formatArgs
            .map { arg ->
                if (arg is StringResource) {
                    getStringSuspend(arg)
                } else {
                    arg
                }
            }
            .toTypedArray()

    return if (resolvedArgs.isNotEmpty()) {
        @Suppress("SpreadOperator")
        composeGetString(stringResource, *resolvedArgs)
    } else {
        composeGetString(stringResource)
    }
}

/** Retrieves a plural string in a suspending manner. */
suspend fun getPluralStringSuspend(
    pluralStringResource: PluralStringResource,
    quantity: Int,
    vararg formatArgs: Any,
): String = if (formatArgs.isNotEmpty()) {
    @Suppress("SpreadOperator")
    composeGetPluralString(pluralStringResource, quantity, *formatArgs)
} else {
    composeGetPluralString(pluralStringResource, quantity)
}
