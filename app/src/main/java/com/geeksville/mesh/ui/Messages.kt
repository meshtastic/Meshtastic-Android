package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.mutableStateOf
import androidx.compose.state
import androidx.ui.core.Clip
import androidx.ui.core.Text
import androidx.ui.core.TextField
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.layout.Column
import androidx.ui.layout.Padding
import androidx.ui.layout.Row
import androidx.ui.material.MaterialTheme
import androidx.ui.material.darkColorPalette
import androidx.ui.material.surface.Surface
import androidx.ui.text.TextStyle
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging
import com.geeksville.mesh.ui.MessagesState.messages
import java.util.*


data class TextMessage(val date: Date, val from: String, val text: String)


object MessagesState : Logging {
    val testTexts = listOf(
        TextMessage(Date(), "+6508675310", "I found the cache"),
        TextMessage(Date(), "+6508675311", "Help! I've fallen and I can't get up.")
    )

    // If the following (unused otherwise) line is commented out, the IDE preview window works.
    // if left in the preview always renders as empty.
    val messages = mutableStateOf(MessagesState.testTexts)
}

@Composable
fun MessagesContent() {
    Column {

        Text("hi")

        messages.value.forEach {
            Text("Text: ${it.text}")
        }

        val message = state { "text message" }
        Surface(color = Color.Yellow) {
            Row {
                Clip(shape = RoundedCornerShape(15.dp)) {
                    Padding(padding = 15.dp) {
                        TextField(
                            value = message.value,
                            onValueChange = { message.value = it },
                            textStyle = TextStyle(
                                color = Color.DarkGray
                            ),
                            imeAction = ImeAction.Send,
                            onImeActionPerformed = {
                                MessagesState.info("did IME action")
                            }
                        )
                    }
                }
            }
        }
    }
}


@Preview
@Composable
fun previewMessagesView() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = darkColorPalette()) {
        MessagesContent()
    }
}
