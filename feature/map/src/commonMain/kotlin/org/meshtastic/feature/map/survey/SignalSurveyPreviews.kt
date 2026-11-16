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
package org.meshtastic.feature.map.survey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.meshtastic.core.model.SignalSurveySession
import org.meshtastic.core.ui.theme.AppTheme

private const val PREVIEW_START_MILLIS = 1_794_768_000_000L
private const val HOUR_MILLIS = 3_600_000L

/** The survey controls mid-walk: recording into a new session, with an earlier one kept and shown on the map. */
@PreviewLightDark
@Composable
@Suppress("PreviewPublic", "MagicNumber")
fun SignalSurveySectionPreview() {
    val live = SignalSurveySession(2, PREVIEW_START_MILLIS, null, "LONG_FAST", readingCount = 38)
    val earlier = SignalSurveySession(1, PREVIEW_START_MILLIS - HOUR_MILLIS, PREVIEW_START_MILLIS, "LONG_FAST", 412)
    AppTheme {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            SignalSurveyContent(
                state =
                SignalSurveyUiState(
                    recording = true,
                    sessions =
                    listOf(
                        SurveySessionUi(live, visible = true, recording = true),
                        SurveySessionUi(earlier, visible = true, recording = false),
                    ),
                ),
                actions = SignalSurveyActions(),
            )
        }
    }
}
