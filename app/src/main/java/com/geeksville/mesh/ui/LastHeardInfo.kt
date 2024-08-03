package com.geeksville.mesh.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.formatAgo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes

@Composable
fun LastHeardInfo(
    modifier: Modifier = Modifier,
    lastHeard: Int
) {
    var ago by remember { mutableStateOf(formatAgo(lastHeard)) }

    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        if (running) {
            while (true) {
                delay(1.minutes)
                ago = formatAgo(lastHeard)
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        running = true
        onPauseOrDispose {
            running = false
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            modifier = Modifier.height(18.dp),
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_antenna_24),
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface,
        )
        Text(
            text = ago,
            color = MaterialTheme.colors.onSurface,
            fontSize = MaterialTheme.typography.button.fontSize
        )
    }
}

@PreviewLightDark
@Composable
fun LastHeardInfoPreview() {
    AppTheme {
        LastHeardInfo(lastHeard = (System.currentTimeMillis() / 1000).toInt() - 8600)
    }
}
