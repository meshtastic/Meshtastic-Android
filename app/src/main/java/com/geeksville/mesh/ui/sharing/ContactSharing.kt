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

package com.geeksville.mesh.ui.sharing

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.model.DeviceVersion
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.common.components.CopyIconButton
import com.geeksville.mesh.ui.common.components.SimpleAlertDialog
import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.MalformedURLException

@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun AddContactFAB(
    modifier: Modifier = Modifier,
    model: UIViewModel = hiltViewModel(),
    onSharedContactImport: (AdminProtos.SharedContact) -> Unit = {},
) {
    val context = LocalContext.current
    val contactToImport: AdminProtos.SharedContact? by model.sharedContactRequested.collectAsStateWithLifecycle(null)

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val uri = result.contents.toUri()
            val sharedContact = try {
                uri.toSharedContact()
            } catch (ex: MalformedURLException) {
                errormsg("URL was malformed: ${ex.message}")
                null
            }
            if (sharedContact != null) {
                model.setSharedContactRequested(sharedContact)
            }
        }
    }

    if (contactToImport != null) {
        val nodeNum = contactToImport?.nodeNum
        val nodes by model.unfilteredNodeList.collectAsState()
        val node = nodes.find { it.num == nodeNum }
        SimpleAlertDialog(
            title = R.string.import_shared_contact,
            text = {
                Column {
                    if (node != null) {
                        Text(
                            text = stringResource(
                                R.string.import_known_shared_contact_text
                            )
                        )
                        if (node.user.publicKey.size() > 0 && node.user.publicKey != contactToImport?.user?.publicKey) {
                            Text(
                                text = stringResource(
                                    R.string.public_key_changed
                                ),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        HorizontalDivider()
                        Text(
                            text = compareUsers(node.user, contactToImport!!.user)
                        )
                    } else {
                        Text(
                            text = userFieldsToString(contactToImport!!.user)
                        )
                    }
                }
            },
            dismissText = stringResource(R.string.cancel),
            onDismiss = {
                model.setSharedContactRequested(null)
            },
            confirmText = stringResource(R.string.import_label),
            onConfirm = {
                onSharedContactImport(contactToImport!!)
                model.setSharedContactRequested(null)
            }
        )
    }

    fun zxingScan() {
        debug("Starting zxing QR code scanner")
        val zxingScan = ScanOptions()
        zxingScan.setCameraId(CAMERA_ID)
        zxingScan.setPrompt("")
        zxingScan.setBeepEnabled(false)
        zxingScan.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        barcodeLauncher.launch(zxingScan)
    }

    val requestPermissionAndScanLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) zxingScan()
        }

    var showPermissionRationale by remember { mutableStateOf(false) }
    if (showPermissionRationale) {
        SimpleAlertDialog(
            title = R.string.camera_required,
            text = R.string.why_camera_required,
            onDismiss = {
                debug("Camera permission denied")
                showPermissionRationale = false
            },
            onConfirm = {
                requestPermissionAndScanLauncher.launch(context.getCameraPermissions())
                showPermissionRationale = false
            }
        )
    }
    fun requestPermissionAndScan() {
        showPermissionRationale = true
    }

    FloatingActionButton(
        onClick = {
            if (context.getCameraPermissions().all {
                    context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
                }
            ) {
                zxingScan()
            } else {
                requestPermissionAndScan()
            }
        },
        modifier = modifier.padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.TwoTone.QrCodeScanner,
            contentDescription = stringResource(R.string.scan_qr_code),
        )
    }
}

@Composable
private fun QrCodeImage(
    uri: Uri,
    modifier: Modifier = Modifier,
) = Image(
    painter = uri.qrCode
        ?.let { BitmapPainter(it.asImageBitmap()) }
        ?: painterResource(id = R.drawable.qrcode),
    contentDescription = stringResource(R.string.qr_code),
    modifier = modifier,
    contentScale = ContentScale.Inside,
)

