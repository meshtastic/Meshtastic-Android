package com.geeksville.mesh.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun SimpleAlertDialog(
    @StringRes title: Int,
    text: @Composable (() -> Unit)? = null,
    onDismiss: () -> Unit = {},
) = AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(horizontal = 16.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colors.onSurface,
            ),
        ) { Text(text = stringResource(id = R.string.close)) }
    },
    title = {
        Text(
            text = stringResource(id = title),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    },
    text = text,
    shape = RoundedCornerShape(16.dp),
    backgroundColor = MaterialTheme.colors.background,
)

@Composable
fun SimpleAlertDialog(
    @StringRes title: Int,
    @StringRes text: Int,
    onDismiss: () -> Unit = {},
) = SimpleAlertDialog(
    onDismiss = onDismiss,
    title = title,
    text = {
        Text(
            text = stringResource(id = text),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    },
)

@PreviewLightDark
@Composable
private fun SimpleAlertDialogPreview() {
    AppTheme {
        SimpleAlertDialog(
            title = R.string.message,
            text = R.string.sample_message,
        )
    }
}
