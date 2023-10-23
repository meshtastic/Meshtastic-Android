package com.geeksville.mesh.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun TextDividerPreference(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    TextDividerPreference(
        title = AnnotatedString(text = title),
        enabled = enabled,
        modifier = modifier,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun TextDividerPreference(
    title: AnnotatedString,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        backgroundColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(all = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
            )
            if (trailingIcon != null) Icon(
                trailingIcon, "trailingIcon",
                modifier = modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.End),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TextDividerPreferencePreview() {
    TextDividerPreference(title = "Advanced settings")
}
