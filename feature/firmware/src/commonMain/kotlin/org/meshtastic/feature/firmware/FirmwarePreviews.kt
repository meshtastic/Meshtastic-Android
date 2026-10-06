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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_maintenance_wrong_destination
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.firmware.game.ChirpyHopContent
import org.meshtastic.feature.firmware.game.ChirpyHopUpdatePhase
import org.meshtastic.feature.firmware.game.ChirpyHopUpdateStatus

// These previews intentionally wrap-content (no fillMaxSize) so the generated reference images are tight crops of
// the status block — the docs reference the status component itself, not the whole screen. See docs/assets/screenshots.

@PreviewLightDark
@Composable
fun VerifyingStatePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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

@PreviewLightDark
@Composable
internal fun UsbMaintenanceCardPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp)) {
                UsbMaintenanceCard(
                    deviceName = "RAK4631",
                    latestBootloader = "0.9.2-OTAFIX2.5",
                    onBootloaderUpgrade = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
internal fun BootloaderReviewPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BootloaderReviewState(
                    versions = BootloaderVersions(installed = "0.9.2-OTAFIX2.3-BP1.5", available = "0.9.2-OTAFIX2.5"),
                    onUpgrade = {},
                    onSkip = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
internal fun BootloaderUpToDatePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                BootloaderReviewState(
                    versions = BootloaderVersions(installed = "0.9.2-OTAFIX2.5", available = "0.9.2-OTAFIX2.5"),
                    onUpgrade = {},
                    onSkip = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
internal fun UsbMaintenanceCardRefusedPreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp)) {
                WipeDeviceToggle(
                    updateMethod = FirmwareUpdateMethod.Usb,
                    refusal = UsbMaintenanceRefusal.UnknownSoftDevice,
                    wipeDevice = false,
                    onWipeDeviceChange = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
internal fun AwaitingEraseFileSavePreview() {
    AppTheme {
        Surface {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AwaitingFileSaveState(
                    state =
                    FirmwareUpdateState.AwaitingFileSave(
                        uf2Artifact = null,
                        fileName = null,
                        step = UsbFileSaveStep.FactoryErase,
                        retryMessage = UiText.Resource(Res.string.firmware_maintenance_wrong_destination),
                    ),
                    onSaveFile = {},
                    onPickVolume = {},
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ChirpyHopPreview() {
    AppTheme {
        ChirpyHopContent(
            status =
            ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Active, UiText.DynamicString("Uploading firmware"), 0.42f),
            bestScore = 27,
            onScore = {},
            onClose = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun ChirpyHopFinishedPreview() {
    AppTheme {
        ChirpyHopContent(
            status =
            ChirpyHopUpdateStatus(ChirpyHopUpdatePhase.Complete, UiText.DynamicString("Update Successful!"), 1f),
            bestScore = 27,
            onScore = {},
            onClose = {},
        )
    }
}
