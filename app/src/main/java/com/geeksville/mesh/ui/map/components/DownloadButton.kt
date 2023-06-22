package com.geeksville.mesh.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.geeksville.mesh.R

@Composable
fun DownloadButton(
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
            Image(
                painterResource(R.drawable.ic_twotone_download_24),
                stringResource(R.string.map_download_region),
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
