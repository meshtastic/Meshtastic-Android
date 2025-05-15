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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.geeksville.mesh.R
import com.geeksville.mesh.android.BuildUtils.debug
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.android.getCameraPermissions
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("LongMethod", "CyclomaticComplexMethod")
@Composable
fun AddContact(
    viewModel: UIViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val enabled = connectionState == MeshService.ConnectionState.CONNECTED && !viewModel.isManaged

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.requestChannelUrl(result.contents.toUri())
        }
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

    Button(
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        enabled = enabled,
    ) {
        Icon(
            imageVector = Icons.TwoTone.ContentCopy,
            contentDescription = stringResource(R.string.scan_qr_code),
        )
        Text(text = stringResource(R.string.scan_qr_code))
    }
}

@Composable
private fun QrCodeImage(
    enabled: Boolean,
    uri: Uri,
    modifier: Modifier = Modifier,
) = Image(
    painter = uri.qrCode
        ?.let { BitmapPainter(it.asImageBitmap()) }
        ?: painterResource(id = R.drawable.qrcode),
    contentDescription = stringResource(R.string.qr_code),
    modifier = modifier,
    contentScale = ContentScale.Inside,
    alpha = if (enabled) 1.0f else ContentAlpha.disabled,
    // colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
)

@Composable
private fun ShareContact(
    enabled: Boolean,
    contactUri: Uri,
) {
    QrCodeImage(
        enabled = enabled,
        uri = contactUri,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

@RequiresApi(Build.VERSION_CODES.M)
@Preview
@Composable
private fun AddContactPreview() {
    AddContact(
        viewModel = hiltViewModel(),
    )
}

@Preview
@Composable
private fun ShareContactPreview() {
    ShareContact(
        enabled = true,
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
                960,
                960
            )
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (ex: Throwable) {
        errormsg("URL was too complex to render as barcode")
        null
    }
