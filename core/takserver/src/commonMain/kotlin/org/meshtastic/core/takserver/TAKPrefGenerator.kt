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
package org.meshtastic.core.takserver

import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.XML

object TAKPrefGenerator {
    private val xmlSerializer = XML {
        xmlDeclMode = XmlDeclMode.Charset
        indentString = "  "
    }

    fun generateConfigPref(
        serverHost: String = "127.0.0.1",
        port: Int = DEFAULT_TAK_PORT,
        description: String = "Meshtastic TAK Server",
    ): String {
        val prefs =
            TAKPreferencesXml(
                preferences =
                listOf(
                    TAKPreferenceXml(
                        version = "1",
                        name = "cot_streams",
                        entries =
                        listOf(
                            TAKEntryXml("count", "class java.lang.Integer", "1"),
                            TAKEntryXml("description0", "class java.lang.String", description),
                            TAKEntryXml("enabled0", "class java.lang.Boolean", "true"),
                            TAKEntryXml("connectString0", "class java.lang.String", "$serverHost:$port:tcp"),
                        ),
                    ),
                    TAKPreferenceXml(
                        version = "1",
                        name = "com.atakmap.app_preferences",
                        entries =
                        listOf(TAKEntryXml("displayServerConnectionWidget", "class java.lang.Boolean", "true")),
                    ),
                ),
            )

        return xmlSerializer
            .encodeToString(TAKPreferencesXml.serializer(), prefs)
            .replace(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<?xml version='1.0' encoding='ASCII' standalone='yes'?>",
            )
    }
}
