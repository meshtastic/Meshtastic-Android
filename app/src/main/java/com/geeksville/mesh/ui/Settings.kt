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
import com.geeksville.mesh.service.RadioInterfaceService


object SettingsLog : Logging

@Composable
fun SettingsContent() {
    //val typography = MaterialTheme.typography()

    val context = ContextAmbient.current
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

        val bonded = RadioInterfaceService.getBondedDeviceAddress(context) != null
        if (!bonded) {

            val typography = MaterialTheme.typography()

            Text(
                text =
                """
            You haven't yet paired a Meshtastic compatible radio with this phone.
            
            This application is an early alpha release, if you find problems please post on our website chat.
            
            For more information see our web page - www.meshtastic.org.
        """.trimIndent(), style = typography.body2
            )
        }
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
