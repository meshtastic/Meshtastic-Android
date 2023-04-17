package com.geeksville.mesh.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    onCancelClicked: () -> Unit,
    onSaveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PreferenceFooter(
        enabled = enabled,
        negativeText = R.string.cancel,
        onNegativeClicked = onCancelClicked,
        positiveText = R.string.send,
        onPositiveClicked = onSaveClicked,
        modifier = modifier,
    )
}

@Composable
fun PreferenceFooter(
    enabled: Boolean,
    @StringRes negativeText: Int,
    onNegativeClicked: () -> Unit,
    @StringRes positiveText: Int,
    onPositiveClicked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .weight(1f),
            enabled = enabled,
            onClick = onNegativeClicked,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Red.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) {
            Text(
                text = stringResource(id = negativeText),
                style = MaterialTheme.typography.body1,
            )
        }
        Button(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .weight(1f),
            enabled = enabled,
            onClick = onPositiveClicked,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Green.copy(alpha = 0.6f),
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) {
            Text(
                text = stringResource(id = positiveText),
                style = MaterialTheme.typography.body1,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceFooterPreview() {
    PreferenceFooter(enabled = true, onCancelClicked = {}, onSaveClicked = {})
}
