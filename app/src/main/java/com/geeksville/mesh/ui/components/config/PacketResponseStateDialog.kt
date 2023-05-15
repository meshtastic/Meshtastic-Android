package com.geeksville.mesh.ui.components.config

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.PacketResponseState

@Composable
fun PacketResponseStateDialog(
    state: PacketResponseState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state is PacketResponseState.Loading) {
                    val progress = state.completed.toFloat() / state.total.toFloat()
                    Text("%.0f%%".format(progress * 100))
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        color = MaterialTheme.colors.onSurface,
                    )
                }
                if (state is PacketResponseState.Success) {
                    Text("Success!")
                }
                if (state is PacketResponseState.Error) {
                    Text("Error: ${state.error}")
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
                ) {
                    if (state is PacketResponseState.Loading) {
                        Text(stringResource(R.string.cancel))
                    } else {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun PacketResponseStateDialogPreview() {
    PacketResponseStateDialog(
        state = PacketResponseState.Loading.apply {
            total = 17
            completed = 5
        },
        onDismiss = { }
    )
}
