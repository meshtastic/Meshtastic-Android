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
package org.meshtastic.feature.settings

internal actual fun nativeLanguageName(tag: String): String =
    (ownName(tag) ?: missingFromBrowser[tag] ?: tag).replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }

/**
 * Chromium ships language data only for its own UI languages and names any other language in the browser's language
 * instead. These are the shipped app locales it lacks (checked in Chrome), named as CLDR names them in themselves.
 */
private val missingFromBrowser =
    mapOf("be" to "беларуская", "ga" to "Gaeilge", "gl" to "galego", "is" to "íslenska", "sq" to "shqip")

/** The language's name in itself, or null when the browser would name it in some other language. */
@Suppress("UnusedParameter") // read by name inside the js() body
private fun ownName(tag: String): String? =
    js(
        """{
    const names = new Intl.DisplayNames([tag], { type: 'language' });
    const language = (locale) => locale.split('-')[0].toLowerCase();
    return language(names.resolvedOptions().locale) === language(tag) ? names.of(tag) || null : null;
}""",
    )
