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
@file:Suppress("detekt:ALL")

package org.meshtastic.core.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isSensitiveData
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.copy
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.qr_code
import org.meshtastic.core.resources.share_action
import org.meshtastic.core.resources.share_by_tap_subtext
import org.meshtastic.core.resources.share_qr_subtext
import org.meshtastic.core.resources.write_nfc
import org.meshtastic.core.resources.write_nfc_failed
import org.meshtastic.core.resources.write_nfc_subtext
import org.meshtastic.core.resources.write_nfc_success
import org.meshtastic.core.resources.write_nfc_text
import org.meshtastic.core.ui.icon.Copy
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nfc
import org.meshtastic.core.ui.icon.Share
import org.meshtastic.core.ui.theme.SemanticColors
import org.meshtastic.core.ui.util.LocalNfcEmulationSupported
import org.meshtastic.core.ui.util.LocalNfcEmulatorProvider
import org.meshtastic.core.ui.util.LocalNfcScannerSupported
import org.meshtastic.core.ui.util.LocalNfcWriterProvider
import org.meshtastic.core.ui.util.SetScreenBrightness
import org.meshtastic.core.ui.util.createClipEntry
import org.meshtastic.core.ui.util.rememberQrCodePainter
import org.meshtastic.core.ui.util.rememberShareText

private const val QR_DISPLAY_SIZE = 320

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrDialog(
    title: String,
    uriString: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    shareSubject: String = title,
) {
    val clipboardManager = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    // Render at the actual on-screen pixel size so low-density devices don't over-allocate bitmap memory.
    val qrRenderSizePx = with(LocalDensity.current) { QR_DISPLAY_SIZE.dp.roundToPx() }
    val qrPainter = rememberQrCodePainter(uriString, qrRenderSizePx)

    val nfcSupported = LocalNfcScannerSupported.current
    val nfcWriter = LocalNfcWriterProvider.current
    val shareText = rememberShareText()
    val hceSupported = LocalNfcEmulationSupported.current
    val nfcEmulator = LocalNfcEmulatorProvider.current
    var isWritingNfc by rememberSaveable { mutableStateOf(false) }
    var showNfcDisabled by rememberSaveable { mutableStateOf(false) }
    var writeSucceeded by rememberSaveable { mutableStateOf<Boolean?>(null) }

    SetScreenBrightness(1f)

    // Offer the same URL to readers for as long as this dialog is up. The exposure matches the
    // QR already on screen, and arming ends with the dialog. An armed write takes the radio into
    // reader mode, which suspends card emulation, so the two never contend.
    if (hceSupported) {
        nfcEmulator(uriString)
    }

    if (isWritingNfc) {
        nfcWriter(
            uriString,
            { success ->
                isWritingNfc = false
                writeSucceeded = success
            },
            {
                isWritingNfc = false
                showNfcDisabled = true
            },
        )
        MeshtasticDialog(
            onDismiss = { isWritingNfc = false },
            titleRes = Res.string.write_nfc,
            messageRes = Res.string.write_nfc_text,
            dismissTextRes = Res.string.cancel,
        )
    }

    writeSucceeded?.let { success ->
        MeshtasticDialog(
            onDismiss = { writeSucceeded = null },
            titleRes = Res.string.write_nfc,
            messageRes = if (success) Res.string.write_nfc_success else Res.string.write_nfc_failed,
            messageColor = if (success) SemanticColors.Success else MaterialTheme.colorScheme.error,
            onConfirm = { writeSucceeded = null },
            confirmTextRes = Res.string.okay,
        )
    }

    if (showNfcDisabled) {
        NfcDisabledDialog(titleRes = Res.string.write_nfc, onDismiss = { showNfcDisabled = false })
    }

    MeshtasticDialog(
        onDismiss = onDismiss,
        title = title,
        confirmText = stringResource(Res.string.okay),
        onConfirm = onDismiss,
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                subtitle?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                }

                Image(
                    painter = qrPainter,
                    contentDescription = stringResource(Res.string.qr_code),
                    modifier = Modifier.size(QR_DISPLAY_SIZE.dp),
                    contentScale = ContentScale.Fit,
                )

                Text(
                    text = stringResource(Res.string.share_qr_subtext),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uriString,
                        modifier = Modifier.weight(1f).semantics { isSensitiveData = true },
                        style = MaterialTheme.typography.bodySmall,
                        overflow = TextOverflow.Visible,
                        softWrap = true,
                    )
                    // Copy stays an icon: it is the secondary of the three, and a tooltip carries the
                    // label on desktop, where standards section 4 asks for one on an icon-only control.
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = { PlainTooltip { Text(stringResource(Res.string.copy)) } },
                        state = rememberTooltipState(),
                    ) {
                        IconButton(
                            onClick = {
                                // The channel URL embeds channel PSKs, so mark the clip as sensitive.
                                coroutineScope.launch {
                                    clipboardManager.setClipEntry(createClipEntry(uriString, sensitive = true))
                                }
                            },
                        ) {
                            Icon(
                                imageVector = MeshtasticIcons.Copy,
                                contentDescription = stringResource(Res.string.copy),
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { shareText(uriString, shareSubject) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Icon(imageVector = MeshtasticIcons.Share, contentDescription = null)
                    Text(text = stringResource(Res.string.share_action), modifier = Modifier.padding(start = 8.dp))
                }

                if (nfcSupported) {
                    OutlinedButton(
                        onClick = { isWritingNfc = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Icon(imageVector = MeshtasticIcons.Nfc, contentDescription = null)
                        Text(text = stringResource(Res.string.write_nfc), modifier = Modifier.padding(start = 8.dp))
                    }
                }

                if (nfcSupported || hceSupported) {
                    Text(
                        text =
                        listOfNotNull(
                            stringResource(Res.string.write_nfc_subtext).takeIf { nfcSupported },
                            stringResource(Res.string.share_by_tap_subtext).takeIf { hceSupported },
                        )
                            .joinToString(" "),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
