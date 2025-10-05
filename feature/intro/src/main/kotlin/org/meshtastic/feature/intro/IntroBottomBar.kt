/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.intro

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A common bottom bar used across app introduction screens. Provides consistent "Skip" and "Configure" (or "Next")
 * buttons.
 *
 * @param onSkip Callback for the skip action.
 * @param onConfigure Callback for the main configure/next action.
 * @param skipButtonText Text for the skip button.
 * @param configureButtonText Text for the configure/next button.
 * @param showSkipButton Whether to display the skip button. Defaults to true.
 */
@Composable
internal fun IntroBottomBar(
    onSkip: () -> Unit,
    onConfigure: () -> Unit,
    skipButtonText: String,
    configureButtonText: String,
    showSkipButton: Boolean = true,
) {
    BottomAppBar(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp)) {
        if (showSkipButton) {
            Button(onClick = onSkip) { Text(skipButtonText) }
        }

        Spacer(modifier = Modifier.fillMaxWidth().weight(1f))

        Button(onClick = onConfigure) { Text(configureButtonText) }
    }
}