@Composable
private fun SharedContact(
    contactUri: Uri,
) {
    Column {
        QrCodeImage(
            uri = contactUri,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = contactUri.toString(),
                modifier = Modifier
                    .weight(1f)
            )
            CopyIconButton(
                valueToCopy = contactUri.toString(),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun SharedContactDialog(
    contact: Node?,
    onDismiss: () -> Unit,
) {
    if (contact == null) return
    val sharedContact =
        AdminProtos.SharedContact.newBuilder().setUser(contact.user).setNodeNum(contact.num).build()
    val uri = sharedContact.getSharedContactUrl()
    SimpleAlertDialog(
        title = R.string.share_contact,
        text = {
            Column {
                Text(contact.user.longName)
                SharedContact(
                    contactUri = uri,
                )
            }
        },
        onDismiss = onDismiss
    )
}

@Preview
@Composable
private fun ShareContactPreview() {
    SharedContact(
        contactUri = "https://example.com".toUri(),
    )
}

val Uri.qrCode: Bitmap?
    get() = try {
        val multiFormatWriter = MultiFormatWriter()
        val bitMatrix =
            multiFormatWriter.encode(
                this.toString(),
                BarcodeFormat.QR_CODE,
                BARCODE_PIXEL_SIZE,
                BARCODE_PIXEL_SIZE
            )
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (ex: WriterException) {
        errormsg("URL was too complex to render as barcode: ${ex.message}")
        null
    }

private const val REQUIRED_MIN_FIRMWARE = "2.6.8"
private const val BARCODE_PIXEL_SIZE = 960
private const val MESHTASTIC_HOST = "meshtastic.org"
private const val CONTACT_SHARE_PATH = "/v/"
internal const val URL_PREFIX = "https://$MESHTASTIC_HOST$CONTACT_SHARE_PATH#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING
private const val CAMERA_ID = 0

fun DeviceVersion.supportsQrCodeSharing(): Boolean =
    this >= DeviceVersion(REQUIRED_MIN_FIRMWARE)

@Suppress("MagicNumber")
@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): AdminProtos.SharedContact {
    if (fragment.isNullOrBlank() ||
        !host.equals(MESHTASTIC_HOST, true) ||
        !path.equals(CONTACT_SHARE_PATH, true)
    ) {
        throw MalformedURLException("Not a valid Meshtastic URL: ${toString().take(40)}")
    }
        val url = AdminProtos.SharedContact.parseFrom(Base64.decode(fragment!!, BASE64FLAGS))
        return url.toBuilder().build()
    }

fun AdminProtos.SharedContact.getSharedContactUrl(): Uri {
    val bytes = this.toByteArray() ?: ByteArray(0)
    val enc = Base64.encodeToString(bytes, BASE64FLAGS)
    return "$URL_PREFIX$enc".toUri()
}

fun compareUsers(oldUser: MeshProtos.User, newUser: MeshProtos.User): String {
    val changes = mutableListOf<String>()

    // Iterate over all fields in the User message descriptor
    for (fieldDescriptor: Descriptors.FieldDescriptor in MeshProtos.User.getDescriptor().fields) {
        val fieldName = fieldDescriptor.name
        val oldValue =
            if (oldUser.hasField(fieldDescriptor)) oldUser.getField(fieldDescriptor) else null
        val newValue =
            if (newUser.hasField(fieldDescriptor)) newUser.getField(fieldDescriptor) else null

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

fun userFieldsToString(user: MeshProtos.User): String {
    val fieldLines = mutableListOf<String>()

    for (fieldDescriptor: Descriptors.FieldDescriptor in MeshProtos.User.getDescriptor().fields) {
        val fieldName = fieldDescriptor.name
        if (user.hasField(fieldDescriptor)) {
            val value = user.getField(fieldDescriptor)
            val valueString =
                valueToString(value, fieldDescriptor) // Using the helper from previous example
            fieldLines.add("$fieldName: $valueString")
        } else if (fieldDescriptor.isRepeated || fieldDescriptor.hasDefaultValue() || fieldDescriptor.isOptional) {
            val defaultValue = fieldDescriptor.defaultValue
            val valueString = if (fieldDescriptor.isRepeated) {
                "[]" // Empty list
            } else if (user.hasField(fieldDescriptor)) {
                valueToString(
                user.getField(fieldDescriptor),
                fieldDescriptor
            )
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
