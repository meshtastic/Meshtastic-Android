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
package org.meshtastic.feature.map.layers

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import org.meshtastic.core.common.util.nowMillis

@Composable
actual fun rememberMapLayerPicker(onPick: (PickedMapFile) -> Unit): MapLayerPickerLauncher {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { onPick(it.toPickedMapFile(context)) }
            }
        }

    return MapLayerPickerLauncher {
        // Providers hand back generic MIME types for perfectly good map files, so the extension is validated after
        // selection rather than filtered here.
        launcher.launch(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
        )
    }
}

/**
 * Wrap a picked `content://` as a [PickedMapFile] for the layer store.
 *
 * The store never sees a [Uri]: resolving one is exactly the platform knowledge it exists to avoid, so the read is
 * handed over as a lambda instead. The MIME type is the fallback for the extension rather than being used only when the
 * display name is absent — a picked file with no dot in its name used to resolve to no type and be rejected.
 */
fun Uri.toPickedMapFile(context: Context): PickedMapFile {
    val displayName = getFileName(context)
    return PickedMapFile(
        displayName = displayName,
        extensionOrMime =
        displayName.substringAfterLast('.', "").ifBlank {
            context.contentResolver.getType(this)?.substringAfterLast('/')
        },
        read = { context.contentResolver.openInputStream(this)?.use { it.readBytes() } },
    )
}

/**
 * Resolve a display file name for [this] URI, querying the content resolver for `content://` URIs. Untrusted providers
 * (share/open-with from other apps) can throw or return a null display name, so guard both and fall back to the URI's
 * last path segment.
 */
@Suppress("NestedBlockDepth")
fun Uri.getFileName(context: Context): String {
    var name = lastPathSegment ?: "layer_$nowMillis"
    if (scheme == "content") {
        try {
            context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        cursor.getString(displayNameIndex)?.let { name = it }
                    }
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Keep the lastPathSegment fallback assigned above rather than crashing the import.
            Logger.withTag("MapLayer").w(e) { "Failed to resolve display name for content URI; using fallback" }
        }
    }
    return name
}
