package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun BitwisePreference(
    title: String,
    value: Int,
    enabled: Boolean,
    items: List<Pair<Int, String>>,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dropDownExpanded by remember { mutableStateOf(value = false) }

    RegularPreference(
        title = title,
        subtitle = value.toString(),
        onClick = { dropDownExpanded = !dropDownExpanded },
        enabled = enabled,
        trailingIcon = if (dropDownExpanded) Icons.TwoTone.KeyboardArrowDown
        else Icons.TwoTone.KeyboardArrowUp,
    )

    Box {
        DropdownMenu(
            expanded = dropDownExpanded,
            onDismissRequest = { dropDownExpanded = !dropDownExpanded },
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    onClick = { onItemSelected(value xor item.first) },
                    modifier = Modifier.fillMaxWidth(),
                    content = {
                        Text(
                            text = item.second,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Checkbox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentWidth(Alignment.End),
                            checked = value and item.first != 0,
                            onCheckedChange = { onItemSelected(value xor item.first) },
                            enabled = enabled,
                        )
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    enabled = enabled,
                    onClick = { onItemSelected(0) },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red)
                ) {
                    Text(
                        text = stringResource(id = R.string.clear_last_messages),
                        style = MaterialTheme.typography.body1,
                        color = Color.Unspecified,
                    )
                }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    enabled = enabled,
                    onClick = { dropDownExpanded = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.Green)
                ) {
                    Text(
                        text = stringResource(id = R.string.close),
                        style = MaterialTheme.typography.body1,
                        color = Color.DarkGray,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BitwisePreferencePreview() {
    BitwisePreference(
        title = "Settings",
        value = 3,
        enabled = true,
        items = listOf(1 to "TEST1", 2 to "TEST2"),
        onItemSelected = {}
    )
}
