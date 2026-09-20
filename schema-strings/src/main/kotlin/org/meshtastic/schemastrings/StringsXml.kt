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
package org.meshtastic.schemastrings

/**
 * Line-level edits of a Compose `strings.xml`. Element bodies are copied verbatim so translations keep their escapes.
 */
object StringsXml {
    private val element =
        Regex("""[ \t]*<string name="([^"]+)"[^>]*>(.*?)</string>[ \t]*\r?\n?""", RegexOption.DOT_MATCHES_ALL)

    /** Every `<string>` body by name, in file order. */
    fun bodies(xml: String): LinkedHashMap<String, String> =
        element.findAll(xml).associateTo(LinkedHashMap()) { it.groupValues[1] to it.groupValues[2] }

    /** The text before `<resources>`, which carries the licence header. */
    fun header(xml: String): String = xml.substringBefore("<resources>")

    /** A whole file: header, an optional notice, then one element per entry in the map's (sorted) order. */
    fun render(header: String, bodies: Map<String, String>, notice: String?): String = buildString {
        append(header)
        append("<resources>\n")
        notice?.let { append("    ").append(it).append('\n') }
        for ((name, body) in bodies) {
            append("    <string name=\"").append(name).append("\">").append(body).append("</string>\n")
        }
        append("</resources>\n")
    }

    /** Only the XML metacharacters; quotes stay bare, as `check-string-escapes.py` requires. */
    fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
