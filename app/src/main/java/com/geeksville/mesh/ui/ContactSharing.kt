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

package com.geeksville.mesh.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.geeksville.mesh.AdminProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.ui.components.CopyIconButton
import com.geeksville.mesh.ui.components.SimpleAlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.net.MalformedURLException

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun AddContactFAB(
    modifier: Modifier = Modifier.padding(16.dp),
    onSharedContactImport: (AdminProtos.SharedContact) -> Unit = {},
) {
    val context = LocalContext.current
    var contactToImport: AdminProtos.SharedContact? by remember { mutableStateOf(null) }

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
                contactToImport = sharedContact
            }
        }
    }

    if (contactToImport != null) {
        SimpleAlertDialog(
            title = R.string.import_shared_contact,
            text = {
                Text("$contactToImport")
            },
            onDismiss = {
                contactToImport = null
            },
            onConfirm = {
                onSharedContactImport(contactToImport!!)
                contactToImport = null
            }
        )
    }

    fun zxingScan() {
        debug("Starting zxing QR code scanner")
        val zxingScan = ScanOptions()
        zxingScan.setCameraId(0)
        zxingScan.setPrompt("")
        zxingScan.setBeepEnabled(false)
        zxingScan.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        barcodeLauncher.launch(zxingScan)
    }

    val requestPermissionAndScanLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.entries.all { it.value }) zxingScan()
        }

    fun requestPermissionAndScan() {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.camera_required)
            .setMessage(R.string.why_camera_required)
            .setNeutralButton(R.string.cancel) { _, _ ->
                debug("Camera permission denied")
            }
            .setPositiveButton(R.string.accept) { _, _ ->
                requestPermissionAndScanLauncher.launch(context.getCameraPermissions())
            }
            .show()
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
        modifier = modifier
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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

private const val BARCODE_PIXEL_SIZE = 960

private const val MESHTASTIC_HOST = "meshtastic.org"
private const val MESHTASTIC_PATH = "/v/"
internal const val URL_PREFIX = "https://$MESHTASTIC_HOST$MESHTASTIC_PATH#"
private const val BASE64FLAGS = Base64.URL_SAFE + Base64.NO_WRAP + Base64.NO_PADDING

@Throws(MalformedURLException::class)
fun Uri.toSharedContact(): AdminProtos.SharedContact {
    if (fragment.isNullOrBlank() ||
        !host.equals(MESHTASTIC_HOST, true) ||
        !path.equals(MESHTASTIC_PATH, true)
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

