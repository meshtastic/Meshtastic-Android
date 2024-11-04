package com.geeksville.mesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R

@Composable
internal fun DownloadButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = enabled,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    ) {
        FloatingActionButton(
            onClick = onClick,
            backgroundColor = MaterialTheme.colors.primary,
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(R.string.map_download_region),
                modifier = Modifier.scale(1.25f),
            )
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//private fun DownloadButtonPreview() {
//    DownloadButton(true, onClick = {})
//}
