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
import org.meshtastic.core.model.util.isOtaStatusNotification
import org.meshtastic.proto.ClientNotification
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
        assertTrue(summary.contains("queryParameterCount=2"))
        assertTrue(summary.contains("schemeLength=5"))
        assertFalse(summary.contains("token"))
        assertFalse(summary.contains("add"))
    }

    @Test
    fun sanitized_import_summary_treats_blank_fragment_as_absent() {
        val summary = CommonUri.parse("https://meshtastic.org/e/#").toSanitizedImportSummary()

        assertTrue(summary.contains("hasFragment=false"))
    }

    @Test
    fun sanitized_import_summary_handles_opaque_mailto_uri() {
        val summary = CommonUri.parse("mailto:someone@example.com").toSanitizedImportSummary()

        assertTrue(summary.contains("queryParameterCount=0"))
        assertTrue(summary.contains("schemeLength=6"))
        assertFalse(summary.contains("someone@example.com"))
    }

    @Test
    fun sanitized_import_summary_omits_a_host_that_is_not_meshtastic() {
        val summary = CommonUri.parse("https://alice%40example.com/").toSanitizedImportSummary()

        assertTrue(summary.contains("meshtasticHost=false"))
        assertFalse(summary.contains("alice"))
        assertFalse(summary.contains("example.com"))
    }

    @Test
    fun sanitized_import_summary_flags_a_meshtastic_host() {
        val summary = CommonUri.parse("https://www.meshtastic.org/e/").toSanitizedImportSummary()

        assertTrue(summary.contains("meshtasticHost=true"))
    }

    @Test
    fun sanitized_import_summary_handles_opaque_scheme_only_uri() {
        val summary = CommonUri.parse("foo:bar").toSanitizedImportSummary()

        assertTrue(summary.contains("schemeLength=3"))
        assertTrue(summary.contains("queryParameterCount=0"))
    }

    @Test
    fun ota_status_notifications_are_suppressed() {
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Rebooting to WiFi OTA" }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Rebooting to BLE OTA" }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Cannot start OTA: OTA Loader partition not found." }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "OTA Loader does not support WiFi" }
                .build()
                .isOtaStatusNotification(),
        )
        assertTrue(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Unable to switch to the OTA partition." }
                .build()
                .isOtaStatusNotification(),
        )
    }

    @Test
    fun non_ota_status_notifications_are_not_suppressed() {
        assertFalse(
            ClientNotification.Builder().also { wb -> wb.message = "Low battery" }.build().isOtaStatusNotification(),
        )
        assertFalse(
            ClientNotification.Builder()
                .also { wb -> wb.message = "Key verification requested" }
                .build()
                .isOtaStatusNotification(),
        )
        assertFalse(
            ClientNotification.Builder()
                .also { wb -> wb.message = "ROTATE credentials" }
                .build()
                .isOtaStatusNotification(),
        )
        assertFalse(
            ClientNotification.Builder().also { wb -> wb.message = "Quota exceeded" }.build().isOtaStatusNotification(),
        )
    }
}
