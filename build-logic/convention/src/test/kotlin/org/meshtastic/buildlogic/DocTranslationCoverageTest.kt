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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocTranslationCoverageTest {
    private val english =
        """
        |---
        |title: Nodes
        |last_updated: 2026-10-01
        |---
        |## Overview
        |The node list shows every node your radio has heard, sorted by when it was last heard.
        |Tap a node to open its details, where signal quality, telemetry and position history live.
        |Long-press a node for quick actions such as sending a direct message or requesting a traceroute.
        |Filters at the top narrow the list by role, hop count, or whether the node has a position.
        |Nodes not heard for a long time fade out and can be hidden from the list in the filter settings.
        |
        || Setting | Meaning |
        ||---|---|
        || Role | How the node relays packets |
        """
            .trimMargin()

    @Test
    fun `a verbatim copy is entirely untranslated`() {
        assertEquals(1.0, untranslatedShare(english, english))
    }

    @Test
    fun `table padding and a new last_updated stamp do not count as translation`() {
        val repadded =
            english
                .replace("| Role | How the node relays packets |", "| Role      | How the node relays packets   |")
                .replace("|---|---|", "| ------- | ------------------------------ |")
                .replace("last_updated: 2026-10-01", "last_updated: 2026-11-02")

        assertEquals(1.0, untranslatedShare(repadded, english))
    }

    @Test
    fun `translated headings alone leave the page above the threshold`() {
        val headingsOnly = english.replace("## Overview", "## Übersicht").replace("| Setting |", "| Einstellung |")

        assertTrue(untranslatedShare(headingsOnly, english) >= UNTRANSLATED_DOC_THRESHOLD)
    }

    @Test
    fun `translated prose drops the page below the threshold`() {
        val translated =
            english.replace(
                "The node list shows every node your radio has heard, sorted by when it was last heard.",
                "Die Knotenliste zeigt jeden Knoten, den dein Funkgerät gehört hat, sortiert nach dem letzten Empfang.",
            )

        assertTrue(untranslatedShare(translated, english) < UNTRANSLATED_DOC_THRESHOLD)
    }

    @Test
    fun `nine tenths English text scores exactly the threshold`() {
        val source = "a".repeat(90)

        assertEquals(UNTRANSLATED_DOC_THRESHOLD, untranslatedShare(source + "\n" + "b".repeat(10), source))
    }

    @Test
    fun `an empty page counts as untranslated`() {
        assertEquals(1.0, untranslatedShare("\n\n", english))
    }
}
