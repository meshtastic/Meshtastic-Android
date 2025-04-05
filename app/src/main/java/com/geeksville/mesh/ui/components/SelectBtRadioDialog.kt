package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.geeksville.mesh.model.BTScanModel
import com.geeksville.mesh.R


@Composable
fun SelectBtRadioDialog(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit = {},
    onRadioSelected: (BTScanModel.DeviceListEntry) -> Unit = {},
    devices: Map<String, BTScanModel.DeviceListEntry> = emptyMap(),
) {
    BottomSheetDialog(
        modifier = modifier.fillMaxWidth(),
        onDismiss = onDismissRequest) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("Select a radio")
            LazyColumn {
                items(devices.values.toList()) { device ->
                    TextButton(onClick = { onRadioSelected(device); onDismissRequest() }) {
                        Text(device.name)
                    }
                }
            }

            OutlinedButton(onClick = onDismissRequest, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}


@Preview
@Composable
private fun SelectBtRadioDialogPreview() {
    Surface {
        SelectBtRadioDialog()
    }
}
