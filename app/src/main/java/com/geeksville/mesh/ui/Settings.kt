package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.ambient
import androidx.compose.state
import androidx.ui.core.ContextAmbient
import androidx.ui.core.Text
import androidx.ui.input.ImeAction
import androidx.ui.layout.*
import androidx.ui.material.MaterialTheme
import androidx.ui.text.TextStyle
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging
import com.geeksville.mesh.model.MessagesState
import com.geeksville.mesh.model.UIState


object SettingsLog : Logging

@Composable
fun SettingsContent() {
    val typography = MaterialTheme.typography()

    val context = ambient(ContextAmbient)
    Column(modifier = LayoutSize.Fill + LayoutPadding(16.dp)) {

        Row {
            Text("Your name ", modifier = LayoutGravity.Center)

            val name = state { UIState.ownerName }
            StyledTextField(
                value = name.value,
                onValueChange = { name.value = it },
                textStyle = TextStyle(
                    color = palette.onSecondary.copy(alpha = 0.8f)
                ),
                imeAction = ImeAction.Done,
                onImeActionPerformed = {
                    MessagesState.info("did IME action")
                    val n = name.value.trim()
                    if (n.isNotEmpty())
                        UIState.setOwner(context, n)
                },
                hintText = "Type your name here...",
                modifier = LayoutGravity.Center
            )
        }

        BTScanScreen()
    }
}


@Preview
@Composable
fun previewSettings() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        SettingsContent()
    }
}
