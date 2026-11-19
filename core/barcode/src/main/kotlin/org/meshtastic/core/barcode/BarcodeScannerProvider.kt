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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.camera_permission
import org.meshtastic.core.resources.camera_permission_rationale
import org.meshtastic.core.resources.camera_unavailable
import org.meshtastic.core.ui.component.BarcodeScannerFrame
import org.meshtastic.core.ui.component.PermissionRecoveryCard
import org.meshtastic.core.ui.util.BarcodeScanner
import org.meshtastic.core.ui.util.PermissionStatus
import org.meshtastic.core.ui.util.rememberCameraPermissionState
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
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
    var hasCameraError by remember { mutableStateOf(false) }
    var scannerAttempt by remember { mutableIntStateOf(0) }
    val resultGate = remember { SingleScanResultGate() }
    val currentOnResult by rememberUpdatedState(onResult)
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    fun deliverResult(result: String?) {
        resultGate.tryDeliver(result) { value -> mainExecutor.execute { currentOnResult(value) } }
    }

    BarcodeScannerFrame(
        onClose = { deliverResult(null) },
        showReticle = isCameraReady,
        errorMessage = if (hasCameraError) stringResource(Res.string.camera_unavailable) else null,
        onRetry = {
            hasCameraError = false
            scannerAttempt++
        },
    ) { _ ->
        key(scannerAttempt) {
            ScannerView(
                onResult = { deliverResult(it) },
                onCameraReady = {
                    isCameraReady = it
                    if (it) hasCameraError = false
                },
                onCameraError = {
                    isCameraReady = false
                    hasCameraError = true
                },
            )
        }
    }
}

@Suppress("LongMethod", "InjectDispatcher") // CameraX analysis wants an Executor; the default pool backs it
@Composable
private fun ScannerView(onResult: (String) -> Unit, onCameraReady: (Boolean) -> Unit, onCameraError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Dispatchers.Default.asExecutor() }
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    val disposed = remember { AtomicBoolean(false) }
    val boundCameraProvider = remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val boundPreview = remember { mutableStateOf<Preview?>(null) }
    val boundImageAnalysis = remember { mutableStateOf<ImageAnalysis?>(null) }
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            surfaceRequest?.willNotProvideSurface()
            surfaceRequest = null
            cleanupCameraUseCases(
                cameraProvider = boundCameraProvider.value,
                preview = boundPreview.value,
                imageAnalysis = boundImageAnalysis.value,
            )
            currentOnCameraReady(false)
        }
    }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener(
            {
                if (disposed.get()) return@addListener

                val cameraProvider = getCameraProvider(cameraProviderFuture)
                if (cameraProvider == null) {
                    if (!disposed.get()) currentOnCameraError()
                    return@addListener
                }

                if (disposed.get()) return@addListener

                val preview = Preview.Builder().build()
                preview.setSurfaceProvider { request ->
                    if (!disposed.get()) {
                        surfaceRequest = request
                        currentOnCameraReady(true)
                    } else {
                        request.willNotProvideSurface()
                    }
                }

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(cameraExecutor, createBarcodeAnalyzer { currentOnResult(it) })
                        }

                if (bindCameraUseCases(cameraProvider, lifecycleOwner, preview, imageAnalysis)) {
                    boundCameraProvider.value = cameraProvider
                    boundPreview.value = preview
                    boundImageAnalysis.value = imageAnalysis
                } else if (!disposed.get()) {
                    currentOnCameraReady(false)
                    currentOnCameraError()
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    surfaceRequest?.let {
        CameraXViewfinder(
            surfaceRequest = it,
            modifier = Modifier.fillMaxSize(),
            isTapToFocusEnabled = true,
            isPinchToZoomEnabled = true,
        )
    }
}

private fun cleanupCameraUseCases(
    cameraProvider: ProcessCameraProvider?,
    preview: Preview?,
    imageAnalysis: ImageAnalysis?,
) {
    imageAnalysis?.clearAnalyzer()
    if (cameraProvider != null && preview != null && imageAnalysis != null) {
        try {
            cameraProvider.unbind(preview, imageAnalysis)
        } catch (exc: IllegalStateException) {
            logCameraFailure(exc, "Camera cleanup failed")
        } catch (exc: IllegalArgumentException) {
            logCameraFailure(exc, "Camera cleanup failed")
        }
    }
}

private fun getCameraProvider(cameraProviderFuture: Future<ProcessCameraProvider>): ProcessCameraProvider? = try {
    cameraProviderFuture.get()
} catch (exc: CancellationException) {
    Logger.e(exc) { "Camera provider request was cancelled" }
    null
} catch (exc: ExecutionException) {
    Logger.e(exc) { "Failed to get camera provider" }
    null
} catch (exc: InterruptedException) {
    Thread.currentThread().interrupt()
    Logger.e(exc) { "Interrupted while getting camera provider" }
    null
}

private fun bindCameraUseCases(
    cameraProvider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    preview: Preview,
    imageAnalysis: ImageAnalysis,
): Boolean = try {
    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
    true
} catch (exc: IllegalStateException) {
    imageAnalysis.handleBindFailure(exc)
} catch (exc: IllegalArgumentException) {
    imageAnalysis.handleBindFailure(exc)
} catch (exc: SecurityException) {
    imageAnalysis.handleBindFailure(exc)
} catch (exc: UnsupportedOperationException) {
    imageAnalysis.handleBindFailure(exc)
}

private fun ImageAnalysis.handleBindFailure(exc: RuntimeException): Boolean {
    clearAnalyzer()
    logCameraFailure(exc, "Use case binding failed")
    return false
}

private fun logCameraFailure(exc: RuntimeException, message: String) {
    Logger.e(exc) { message }
}
