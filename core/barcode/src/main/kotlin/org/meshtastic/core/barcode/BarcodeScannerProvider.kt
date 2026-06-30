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
package org.meshtastic.core.barcode

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.camera_permission
import org.meshtastic.core.resources.camera_permission_rationale
import org.meshtastic.core.resources.close
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.util.BarcodeScanner
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberCameraPermissionState
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun rememberBarcodeScanner(onResult: (String?) -> Unit): BarcodeScanner {
    var showDialog by remember { mutableStateOf(false) }
    var pendingScan by remember { mutableStateOf(false) }
    var showPermissionRecovery by remember { mutableStateOf(false) }
    val cameraPermission = rememberCameraPermissionState()
    val currentStatus = rememberUpdatedState(cameraPermission.status)

    LaunchedEffect(cameraPermission.status) {
        when {
            // A grant arrived for a scan the user asked for — either the pending request or the recovery card's
            // "Grant"/"Open settings" round-trip. Open the scanner and clear both pending flags.
            cameraPermission.isGranted && (pendingScan || showPermissionRecovery) -> {
                showDialog = true
                pendingScan = false
                showPermissionRecovery = false
            }

            // The pending request completed without a grant — surface a recovery card instead of failing silently.
            pendingScan && cameraPermission.status != PermissionStatus.NOT_REQUESTED -> {
                showPermissionRecovery = true
                pendingScan = false
            }
        }
    }

    if (showDialog) {
        BarcodeScannerDialog(
            onResult = {
                showDialog = false
                onResult(it)
            },
        )
    }

    if (showPermissionRecovery) {
        Dialog(onDismissRequest = { showPermissionRecovery = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Heading gives screen readers context for the standalone dialog (unlike the in-sheet Compass
                    // card).
                    Text(
                        text = stringResource(Res.string.camera_permission),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    PermissionRecoveryCard(
                        state = cameraPermission,
                        rationale = stringResource(Res.string.camera_permission_rationale),
                    )
                }
            }
        }
    }

    return remember {
        object : BarcodeScanner {
            override fun startScan() {
                when (currentStatus.value) {
                    PermissionStatus.GRANTED -> showDialog = true

                    PermissionStatus.PERMANENTLY_DENIED -> showPermissionRecovery = true

                    else -> {
                        pendingScan = true
                        cameraPermission.request()
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeScannerDialog(onResult: (String?) -> Unit) {
    var isCameraReady by remember { mutableStateOf(false) }
    val resultGate = remember { SingleScanResultGate() }
    val currentOnResult by rememberUpdatedState(onResult)
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    fun deliverResult(result: String?) {
        resultGate.tryDeliver(result) { value -> mainExecutor.execute { currentOnResult(value) } }
    }

    Dialog(onDismissRequest = { deliverResult(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScannerView(onResult = { deliverResult(it) }, onCameraReady = { isCameraReady = it })
            if (isCameraReady) {
                ScannerReticule()
            }
            IconButton(
                onClick = { deliverResult(null) },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            ) {
                Icon(
                    imageVector = MeshtasticIcons.Close,
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
@Composable
private fun ScannerView(onResult: (String) -> Unit, onCameraReady: (Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Dispatchers.Default.asExecutor() }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val disposed = remember { AtomicBoolean(false) }
    val boundCameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val boundPreview = remember { mutableStateOf<Preview?>(null) }
    val boundImageAnalysis = remember { mutableStateOf<ImageAnalysis?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(Unit) {
        disposed.set(false)
        onDispose {
            disposed.set(true)
            boundImageAnalysis.value?.clearAnalyzer()
            val provider = boundCameraProvider.value
            val preview = boundPreview.value
            val imageAnalysis = boundImageAnalysis.value
            if (provider != null && preview != null && imageAnalysis != null) {
                try {
                    provider.unbind(preview, imageAnalysis)
                } catch (exc: IllegalStateException) {
                    Logger.e(exc) { "Camera cleanup failed" }
                } catch (exc: IllegalArgumentException) {
                    Logger.e(exc) { "Camera cleanup failed" }
                }
            }
            currentOnCameraReady(false)
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                if (!disposed.get()) {
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider { request ->
                        if (!disposed.get()) {
                            surfaceRequest = request
                            currentOnCameraReady(true)
                        }
                    }

                    val imageAnalysis =
                        ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor, createBarcodeAnalyzer { currentOnResult(it) })
                            }

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                        boundCameraProvider.value = cameraProvider
                        boundPreview.value = preview
                        boundImageAnalysis.value = imageAnalysis
                    } catch (exc: IllegalStateException) {
                        imageAnalysis.clearAnalyzer()
                        Logger.e(exc) { "Use case binding failed" }
                    } catch (exc: IllegalArgumentException) {
                        imageAnalysis.clearAnalyzer()
                        Logger.e(exc) { "Use case binding failed" }
                    } catch (exc: UnsupportedOperationException) {
                        imageAnalysis.clearAnalyzer()
                        Logger.e(exc) { "Use case binding failed" }
                    }
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
}
