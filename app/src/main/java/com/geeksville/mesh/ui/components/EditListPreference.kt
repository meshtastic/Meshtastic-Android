package com.geeksville.mesh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R

@Composable
fun EditListPreference(
    title: String,
    list: List<Int>,
    maxCount: Int,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValuesChanged: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = remember(list) { mutableStateListOf<Int>().apply { addAll(list) } }

    Column(modifier = modifier) {
        Text(
            modifier = modifier.padding(16.dp),
            text = title,
            style = MaterialTheme.typography.body2,
            color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
        )
        listState.forEachIndexed { index, value ->
            EditTextPreference(
                title = "${index + 1}/$maxCount",
                value = value,
                enabled = enabled,
                keyboardActions = keyboardActions,
                onValueChanged = {
                    listState[index] = it
                    onValuesChanged(listState)
                },
                modifier = modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            listState.removeAt(index)
                            onValuesChanged(listState)
                        }
                    ) {
                        Icon(
                            Icons.TwoTone.Close,
                            stringResource(R.string.delete),
                            modifier = Modifier.wrapContentSize(),
                        )
                    }
                }
            )
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { listState.add(listState.size, 0) },
            enabled = maxCount > listState.size,
            colors = ButtonDefaults.buttonColors(
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            )
        ) { Text(text = stringResource(R.string.add)) }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditListPreferencePreview() {
    EditListPreference(
        title = "Ignore incoming",
        list = listOf(12345,67890),
        maxCount = 4,
        enabled = true,
        keyboardActions = KeyboardActions {},
        onValuesChanged = { },
    )
}
