package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PreferenceCategory(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text,
        modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp, end = 16.dp),
        style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)
    )
}