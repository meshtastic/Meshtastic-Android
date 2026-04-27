/*
 * Copyright (c) 2026 Meshtastic LLC
 */
package org.meshtastic.feature.messaging

import androidx.compose.runtime.Composable
import org.meshtastic.core.model.Node

@Composable
actual fun MessageExportHandler(
    ourNode: Node?,
    onExport: (org.meshtastic.core.common.util.CommonUri) -> Unit
): () -> Unit {
    return {
        // Not implemented for JVM yet
    }
}
