package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ui.theme.AppTheme

@Composable
fun NodeFilterTextField(
    filterText : String = "",
    onTextChanged : (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 48.dp)
            .background(MaterialTheme.colors.background),
        value = filterText,
        onValueChange = onTextChanged,
        trailingIcon = {
            if (filterText.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "clear node filter",
                    modifier = Modifier.clickable { onTextChanged("") }
                )
            }
        },
        textStyle = TextStyle(
            color = MaterialTheme.colors.onBackground
        ),
        maxLines = 1,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { focusManager.clearFocus() }
        )
    )
}

@Composable
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
fun NodeFilterTextFieldPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.background)
        ) {
            NodeFilterTextField(
                filterText = "Filter text",
                onTextChanged = { }
            )
        }
    }
}