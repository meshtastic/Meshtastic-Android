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
package org.meshtastic.screenshots.feature

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import org.meshtastic.feature.docs.ui.ChirpyAssistantContentPreview
import org.meshtastic.feature.docs.ui.ChirpyAssistantLoadingPreview
import org.meshtastic.feature.docs.ui.DocsBrowserScreenEmptyPreview
import org.meshtastic.feature.docs.ui.DocsBrowserScreenPreview
import org.meshtastic.feature.docs.ui.DocsPageContentPreview
import org.meshtastic.feature.docs.ui.DocsPageNotFoundPreview
import org.meshtastic.feature.docs.ui.DocsSearchBarEmptyPreview
import org.meshtastic.feature.docs.ui.DocsSearchBarWithQueryPreview

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsBrowser() {
    DocsBrowserScreenPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsBrowserEmpty() {
    DocsBrowserScreenEmptyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsPageContent() {
    DocsPageContentPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsPageNotFound() {
    DocsPageNotFoundPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotChirpyAssistant() {
    ChirpyAssistantContentPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotChirpyAssistantLoading() {
    ChirpyAssistantLoadingPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsSearchBarEmpty() {
    DocsSearchBarEmptyPreview()
}

@PreviewTest
@PreviewLightDark
@Composable
fun ScreenshotDocsSearchBarWithQuery() {
    DocsSearchBarWithQueryPreview()
}
