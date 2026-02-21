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
@file:OptIn(ExperimentalPermissionsApi::class)

package org.meshtastic.core.barcode

import android.Manifest
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.close
import java.util.concurrent.Executors

@Composable
fun rememberBarcodeScanner(onResult: (String?) -> Unit): BarcodeScanner {
    var showDialog by remember { mutableStateOf(false) }
    var pendingScan by remember { mutableStateOf(false) }
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted && pendingScan) {
            showDialog = true
            pendingScan = false
        }
    }

    if (showDialog) {
        BarcodeScannerDialog(
            onResult = {
                showDialog = false
                onResult(it)
            },
            onDismiss = {
                showDialog = false
                onResult(null)
            },
        )
    }

    return remember {
        object : BarcodeScanner {
            override fun startScan() {
                if (cameraPermissionState.status.isGranted) {
                    showDialog = true
                } else {
                    pendingScan = true
                    cameraPermissionState.launchPermissionRequest()
                }
            }
        }
    }
}

@Composable
private fun BarcodeScannerDialog(onResult: (String?) -> Unit, onDismiss: () -> Unit) {
    var isCameraReady by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScannerView(onResult = onResult, onCameraReady = { isCameraReady = it })
            if (isCameraReady) {
                ScannerReticule()
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.close),
                    tint = Color.White,
                )
            }
        }
    }
}

@Suppress("MagicNumber")
@Composable
private fun ScannerReticule() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val reticleSize = width.coerceAtMost(height) * 0.7f
        val left = (width - reticleSize) / 2
        val top = (height - reticleSize) / 2
        val rect = Rect(left, top, left + reticleSize, top + reticleSize)

        // Draw semi-transparent background with a hole
        clipPath(Path().apply { addRect(rect) }, clipOp = ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.6f))
        }

        // Draw reticle corners
        val strokeWidth = 3.dp.toPx()
        val cornerLength = 40.dp.toPx()
        val color = Color.White

        // Corners
        val path =
            Path().apply {
                // Top Left
                moveTo(left, top + cornerLength)
                lineTo(left, top)
                lineTo(left + cornerLength, top)

                // Top Right
                moveTo(left + reticleSize - cornerLength, top)
                lineTo(left + reticleSize, top)
                lineTo(left + reticleSize, top + cornerLength)

                // Bottom Right
                moveTo(left + reticleSize, top + reticleSize - cornerLength)
                lineTo(left + reticleSize, top + reticleSize)
                lineTo(left + reticleSize - cornerLength, top + reticleSize)

                // Bottom Left
                moveTo(left + cornerLength, top + reticleSize)
                lineTo(left, top + reticleSize)
                lineTo(left, top + reticleSize - cornerLength)
            }

        drawPath(path, color, style = Stroke(strokeWidth))
    }
}

@Suppress("LongMethod")
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun ScannerView(onResult: (String?) -> Unit, onCameraReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider { request ->
                    surfaceRequest = request
                    onCameraReady(true)
                }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image =
                                        InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    barcodeScanner
                                        .process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                barcode.rawValue?.let { onResult(it) }
                                            }
                                        }
                                        .addOnFailureListener { Logger.e { "Barcode scanning failed: ${it.message}" } }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                        }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis,
                    )
                } catch (exc: IllegalStateException) {
                    Logger.e(exc) { "Use case binding failed" }
                } catch (exc: IllegalArgumentException) {
                    Logger.e(exc) { "Use case binding failed" }
                } catch (exc: UnsupportedOperationException) {
                    Logger.e(exc) { "Use case binding failed" }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
}
