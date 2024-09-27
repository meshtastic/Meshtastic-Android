package com.geeksville.mesh.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
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
fun RegularPreference(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
) {
    RegularPreference(
        title = title,
        subtitle = AnnotatedString(text = subtitle),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        summary = summary,
        trailingIcon = trailingIcon,
    )
}

@Composable
fun RegularPreference(
    title: String,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: String? = null,
    trailingIcon: ImageVector? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(all = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.body1,
                color = if (enabled) {
                    Color.Unspecified
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.body1,
                color = if (enabled) {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
            )
            if (trailingIcon != null) Icon(
                trailingIcon, "trailingIcon",
                modifier = modifier
                    .padding(start = 8.dp)
                    .wrapContentWidth(Alignment.End),
                tint = if (enabled) {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
            )
        }
        if (summary != null) {
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.body2,
                color = if (enabled) {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                } else {
                    MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RegularPreferencePreview() {
    RegularPreference(
        title = "Advanced settings",
        subtitle = "Text2",
        onClick = { },
    )
}