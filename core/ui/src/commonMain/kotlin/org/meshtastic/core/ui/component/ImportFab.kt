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
package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.import_export_label
import org.meshtastic.core.resources.import_label
import org.meshtastic.core.resources.input_channel_url
import org.meshtastic.core.resources.input_connect_code_url
import org.meshtastic.core.resources.input_shared_contact_url
import org.meshtastic.core.resources.okay
import org.meshtastic.core.resources.scan_channels_nfc
import org.meshtastic.core.resources.scan_channels_qr
import org.meshtastic.core.resources.scan_connect_code
import org.meshtastic.core.resources.scan_connect_code_nfc
import org.meshtastic.core.resources.scan_connect_code_qr
import org.meshtastic.core.resources.scan_nfc
import org.meshtastic.core.resources.scan_nfc_text
import org.meshtastic.core.resources.scan_shared_contact_nfc
import org.meshtastic.core.resources.scan_shared_contact_qr
import org.meshtastic.core.resources.share_channels
import org.meshtastic.core.resources.share_connected_node
import org.meshtastic.core.resources.url
import org.meshtastic.core.ui.icon.LinkIcon
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Nfc
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.QrCode2
import org.meshtastic.core.ui.icon.QrCodeScanner
import org.meshtastic.core.ui.share.SharedContactImportDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.util.LocalBarcodeScannerProvider
import org.meshtastic.core.ui.util.LocalBarcodeScannerSupported
import org.meshtastic.core.ui.util.LocalNfcScannerProvider
import org.meshtastic.core.ui.util.LocalNfcScannerSupported
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
 * @param onShareContact Optional callback to trigger sharing the connected node as a contact.
 * @param isContactContext Hint to customize UI strings for contact importing context.
 * @param importKind What is being imported, which picks the UI strings. Derived from [isContactContext] by default.
 * @param testTag Optional test tag for UI testing.
 * @param importDialog Composable to display the import dialog. Defaults to [SharedContactImportDialog].
 */
@Suppress("LongMethod")
@Composable
fun MeshtasticImportFAB(
    onImport: (String) -> Unit,
    modifier: Modifier = Modifier,
    sharedContact: SharedContact? = null,
    onDismissSharedContact: () -> Unit = {},
    onShareChannels: (() -> Unit)? = null,
    onShareContact: (() -> Unit)? = null,
    isContactContext: Boolean = true,
    importKind: ImportKind = if (isContactContext) ImportKind.Contact else ImportKind.Channel,
    testTag: String? = null,
    importDialog: @Composable (SharedContact, () -> Unit) -> Unit = { contact, dismiss ->
        SharedContactImportDialog(sharedContact = contact, onDismiss = dismiss)
    },
) {
    sharedContact?.let { importDialog(it, onDismissSharedContact) }

    var expanded by rememberSaveable { mutableStateOf(false) }
    var showUrlDialog by rememberSaveable { mutableStateOf(false) }
    var isNfcScanning by rememberSaveable { mutableStateOf(false) }
    var showNfcDisabledDialog by rememberSaveable { mutableStateOf(false) }

    val barcodeScanner = LocalBarcodeScannerProvider.current { contents ->
        normalizeImportContents(contents)?.let(onImport)
    }
    val nfcScanner = LocalNfcScannerProvider.current
    val isNfcSupported = LocalNfcScannerSupported.current
    val isBarcodeSupported = LocalBarcodeScannerSupported.current

    if (isNfcScanning) {
        nfcScanner(
            { contents ->
                isNfcScanning = false
                normalizeImportContents(contents)?.let(onImport)
            },
            {
                isNfcScanning = false
                showNfcDisabledDialog = true
            },
        )
        NfcScanningDialog(onDismiss = { isNfcScanning = false })
    }

    if (showNfcDisabledDialog) {
        NfcDisabledDialog(titleRes = Res.string.scan_nfc, onDismiss = { showNfcDisabledDialog = false })
    }

    if (showUrlDialog) {
        InputUrlDialog(
            title = stringResource(importKind.inputUrlRes),
            onDismiss = { showUrlDialog = false },
            onConfirm = { contents ->
                normalizeImportContents(contents)?.let {
                    onImport(it)
                    showUrlDialog = false
                }
            },
        )
    }

    val items = mutableListOf<MenuFABItem>()

    if (isNfcSupported) {
        items.add(
            MenuFABItem(
                label = stringResource(importKind.scanNfcRes),
                icon = MeshtasticIcons.Nfc,
                onClick = { isNfcScanning = true },
                testTag = "nfc_import",
            ),
        )
    }

    if (isBarcodeSupported) {
        items.add(
            MenuFABItem(
                label = stringResource(importKind.scanQrRes),
                icon = MeshtasticIcons.QrCodeScanner,
                onClick = { barcodeScanner.startScan() },
                testTag = "qr_import",
            ),
        )
    }

    items.add(
        MenuFABItem(
            label = stringResource(importKind.inputUrlRes),
            icon = MeshtasticIcons.LinkIcon,
            onClick = { showUrlDialog = true },
            testTag = "url_import",
        ),
    )

    onShareChannels?.let {
        items.add(
            MenuFABItem(
                label = stringResource(Res.string.share_channels),
                icon = MeshtasticIcons.QrCode2,
                onClick = it,
                testTag = "share_channels",
            ),
        )
    }

    onShareContact?.let {
        items.add(
            MenuFABItem(
                label = stringResource(Res.string.share_connected_node),
                icon = MeshtasticIcons.Person,
                onClick = it,
                testTag = "share_contact",
            ),
        )
    }

    MenuFAB(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        items = items,
        modifier = modifier.padding(bottom = 16.dp),
        contentDescription =
        stringResource(
            importFabContentDescriptionRes(
                importKind = importKind,
                canScan = isNfcSupported || isBarcodeSupported,
                canShare = onShareChannels != null || onShareContact != null,
            ),
        ),
        testTag = testTag,
    )
}

internal fun normalizeImportContents(contents: String?): String? = contents?.trim()?.takeIf { it.isNotEmpty() }

private fun importFabContentDescriptionRes(importKind: ImportKind, canScan: Boolean, canShare: Boolean) = when {
    canShare -> Res.string.import_export_label
    importKind == ImportKind.ConnectCode && canScan -> Res.string.scan_connect_code
    else -> Res.string.import_label
}

/** What [MeshtasticImportFAB] imports, and the strings its menu shows for it. */
enum class ImportKind(
    internal val scanNfcRes: StringResource,
    internal val scanQrRes: StringResource,
    internal val inputUrlRes: StringResource,
) {
    Contact(Res.string.scan_shared_contact_nfc, Res.string.scan_shared_contact_qr, Res.string.input_shared_contact_url),
    Channel(Res.string.scan_channels_nfc, Res.string.scan_channels_qr, Res.string.input_channel_url),
    ConnectCode(Res.string.scan_connect_code_nfc, Res.string.scan_connect_code_qr, Res.string.input_connect_code_url),
}

@Composable
private fun NfcScanningDialog(onDismiss: () -> Unit) {
    MeshtasticDialog(
        onDismiss = onDismiss,
        titleRes = Res.string.scan_nfc,
        messageRes = Res.string.scan_nfc_text,
        dismissTextRes = Res.string.cancel,
    )
}

@Composable
private fun InputUrlDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var urlText by remember { mutableStateOf("") }
    MeshtasticDialog(
        onDismiss = onDismiss,
        title = title,
        text = {
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text(stringResource(Res.string.url)) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        },
        onConfirm = { onConfirm(urlText) },
        confirmTextRes = Res.string.okay,
        dismissTextRes = Res.string.cancel,
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
