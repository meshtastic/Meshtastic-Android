package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.onCommit
import com.geeksville.android.GeeksvilleApplication

/**
 * Track compose screen visibility
 */
@Composable
fun analyticsScreen(name: String) {
    onCommit(AppStatus.currentScreen) {
        GeeksvilleApplication.analytics.sendScreenView(name)

        onDispose {
            GeeksvilleApplication.analytics.endScreenView()
        }
    }
}