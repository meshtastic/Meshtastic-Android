package com.geeksville.mesh.ui.components

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .size(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Button(
            modifier = modifier
                .fillMaxWidth()
                .weight(1f),
            enabled = enabled,
            onClick = onCancelClicked,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
        ) {
            Text(
                text = stringResource(id = R.string.cancel),
                style = MaterialTheme.typography.body1,
                color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
            )
        }
        Button(
            modifier = modifier
                .fillMaxWidth()
                .weight(1f),
            enabled = enabled,
            onClick = onSaveClicked,
            colors = ButtonDefaults.buttonColors(backgroundColor = Color.Green)
        ) {
            Text(
                text = stringResource(id = R.string.send),
                style = MaterialTheme.typography.body1,
                color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.DarkGray,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreferenceFooterPreview() {
    PreferenceFooter(enabled = true, onCancelClicked = {}, onSaveClicked = {})
}
