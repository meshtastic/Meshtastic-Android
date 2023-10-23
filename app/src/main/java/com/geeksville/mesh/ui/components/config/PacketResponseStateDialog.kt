package com.geeksville.mesh.ui.components.config

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.ResponseState

@Composable
fun <T> PacketResponseStateDialog(
    state: ResponseState<T>,
    onDismiss: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state is ResponseState.Loading) {
                    val progress by animateFloatAsState(
                        targetValue = state.completed.toFloat() / state.total.toFloat(),
                        label = "progress",
                    )
                    Text("%.0f%%".format(progress * 100))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colors.onSurface,
                    )
                    if (state.total == state.completed) onComplete()
                }
                if (state is ResponseState.Success) {
                    Text("Delivery confirmed")
                }
                if (state is ResponseState.Error) {
                    Text(text = "Error\n", textAlign = TextAlign.Center)
                    Text(state.error)
                }
            }
        },
        buttons = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.padding(top = 16.dp)
                ) { Text(stringResource(R.string.close)) }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun PacketResponseStateDialogPreview() {
    PacketResponseStateDialog(
        state = ResponseState.Loading(
            total = 17,
            completed = 5,
        ),
    )
}
