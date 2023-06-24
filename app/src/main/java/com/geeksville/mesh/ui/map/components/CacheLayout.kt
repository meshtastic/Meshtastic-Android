package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun CacheLayout(
    cacheEstimate: String,
    onExecuteJob: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDistance by remember { mutableStateOf(5) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .background(colorResource(R.color.colorAdvancedBackground))
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(id = R.string.map_select_download_region),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.h5,
            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val distances = listOf(5, 10, 15)
        val selectedDistanceIndex = distances.indexOf(selectedDistance)

//        ToggleButton(
//            options = distances.map { it.toString() },
//            selectedOptionIndex = selectedDistanceIndex,
//            onOptionSelected = { selectedDistance = distances[it] },
//        )

//        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.map_tile_download_estimate) + " " + cacheEstimate,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.body1,
            color = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = onCancelDownload,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.cancel),
                    color = MaterialTheme.colors.onPrimary
                )
            }
            Button(
                onClick = onExecuteJob,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.map_start_download),
                    color = MaterialTheme.colors.onPrimary
                )
            }
        }
    }
}

@Composable
fun ToggleButton(
    options: List<String>,
    selectedOptionIndex: Int,
    onOptionSelected: (Int) -> Unit
) {
    val backgroundColor = MaterialTheme.colors.background
    val selectedColor = MaterialTheme.colors.primary
    val textColor = MaterialTheme.colors.onBackground.copy(alpha = ContentAlpha.medium)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedOptionIndex

            Button(
                onClick = { onOptionSelected(index) },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isSelected) selectedColor else backgroundColor,
                    contentColor = textColor
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = option,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (index != options.lastIndex) {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CacheLayoutPreview() {
    CacheLayout(
        cacheEstimate = "100 tiles",
        onExecuteJob = { },
        onCancelDownload = { }
    )
}
