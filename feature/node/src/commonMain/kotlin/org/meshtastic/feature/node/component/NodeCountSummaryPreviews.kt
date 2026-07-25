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
@file:Suppress("MagicNumber", "PreviewPublic")

package org.meshtastic.feature.node.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.meshtastic.core.ui.theme.AppTheme

/** English labels matching the counts from issue #6268 (76 online / 398 shown / 1247 total). */
private val ENGLISH_LABELS = listOf("76 online", "398 shown", "1247 total")

/**
 * Deliberately verbose stand-ins for the longest Crowdin locales (German/Hungarian style compounds run roughly twice
 * the English width). Hard-coded rather than locale-switched so the preview does not depend on a translated bundle.
 */
private val LONG_LOCALE_LABELS = listOf("76 Knoten verbunden", "398 Knoten angezeigt", "1247 Knoten insgesamt")

private const val ENGLISH_DESCRIPTION = "(76 online / 398 shown / 1247 total)"

@Composable
private fun PreviewBody(labels: List<String>, description: String) {
    AppTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceDim) {
            NodeCountSummaryContent(
                labels = labels,
                contentDescription = description,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
    }
}

/** Default case on a narrow (compact-width) phone. */
@Preview(name = "NodeCountSummary - default", widthDp = 360)
@Composable
fun NodeCountSummaryDefaultPreview() {
    PreviewBody(ENGLISH_LABELS, ENGLISH_DESCRIPTION)
}

/** Narrowest realistic phone width — labels must reflow, never ellipsize. */
@Preview(name = "NodeCountSummary - narrow 320dp", widthDp = 320)
@Composable
fun NodeCountSummaryNarrowPreview() {
    PreviewBody(ENGLISH_LABELS, ENGLISH_DESCRIPTION)
}

/** Long/translated text: verbose locale on a narrow phone. */
@Preview(name = "NodeCountSummary - long locale", widthDp = 320)
@Composable
fun NodeCountSummaryLongLocalePreview() {
    PreviewBody(LONG_LOCALE_LABELS, ENGLISH_DESCRIPTION)
}

/** Accessibility text size: largest Android font scale. */
@Preview(name = "NodeCountSummary - font scale 2x", widthDp = 360, fontScale = 2.0f)
@Composable
fun NodeCountSummaryLargeFontPreview() {
    PreviewBody(ENGLISH_LABELS, ENGLISH_DESCRIPTION)
}

/** Worst case: verbose locale AND largest font scale on a narrow phone. */
@Preview(name = "NodeCountSummary - long locale font scale 2x", widthDp = 320, fontScale = 2.0f)
@Composable
fun NodeCountSummaryLongLocaleLargeFontPreview() {
    PreviewBody(LONG_LOCALE_LABELS, ENGLISH_DESCRIPTION)
}

/** Wide/tablet layout — the labels stay on one line and the block does not stretch awkwardly. */
@Preview(name = "NodeCountSummary - wide 840dp", widthDp = 840)
@Composable
fun NodeCountSummaryWidePreview() {
    PreviewBody(ENGLISH_LABELS, ENGLISH_DESCRIPTION)
}
