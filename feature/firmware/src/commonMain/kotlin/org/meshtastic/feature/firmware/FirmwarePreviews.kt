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
package org.meshtastic.feature.firmware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.ui.theme.AppTheme

@PreviewLightDark
@Composable
fun VerifyingStatePreview() {
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                VerifyingState()
            }
        }
    }
}

@PreviewLightDark
@Composable
fun CheckingStatePreview() {
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CheckingState()
            }
        }
    }
}

@PreviewLightDark
@Composable
fun ErrorStatePreview() {
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                ErrorState(error = UiText.DynamicString("Connection lost"), onRetry = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
fun SuccessStatePreview() {
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SuccessState(onDone = {})
            }
        }
    }
}

@PreviewLightDark
@Composable
fun DisclaimerDialogPreview() {
    AppTheme { Surface { DisclaimerDialog(updateMethod = FirmwareUpdateMethod.Ble, onDismiss = {}, onConfirm = {}) } }
}
