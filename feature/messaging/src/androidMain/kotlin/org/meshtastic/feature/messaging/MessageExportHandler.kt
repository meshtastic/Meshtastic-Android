/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.messaging

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.eygraber.uri.toKmpUri
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.common.util.toDate
import org.meshtastic.core.common.util.toInstant
import org.meshtastic.core.model.Node
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
actual fun MessageExportHandler(
    ourNode: Node?,
    onExport: (org.meshtastic.core.common.util.CommonUri) -> Unit
): () -> Unit {
    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let { uri -> onExport(uri.toKmpUri()) }
            }
        }

    return {
        val nodeName = (ourNode?.user?.short_name ?: "").ifBlank { "node" }
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val dateStr = dateFormat.format(nowMillis.toInstant().toDate())
        val fileName = "Meshtastic_${nodeName}_${dateStr}_messages.csv"
        val intent =
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
        exportLauncher.launch(intent)
    }
}
