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
package org.meshtastic.app

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.app.preview.DebugActiveFiltersOrModePreview
import org.meshtastic.app.preview.DebugActiveFiltersPreview
import org.meshtastic.app.preview.MultiPreview
import org.meshtastic.app.preview.NotesSectionEmptyPreview
import org.meshtastic.app.preview.NotesSectionPreview
import org.meshtastic.app.preview.PacketResponseErrorPreview
import org.meshtastic.app.preview.PacketResponseLoadingPreview
import org.meshtastic.app.preview.PacketResponseSuccessPreview
import org.meshtastic.app.preview.QrDialogPreview

/** Screenshot tests for additional feature composables (QrDialog, NotesSection, PacketResponse, etc.). */
class AdditionalFeatureScreenshotTests {
    @PreviewTest
    @MultiPreview
    @Composable
    fun qrDialogScreenshot() {
        QrDialogPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun notesSectionScreenshot() {
        NotesSectionPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun notesSectionEmptyScreenshot() {
        NotesSectionEmptyPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun packetResponseLoadingScreenshot() {
        PacketResponseLoadingPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun packetResponseSuccessScreenshot() {
        PacketResponseSuccessPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun packetResponseErrorScreenshot() {
        PacketResponseErrorPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun debugActiveFiltersScreenshot() {
        DebugActiveFiltersPreview()
    }

    @PreviewTest
    @MultiPreview
    @Composable
    fun debugActiveFiltersOrModeScreenshot() {
        DebugActiveFiltersOrModePreview()
    }
}
