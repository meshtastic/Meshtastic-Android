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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.ui.R
import org.meshtastic.core.ui.share.SharedContactDialog
import org.meshtastic.proto.AdminProtos
import org.meshtastic.proto.MeshProtos
import timber.log.Timber
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
    sharedContact: AdminProtos.SharedContact?,
    modifier: Modifier = Modifier,
    onSharedContactRequested: (AdminProtos.SharedContact?) -> Unit,
) {
    val barcodeLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            if (result.contents != null) {
                val uri = result.contents.toUri()
                val sharedContact =
                    try {
                        uri.toSharedContact()
                    } catch (ex: MalformedURLException) {
                        Timber.e("URL was malformed: ${ex.message}")
                        null
                    }
                if (sharedContact != null) {
                    onSharedContactRequested(sharedContact)
                }
            }
        }

    sharedContact?.let { SharedContactDialog(sharedContact = it, onDismiss = { onSharedContactRequested(null) }) }

    fun zxingScan() {
        Timber.d("Starting zxing QR code scanner")
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
            Timber.d("Camera permission granted")
        } else {
            Timber.d("Camera permission denied")
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
        Icon(
            imageVector = Icons.TwoTone.QrCodeScanner,
            contentDescription = stringResource(org.meshtastic.core.strings.R.string.scan_qr_code),
        )
    }
}

@Composable
private fun QrCodeImage(uri: Uri, modifier: Modifier = Modifier) = Image(
    painter = uri.qrCode?.let { BitmapPainter(it.asImageBitmap()) } ?: painterResource(id = R.drawable.qrcode),
    contentDescription = stringResource(org.meshtastic.core.strings.R.string.qr_code),
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
    val sharedContact = AdminProtos.SharedContact.newBuilder().setUser(contact.user).setNodeNum(contact.num).build()
    val uri = sharedContact.getSharedContactUrl()
    SimpleAlertDialog(
        title = org.meshtastic.core.strings.R.string.share_contact,
        text = {
            Column {
                Text(contact.user.longName)
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
            Timber.e("URL was too complex to render as barcode: ${ex.message}")
            null
        }

private const val REQUIRED_MIN_FIRMWARE = "2.6.8"
private const val BARCODE_PIXEL_SIZE = 960
private const val MESHTASTIC_HOST = "meshtastic.org"
private const val CONTACT_SHARE_PATH = "/v/"

/** Prefix for Meshtastic contact sharing URLs. */
internal const val URL_PREFIX = "https://$MESHTASTIC_HOST$CONTACT_SHARE_PATH#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING
private const val CAMERA_ID = 0

/** Checks if the device firmware version supports QR code sharing. */
fun DeviceVersion.supportsQrCodeSharing(): Boolean = this >= DeviceVersion(REQUIRED_MIN_FIRMWARE)

/**
 * Converts a URI to a [AdminProtos.SharedContact].
 *
 * @throws MalformedURLException if the URI is not a valid Meshtastic contact sharing URL.
 */
@Suppress("MagicNumber")
@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): AdminProtos.SharedContact {
    if (fragment.isNullOrBlank() || !host.equals(MESHTASTIC_HOST, true) || !path.equals(CONTACT_SHARE_PATH, true)) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }
    val url = AdminProtos.SharedContact.parseFrom(Base64.decode(fragment!!, BASE64FLAGS))
    return url.toBuilder().build()
}

/** Converts a [AdminProtos.SharedContact] to its corresponding URI representation. */
fun AdminProtos.SharedContact.getSharedContactUrl(): Uri {
    val bytes = this.toByteArray() ?: ByteArray(0)
    val enc = Base64.encodeToString(bytes, BASE64FLAGS)
    return "$URL_PREFIX$enc".toUri()
}

/** Compares two [MeshProtos.User] objects and returns a string detailing the differences. */
fun compareUsers(oldUser: MeshProtos.User, newUser: MeshProtos.User): String {
    val changes = mutableListOf<String>()

    // Iterate over all fields in the User message descriptor
    for (fieldDescriptor: Descriptors.FieldDescriptor in MeshProtos.User.getDescriptor().fields) {
        val fieldName = fieldDescriptor.name
        val oldValue = if (oldUser.hasField(fieldDescriptor)) oldUser.getField(fieldDescriptor) else null
        val newValue = if (newUser.hasField(fieldDescriptor)) newUser.getField(fieldDescriptor) else null

        if (oldValue != newValue) {
            val oldValueString = valueToString(oldValue, fieldDescriptor)
            val newValueString = valueToString(newValue, fieldDescriptor)
            changes.add("$fieldName: $oldValueString -> $newValueString")
        }
    }

    return if (changes.isEmpty()) {
        "No changes detected."
    } else {
        "Changes:\n" + changes.joinToString("\n")
    }
}

/** Converts a [MeshProtos.User] object to a string representation of its fields and values. */
fun userFieldsToString(user: MeshProtos.User): String {
    val fieldLines = mutableListOf<String>()

    for (fieldDescriptor: Descriptors.FieldDescriptor in MeshProtos.User.getDescriptor().fields) {
        val fieldName = fieldDescriptor.name
        if (user.hasField(fieldDescriptor)) {
            val value = user.getField(fieldDescriptor)
            val valueString = valueToString(value, fieldDescriptor) // Using the helper from previous example
            fieldLines.add("$fieldName: $valueString")
        } else if (fieldDescriptor.isRepeated || fieldDescriptor.hasDefaultValue() || fieldDescriptor.isOptional) {
            val defaultValue = fieldDescriptor.defaultValue
            val valueString =
                if (fieldDescriptor.isRepeated) {
                    "[]" // Empty list
                } else if (user.hasField(fieldDescriptor)) {
                    valueToString(user.getField(fieldDescriptor), fieldDescriptor)
                } else {
                    valueToString(defaultValue, fieldDescriptor)
                }

            fieldLines.add("$fieldName: $valueString")
        }
    }
    return if (fieldLines.isEmpty()) {
        "User object has no fields set."
    } else {
        fieldLines.joinToString("\n")
    }
}

private fun valueToString(value: Any?, fieldDescriptor: Descriptors.FieldDescriptor): String {
    if (value == null) {
        return "null"
    }
    return when (fieldDescriptor.type) {
        Descriptors.FieldDescriptor.Type.BYTES -> {
            // For ByteString, you might want to display it as hex or Base64
            // For simplicity, here we'll just show its size.
            if (value is ByteString) {
                Base64.encodeToString(value.toByteArray(), Base64.DEFAULT).trim()
            } else {
                value.toString().trim()
            }
        }
        // Add more custom formatting for other types if needed
        else -> value.toString().trim()
    }
}
