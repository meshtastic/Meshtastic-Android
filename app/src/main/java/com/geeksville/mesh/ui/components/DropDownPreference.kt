package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.KeyboardArrowDown
import androidx.compose.material.icons.twotone.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun <T> DropDownPreference(
    title: String,
    enabled: Boolean,
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    var dropDownExpanded by remember { mutableStateOf(value = false) }

    RegularPreference(
        title = title,
        subtitle = items.first { it.first == selectedItem }.second,
        onClick = {
            dropDownExpanded = true
        },
        enabled = enabled,
        trailingIcon = if (dropDownExpanded) Icons.TwoTone.KeyboardArrowUp
        else Icons.TwoTone.KeyboardArrowDown,
        summary = summary,
    )

    Box {
        DropdownMenu(
            expanded = dropDownExpanded,
            onDismissRequest = { dropDownExpanded = !dropDownExpanded },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = {
                        dropDownExpanded = false
                        onItemSelected(item.first)
                    },
                    modifier = modifier
                        .background(
                            color = if (selectedItem == item.first)
                                MaterialTheme.colors.primary.copy(alpha = 0.3f)
                            else
                                Color.Unspecified,
                        ),
                    content = {
                        Text(
                            text = item.second,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DropDownPreferencePreview() {
    DropDownPreference(
        title = "Settings",
        summary = "Lorem ipsum dolor sit amet",
        enabled = true,
        items = listOf("TEST1" to "text1", "TEST2" to "text2"),
        selectedItem = "TEST2",
        onItemSelected = {}
    )
}
