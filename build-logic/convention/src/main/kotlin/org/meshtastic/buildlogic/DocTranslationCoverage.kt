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
package org.meshtastic.buildlogic

/**
 * A Crowdin page at or above this English share is left out of the app, which then shows the English page (or, on
 * google, its on-device translation). Crowdin exports every page for every locale, translation-memory fills only
 * headings and table cells, and those near-English copies made up most of the in-app docs bundle.
 */
const val UNTRANSLATED_DOC_THRESHOLD = 0.9

private val PADDING = Regex("[ \\t-]+")

/**
 * Share of [translated]'s text, by characters, made of lines that also appear in [source]. Crowdin re-pads markdown
 * tables, so runs of spaces, tabs and dashes are collapsed before comparing, and `last_updated` lines are ignored.
 */
fun untranslatedShare(translated: String, source: String): Double {
    val sourceLines = normalizedLines(source).toSet()
    val lines = normalizedLines(translated)
    val total = lines.sumOf { it.length }
    if (total == 0) return 1.0
    return lines.filter { it in sourceLines }.sumOf { it.length }.toDouble() / total
}

private fun normalizedLines(markdown: String): List<String> = markdown
    .lineSequence()
    .map { it.replace(PADDING, " ").trim() }
    .filter { it.isNotEmpty() && !it.startsWith("last_updated") }
    .toList()
