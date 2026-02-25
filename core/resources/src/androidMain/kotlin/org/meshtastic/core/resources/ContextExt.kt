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

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString as composeGetString

/** Retrieves a string from the [StringResource] in a blocking manner. Use primarily in non-composable code. */
fun getString(stringResource: StringResource): String = runBlocking { composeGetString(stringResource) }

/** Retrieves a formatted string from the [StringResource] in a blocking manner. */
fun getString(stringResource: StringResource, vararg formatArgs: Any): String = runBlocking {
    getStringSuspend(stringResource, *formatArgs)
}

/** Retrieves a string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource): String = composeGetString(stringResource)

/** Retrieves a formatted string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource, vararg formatArgs: Any): String {
    val resolvedArgs =
        formatArgs
            .map { arg ->
                if (arg is StringResource) {
                    // Resolve nested StringResources recursively
                    getStringSuspend(arg)
                } else {
                    arg
                }
            }
            .toTypedArray()

    // Compose Multiplatform doesn't fully support complex formatting like %.2f
    // Fetch the raw string and format it using standard Java String.format.
    val rawString = composeGetString(stringResource)
    @Suppress("SpreadOperator")
    return String.format(java.util.Locale.getDefault(), rawString, *resolvedArgs)
}
