package com.geeksville.mesh.ui.map.components
import androidx.compose.foundation.Image
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.R

@Composable
fun DownloadButton(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        backgroundColor = MaterialTheme.colors.primary,
    ) {
        Image(
            painter = painterResource(id = R.drawable.cloud_download_outline_24),
            contentDescription = "Download Icon",
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadButtonPreview() {
    DownloadButton(onClick = {})
}
