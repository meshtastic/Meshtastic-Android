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
package org.meshtastic.core.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.barcode.rememberBarcodeScanner
import org.meshtastic.core.nfc.NfcScannerEffect
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.cancel
import org.meshtastic.core.strings.import_label
import org.meshtastic.core.strings.input_channel_url
import org.meshtastic.core.strings.input_shared_contact_url
import org.meshtastic.core.strings.nfc_disabled
import org.meshtastic.core.strings.okay
import org.meshtastic.core.strings.open_settings
import org.meshtastic.core.strings.scan_channels_nfc
import org.meshtastic.core.strings.scan_channels_qr
import org.meshtastic.core.strings.scan_nfc
import org.meshtastic.core.strings.scan_nfc_text
import org.meshtastic.core.strings.scan_shared_contact_nfc
import org.meshtastic.core.strings.scan_shared_contact_qr
import org.meshtastic.core.strings.share_channels_qr
import org.meshtastic.core.strings.url
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.QrCode2
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.openNfcSettings
import org.meshtastic.proto.SharedContact

/**
 * Unified Floating Action Button for importing Meshtastic data (Contacts, Channels, etc.) via NFC, QR, or URL. Handles
 * the [SharedContactImportDialog] if a contact is pending import.
 *
 * @param onImport Callback when a valid Meshtastic URI is scanned or input.
 * @param modifier Modifier for this composable.
 * @param sharedContact Optional pending [SharedContact] to display an import dialog for.
 * @param onDismissSharedContact Callback to clear the pending shared contact.
 * @param onShareChannels Optional callback to trigger sharing channels.
 * @param isContactContext Hint to customize UI strings for contact importing context.
 * @param testTag Optional test tag for UI testing.
 */
@Suppress("LongMethod")
@Composable
fun MeshtasticImportFAB(
    onImport: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    sharedContact: SharedContact? = null,
    onDismissSharedContact: () -> Unit = {},
    onShareChannels: (() -> Unit)? = null,
    isContactContext: Boolean = true,
    testTag: String? = null,
) {
    sharedContact?.let { SharedContactImportDialog(sharedContact = it, onDismiss = onDismissSharedContact) }

    var expanded by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var isNfcScanning by remember { mutableStateOf(false) }
    var showNfcDisabledDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val barcodeScanner = rememberBarcodeScanner(onResult = { contents -> contents?.toUri()?.let { onImport(it) } })

    if (isNfcScanning) {
        NfcScannerEffect(
            onResult = { contents ->
                contents?.toUri()?.let {
                    onImport(it)
                    isNfcScanning = false
                }
            },
            onNfcDisabled = {
                isNfcScanning = false
                showNfcDisabledDialog = true
            },
        )
        NfcScanningDialog(onDismiss = { isNfcScanning = false })
    }

    if (showNfcDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showNfcDisabledDialog = false },
            title = { Text(stringResource(Res.string.scan_nfc)) },
            text = { Text(stringResource(Res.string.nfc_disabled)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        context.openNfcSettings()
                        showNfcDisabledDialog = false
                    },
                ) {
                    Text(stringResource(Res.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNfcDisabledDialog = false }) { Text(stringResource(Res.string.cancel)) }
            },
        )
    }

    if (showUrlDialog) {
        InputUrlDialog(
            title =
            stringResource(
                if (isContactContext) Res.string.input_shared_contact_url else Res.string.input_channel_url,
            ),
            onDismiss = { showUrlDialog = false },
            onConfirm = { contents ->
                onImport(contents.toUri())
                showUrlDialog = false
            },
        )
    }

    val items =
        mutableListOf(
            MenuFABItem(
                label =
                stringResource(
                    if (isContactContext) Res.string.scan_shared_contact_nfc else Res.string.scan_channels_nfc,
                ),
                icon = Icons.Rounded.Nfc,
                onClick = { isNfcScanning = true },
            ),
            MenuFABItem(
                label =
                stringResource(
                    if (isContactContext) Res.string.scan_shared_contact_qr else Res.string.scan_channels_qr,
                ),
                icon = Icons.TwoTone.QrCodeScanner,
                onClick = { barcodeScanner.startScan() },
            ),
            MenuFABItem(
                label =
                stringResource(
                    if (isContactContext) Res.string.input_shared_contact_url else Res.string.input_channel_url,
                ),
                icon = Icons.Rounded.Link,
                onClick = { showUrlDialog = true },
            ),
        )

    onShareChannels?.let {
        items.add(
            MenuFABItem(
                label = stringResource(Res.string.share_channels_qr),
                icon = MeshtasticIcons.QrCode2,
                onClick = it,
            ),
        )
    }

    MenuFAB(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        modifier = modifier.padding(bottom = 16.dp),
        contentDescription = stringResource(Res.string.import_label),
        testTag = testTag,
    )
}

@Composable
private fun NfcScanningDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.scan_nfc)) },
        text = { Text(stringResource(Res.string.scan_nfc_text)) },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Composable
private fun InputUrlDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var urlText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text(stringResource(Res.string.url)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(urlText) }) { Text(stringResource(Res.string.okay)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

@Preview(showBackground = true, name = "Contact Context")
@Composable
fun PreviewImportFABContact() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            MeshtasticImportFAB(onImport = {}, modifier = Modifier.align(Alignment.BottomEnd), isContactContext = true)
        }
    }
}

@Preview(showBackground = true, name = "Channel Context with Sharing")
@Composable
fun PreviewImportFABChannel() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            MeshtasticImportFAB(
                onImport = {},
                onShareChannels = {},
                modifier = Modifier.align(Alignment.BottomEnd),
                isContactContext = false,
            )
        }
    }
}
