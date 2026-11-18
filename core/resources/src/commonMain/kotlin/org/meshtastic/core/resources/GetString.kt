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

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString as composeGetPluralString
import org.jetbrains.compose.resources.getString as composeGetString

/**
 * Retrieves a string from the [StringResource], blocking the calling thread on the resource loader. Only for callbacks
 * that cannot suspend, such as a Glance `onCompositionError`; everything else uses [getStringSuspend]. A browser cannot
 * block its event loop, so the web actual throws.
 */
expect fun getString(stringResource: StringResource): String

/** Retrieves a formatted string from the [StringResource], blocking. Same restriction as the overload above. */
expect fun getString(stringResource: StringResource, vararg formatArgs: Any): String

/** Retrieves a string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource): String = composeGetString(stringResource)

/** Retrieves a formatted string from the [StringResource] in a suspending manner. */
suspend fun getStringSuspend(stringResource: StringResource, vararg formatArgs: Any): String {
    val resolvedArgs =
        formatArgs
            .map { arg ->
                if (arg is StringResource) {
                    composeGetString(arg)
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

/** Retrieves a string for an explicit [environment], so a composable's lookup follows its locale and theme. */
suspend fun getStringSuspend(environment: ResourceEnvironment, stringResource: StringResource): String =
    composeGetString(environment, stringResource)

/** Retrieves a formatted string for an explicit [environment]. */
@Suppress("SpreadOperator")
suspend fun getStringSuspend(
    environment: ResourceEnvironment,
    stringResource: StringResource,
    vararg formatArgs: Any,
): String = composeGetString(environment, stringResource, *formatArgs)

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
