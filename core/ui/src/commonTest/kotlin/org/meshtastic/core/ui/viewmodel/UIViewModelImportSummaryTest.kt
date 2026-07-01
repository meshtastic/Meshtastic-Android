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
package org.meshtastic.core.ui.viewmodel

import org.meshtastic.core.common.util.CommonUri
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UIViewModelImportSummaryTest {
    @Test
    fun sanitized_import_summary_omits_sensitive_uri_contents() {
        val fragment = "super-secret-fragment"
        val uri = CommonUri.parse("https://meshtastic.org/e/private-group?token=super-secret-query&add=true#$fragment")

        val summary = uri.toSanitizedImportSummary()

        assertFalse(summary.contains("private-group"))
        assertFalse(summary.contains("super-secret-query"))
        assertFalse(summary.contains(fragment))
        assertTrue(summary.contains("pathSegmentCount=2"))
        assertTrue(summary.contains("hasFragment=true"))
        assertTrue(summary.contains("fragmentLength=${fragment.length}"))
        assertTrue(summary.contains("queryKeys=[add, token]"))
    }

    @Test
    fun sanitized_import_summary_treats_blank_fragment_as_absent() {
        val summary = CommonUri.parse("https://meshtastic.org/e/#").toSanitizedImportSummary()

        assertTrue(summary.contains("hasFragment=false"))
    }
}
