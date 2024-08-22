package com.geeksville.mesh.ui.components

import android.util.Base64
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Close
import androidx.compose.material.icons.twotone.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Channel
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString

@Composable
fun EditBase64Preference(
    title: String,
    value: ByteString,
    enabled: Boolean,
    keyboardActions: KeyboardActions,
    onValueChange: (ByteString) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun ByteString.encodeToString() = Base64.encodeToString(this.toByteArray(), Base64.NO_WRAP)
    fun String.toByteString() = Base64.decode(this, Base64.NO_WRAP).toByteString()

    var valueState by remember { mutableStateOf(value.encodeToString()) }
    val isError = value.encodeToString() != valueState

    // don't update values while the user is editing
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!isFocused) {
            valueState = value.encodeToString()
        }
    }

    OutlinedTextField(
        value = valueState,
        onValueChange = {
            valueState = it
            runCatching { it.toByteString() }.onSuccess(onValueChange)
        },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState -> isFocused = focusState.isFocused },
        enabled = enabled,
        label = { Text(text = title) },
        isError = isError,
        keyboardOptions = KeyboardOptions.Default.copy(
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
        ),
        keyboardActions = keyboardActions,
        trailingIcon = {
            IconButton(
                onClick = {
                    val psk = if (isError) value else Channel.getRandomKey()
                    valueState = psk.encodeToString()
                    onValueChange(psk)
                }
            ) {
                Icon(
                    imageVector = if (isError) Icons.TwoTone.Close else Icons.TwoTone.Refresh,
                    contentDescription = stringResource(if (isError) R.string.error else R.string.reset),
                    tint = if (isError) MaterialTheme.colors.error
                    else LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun EditBase64PreferencePreview() {
    EditBase64Preference(
        title = "Title",
        value = Channel.getRandomKey(),
        enabled = true,
        keyboardActions = KeyboardActions {},
        onValueChange = {},
        modifier = Modifier.padding(16.dp)
    )
}
