package org.meshtastic.app

import androidx.compose.runtime.Composable

@Composable
fun MapLocals(content: @Composable () -> Unit) {
    // No map locals for minimal flavor
    content()
}
