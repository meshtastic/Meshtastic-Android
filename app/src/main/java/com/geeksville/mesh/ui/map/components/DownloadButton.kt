package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun DownloadButton(
    cacheMenu: @Composable () -> Unit,
    onClick: () -> Unit,
) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp, end = 16.dp)
                .wrapContentSize(Alignment.BottomEnd)
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
    }


//@Preview(showBackground = true)
//@Composable
//private fun DownloadButtonPreview() {
//    DownloadButton(onClick = {})
//}
