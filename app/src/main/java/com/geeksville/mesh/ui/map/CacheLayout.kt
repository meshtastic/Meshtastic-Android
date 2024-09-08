package com.geeksville.mesh.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CacheLayout(
    cacheEstimate: String,
    onExecuteJob: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(color = MaterialTheme.colors.background)
            .padding(8.dp),
    ) {
        Text(
            text = stringResource(id = R.string.map_select_download_region),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.map_tile_download_estimate) + " " + cacheEstimate,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium),
        )

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            Button(
                onClick = onCancelDownload,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    color = MaterialTheme.colors.onPrimary,
                )
            }
            Button(
                onClick = onExecuteJob,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(id = R.string.map_start_download),
                    color = MaterialTheme.colors.onPrimary,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CacheLayoutPreview() {
    CacheLayout(
        cacheEstimate = "100 tiles",
        onExecuteJob = { },
        onCancelDownload = { },
    )
}
