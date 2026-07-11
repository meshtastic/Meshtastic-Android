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
package org.meshtastic.app.map

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.site_planner_estimating
import org.meshtastic.core.resources.site_planner_failed
import org.meshtastic.feature.map.component.SitePlannerParams
import org.meshtastic.feature.map.component.SitePlannerSheet

// The official hosted Site Planner (static PWA on GitHub Pages). The estimate flow loads it headless with
// run=1&bridge=1; the native bridge + full flat query contract it relies on shipped in site-planner #74.
private const val SITE_PLANNER_BASE_URL = "https://site.meshtastic.org"
private const val SITE_PLANNER_TIMEOUT_MS = 45_000L

/**
 * Site Planner coverage-estimate flow: an editable [SitePlannerSheet] pre-filled with [initialParams], then a hidden
 * headless WebView that loads the planner with `run=1&bridge=1`, waits for it to hand back the styled GeoJSON, and
 * imports it via [onImport]. Location shortcuts re-seed the coordinates from the device GPS
 * ([onRequestCurrentLocation], when permission is granted), this node ([onUseNodeLocation]), or the map center
 * ([onUseMapCenter]). Google-flavor affordance; targets the official hosted planner.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("LongMethod", "LambdaParameterInRestartableEffect")
@Composable
fun SitePlannerHost(
    initialParams: SitePlannerParams,
    onDismiss: () -> Unit,
    onImport: (name: String, geoJson: String, latitude: Double, longitude: Double) -> Unit,
    onRequestCurrentLocation: (suspend () -> Pair<Double, Double>?)? = null,
    onUseNodeLocation: (() -> Pair<Double, Double>)? = null,
    onUseMapCenter: (() -> Pair<Double, Double>)? = null,
) {
    var params by remember(initialParams) { mutableStateOf(initialParams) }
    var running by remember { mutableStateOf<SitePlannerParams?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val failedText = stringResource(Res.string.site_planner_failed)
    val estimatingText = stringResource(Res.string.site_planner_estimating)

    val current = running
    if (current == null) {
        SitePlannerSheet(
            initial = params,
            onSubmit = { running = it },
            onDismiss = onDismiss,
            onUseCurrentLocation =
            onRequestCurrentLocation?.let { fetch ->
                {
                    scope.launch {
                        fetch()?.let { (lat, lon) -> params = params.copy(latitude = lat, longitude = lon) }
                    }
                }
            },
            onUseNodeLocation =
            onUseNodeLocation?.let { node ->
                {
                    val (lat, lon) = node()
                    params = params.copy(latitude = lat, longitude = lon)
                }
            },
            onUseMapCenter =
            onUseMapCenter?.let { center ->
                {
                    val (lat, lon) = center()
                    params = params.copy(latitude = lat, longitude = lon)
                }
            },
        )
    } else {
        Dialog(onDismissRequest = { onDismiss() }) {
            // The WebView fills the card and runs the sim; the opaque spinner Surface on top hides it, so the flow
            // reads as "Estimating coverage…" rather than a browser. It must be attached and non-trivially sized —
            // a detached or 0-size WebView can't get a WebGL context, and the planner's autorun waits on map load.
            Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                SitePlannerRunner(
                    url = current.toQueryUrl(SITE_PLANNER_BASE_URL),
                    onResult = { geoJson ->
                        onImport(current.name, geoJson, current.latitude, current.longitude)
                        onDismiss()
                    },
                    onError = { detail ->
                        Logger.withTag("SitePlanner").e { "Coverage estimate failed: $detail" }
                        Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    // Headless: fully transparent so the WebGL first-paint frame never flashes through, but still
                    // attached + sized (280dp) so the sim gets a WebGL context. alpha(0) is a compositor property —
                    // the view stays VISIBLE, so the page's requestAnimationFrame/autorun keep running.
                    modifier = Modifier.matchParentSize().alpha(0f),
                )
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    ) {
                        CircularWavyProgressIndicator()
                        Text(estimatingText)
                        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
                    }
                }
            }
        }
        LaunchedEffect(current) {
            delay(SITE_PLANNER_TIMEOUT_MS)
            Logger.withTag("SitePlanner").w { "Coverage estimate timed out after ${SITE_PLANNER_TIMEOUT_MS}ms" }
            Toast.makeText(context, failedText, Toast.LENGTH_SHORT).show()
            onDismiss()
        }
    }
}

/**
 * Headless WebView that loads [url] (the planner with `run=1&bridge=1`), exposes a `window.__meshtasticNative` bridge,
 * and reports the coverage GeoJSON via [onResult] once the planner posts it. Main-frame load failures go to [onError].
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SitePlannerRunner(
    url: String,
    onResult: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onResultState by rememberUpdatedState(onResult)
    val onErrorState by rememberUpdatedState(onError)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val main = Handler(Looper.getMainLooper())
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT) // no opaque black backing before first paint
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                addJavascriptInterface(
                    object {
                        // Called from the planner's JS thread — hop to main before touching Compose state.
                        @JavascriptInterface fun onCoverage(geoJson: String) = main.post { onResultState(geoJson) }
                    },
                    "__meshtasticNative",
                )
                webViewClient =
                    object : WebViewClient() {
                        // Keep the __meshtasticNative bridge exclusive to the planner's own origin: block any
                        // navigation
                        // elsewhere (redirect/compromise/open-redirect) so foreign content can never reach
                        // onCoverage().
                        // Sub-resource fetches (tiles, XHR) aren't navigations, so this doesn't affect the sim itself.
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val target = request?.url ?: return false
                            val trusted = SITE_PLANNER_BASE_URL.toUri()
                            return target.scheme != trusted.scheme || target.host != trusted.host
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?,
                        ) {
                            // Only a failed main-frame load is fatal; a stray tile/asset 404 is not.
                            if (request?.isForMainFrame == true) {
                                main.post { onErrorState("${error?.errorCode} ${error?.description}") }
                            }
                        }
                    }
                loadUrl(url)
            }
        },
        onRelease = WebView::destroy,
    )
}
