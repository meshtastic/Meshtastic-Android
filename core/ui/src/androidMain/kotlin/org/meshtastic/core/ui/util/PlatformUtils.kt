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
package org.meshtastic.core.ui.util

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.net.URLEncoder

@Composable
actual fun rememberOpenNfcSettings(): () -> Unit {
    val context = LocalContext.current
    return remember(context) {
        {
            val intent = Intent(Settings.ACTION_NFC_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}

@Composable
actual fun rememberShowToast(): suspend (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text -> context.showToast(text) } }
}

@Composable
actual fun rememberShowToastResource(): suspend (StringResource) -> Unit {
    val context = LocalContext.current
    return remember(context) { { stringResource -> context.showToast(getString(stringResource)) } }
}

@Composable
actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { lat, lon, label ->
            val encodedLabel = URLEncoder.encode(label, "utf-8")
            val uri = "geo:0,0?q=$lat,$lon&z=17&label=$encodedLabel".toUri()
            val intent = Intent(Intent.ACTION_VIEW, uri).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                }
            } catch (ex: ActivityNotFoundException) {
                Logger.d { "Failed to open geo intent: $ex" }
            }
        }
    }
}

@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            try {
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            } catch (ex: ActivityNotFoundException) {
                Logger.d { "Failed to open URL intent: $ex" }
            }
        }
    }
}

@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (org.meshtastic.core.common.util.MeshtasticUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    onUriReceived(uri.toString().let { org.meshtastic.core.common.util.MeshtasticUri(it) })
                }
            }
        }

    return remember(launcher) {
        { defaultFilename, mimeType ->
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeType
                    putExtra(Intent.EXTRA_TITLE, defaultFilename)
                }
            launcher.launch(intent)
        }
    }
}

@Composable
actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
        ) { permissions ->
            if (permissions.values.any { it }) {
                onGranted()
            } else {
                onDenied()
            }
        }
    return remember(launcher) {
        {
            launcher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }
}

@Composable
actual fun rememberOpenLocationSettings(): () -> Unit {
    val launcher =
        androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
        ) { _ ->
        }
    return remember(launcher) { { launcher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
}
