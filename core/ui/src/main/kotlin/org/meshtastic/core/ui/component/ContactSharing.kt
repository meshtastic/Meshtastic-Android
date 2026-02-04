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

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.qr_code
import org.meshtastic.core.strings.scan_qr_code
import org.meshtastic.core.strings.share_contact
import org.meshtastic.core.ui.R
import org.meshtastic.core.ui.share.SharedContactDialog
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import java.net.MalformedURLException

/**
 * Composable FloatingActionButton to initiate scanning a QR code for adding a contact. Handles camera permission
 * requests using Accompanist Permissions.
 *
 * @param modifier Modifier for this composable.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun AddContactFAB(
    sharedContact: SharedContact?,
    modifier: Modifier = Modifier,
    onSharedContactRequested: (SharedContact?) -> Unit,
) {
    val barcodeLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val uri = result.contents.toUri()
                val sharedContact =
                    try {
                        uri.toSharedContact()
                    } catch (ex: MalformedURLException) {
                        Logger.e { "URL was malformed: ${ex.message}" }
                        null
                    }
                if (sharedContact != null) {
                    onSharedContactRequested(sharedContact)
                }
            }
        }

    sharedContact?.let { SharedContactDialog(sharedContact = it, onDismiss = { onSharedContactRequested(null) }) }

    fun zxingScan() {
        Logger.d { "Starting zxing QR code scanner" }
        val zxingScan = ScanOptions()
        zxingScan.setCameraId(CAMERA_ID)
        zxingScan.setPrompt("")
        zxingScan.setBeepEnabled(false)
        zxingScan.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        barcodeLauncher.launch(zxingScan)
    }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(cameraPermissionState.status) {
        if (cameraPermissionState.status.isGranted) {
            Logger.d { "Camera permission granted" }
        } else {
            Logger.d { "Camera permission denied" }
        }
    }

    FloatingActionButton(
        modifier = modifier,
        onClick = {
            if (cameraPermissionState.status.isGranted) {
                zxingScan()
            } else {
                cameraPermissionState.launchPermissionRequest()
            }
        },
    ) {
        Icon(imageVector = Icons.TwoTone.QrCodeScanner, contentDescription = stringResource(Res.string.scan_qr_code))
    }
}

@Composable
private fun QrCodeImage(uri: Uri, modifier: Modifier = Modifier) = Image(
    painter = uri.qrCode?.let { BitmapPainter(it.asImageBitmap()) } ?: painterResource(id = R.drawable.qrcode),
    contentDescription = stringResource(Res.string.qr_code),
    modifier = modifier,
    contentScale = ContentScale.Inside,
)

@Composable
private fun SharedContact(contactUri: Uri) {
    Column {
        QrCodeImage(uri = contactUri, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = contactUri.toString(), modifier = Modifier.weight(1f))
            CopyIconButton(valueToCopy = contactUri.toString(), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * Displays a dialog with the contact's information as a QR code and URI.
 *
 * @param contact The node representing the contact to share. Null if no contact is selected.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun SharedContactDialog(contact: Node?, onDismiss: () -> Unit) {
    if (contact == null) return
    val sharedContact = SharedContact(user = contact.user, node_num = contact.num)
    val uri = sharedContact.getSharedContactUrl()
    SimpleAlertDialog(
        title = Res.string.share_contact,
        text = {
            Column {
                Text(contact.user.long_name)
                SharedContact(contactUri = uri)
            }
        },
        onDismiss = onDismiss,
    )
}

@Preview
@Composable
private fun ShareContactPreview() {
    SharedContact(contactUri = "https://example.com".toUri())
}

/** Bitmap representation of the Uri as a QR code, or null if generation fails. */
val Uri.qrCode: Bitmap?
    get() =
        try {
            val multiFormatWriter = MultiFormatWriter()
            val bitMatrix =
                multiFormatWriter.encode(this.toString(), BarcodeFormat.QR_CODE, BARCODE_PIXEL_SIZE, BARCODE_PIXEL_SIZE)
            val barcodeEncoder = BarcodeEncoder()
            barcodeEncoder.createBitmap(bitMatrix)
        } catch (ex: WriterException) {
            Logger.e { "URL was too complex to render as barcode: ${ex.message}" }
            null
        }

private const val BARCODE_PIXEL_SIZE = 960
private const val MESHTASTIC_HOST = "meshtastic.org"
private const val CONTACT_SHARE_PATH = "/v/"

/** Prefix for Meshtastic contact sharing URLs. */
internal const val URL_PREFIX = "https://$MESHTASTIC_HOST$CONTACT_SHARE_PATH#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING
private const val CAMERA_ID = 0

@Suppress("MagicNumber")
@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): SharedContact {
    if (fragment.isNullOrBlank() || !host.equals(MESHTASTIC_HOST, true) || !path.equals(CONTACT_SHARE_PATH, true)) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }
    return SharedContact.ADAPTER.decode(Base64.decode(fragment!!, BASE64FLAGS).toByteString())
}

/** Converts a [SharedContact] to its corresponding URI representation. */
fun SharedContact.getSharedContactUrl(): Uri {
    val bytes = SharedContact.ADAPTER.encode(this)
    val enc = Base64.encodeToString(bytes, BASE64FLAGS)
    return "$URL_PREFIX$enc".toUri()
}

/** Compares two [User] objects and returns a string detailing the differences. */
fun compareUsers(oldUser: User, newUser: User): String {
    val changes = mutableListOf<String>()

    if (oldUser.id != newUser.id) changes.add("id: ${oldUser.id} -> ${newUser.id}")
    if (oldUser.long_name != newUser.long_name) changes.add("long_name: ${oldUser.long_name} -> ${newUser.long_name}")
    if (oldUser.short_name != newUser.short_name) {
        changes.add("short_name: ${oldUser.short_name} -> ${newUser.short_name}")
    }
    if (oldUser.macaddr != newUser.macaddr) {
        changes.add("macaddr: ${oldUser.macaddr?.base64()} -> ${newUser.macaddr?.base64()}")
    }
    if (oldUser.hw_model != newUser.hw_model) changes.add("hw_model: ${oldUser.hw_model} -> ${newUser.hw_model}")
    if (oldUser.is_licensed != newUser.is_licensed) {
        changes.add("is_licensed: ${oldUser.is_licensed} -> ${newUser.is_licensed}")
    }
    if (oldUser.role != newUser.role) changes.add("role: ${oldUser.role} -> ${newUser.role}")
    if (oldUser.public_key != newUser.public_key) {
        changes.add("public_key: ${oldUser.public_key?.base64()} -> ${newUser.public_key?.base64()}")
    }

    return if (changes.isEmpty()) {
        "No changes detected."
    } else {
        "Changes:\n" + changes.joinToString("\n")
    }
}

/** Converts a [User] object to a string representation of its fields and values. */
fun userFieldsToString(user: User): String {
    val fieldLines = mutableListOf<String>()

    fieldLines.add("id: ${user.id}")
    fieldLines.add("long_name: ${user.long_name}")
    fieldLines.add("short_name: ${user.short_name}")
    fieldLines.add("macaddr: ${user.macaddr?.base64()}")
    fieldLines.add("hw_model: ${user.hw_model}")
    fieldLines.add("is_licensed: ${user.is_licensed}")
    fieldLines.add("role: ${user.role}")
    fieldLines.add("public_key: ${user.public_key?.base64()}")

    return fieldLines.joinToString("\n")
}

private fun ByteString.base64(): String = Base64.encodeToString(this.toByteArray(), Base64.DEFAULT).trim()
