package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.mutableStateOf
import androidx.compose.state
import androidx.ui.core.Text
import androidx.ui.core.TextField
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.input.ImeAction
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.material.MaterialTheme
import androidx.ui.material.surface.Surface
import androidx.ui.text.TextStyle
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging
import com.geeksville.mesh.ui.MessagesState.messages
import java.util.*


data class TextMessage(val date: Date, val from: String, val text: String, val time: Date? = null)


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
    Column(modifier = LayoutSize.Fill) {

        val sidePad = 8.dp
        val topPad = 4.dp

        messages.value.forEach {
            Text(
                text = "Text: ${it.text}",
                modifier = LayoutPadding(
                    left = sidePad,
                    right = sidePad,
                    top = topPad,
                    bottom = topPad
                )
            )
        }

        val message = state { "text message" }

        val colors = MaterialTheme.colors()
        val backgroundColor = colors.secondary.copy(alpha = 0.12f)

        Surface(
            modifier = LayoutPadding(8.dp),
            color = backgroundColor,
            shape = RoundedCornerShape(4.dp)
        ) {
            TextField(
                value = message.value,
                onValueChange = { message.value = it },
                textStyle = TextStyle(
                    color = colors.onSecondary.copy(alpha = 0.8f)
                ),
                imeAction = ImeAction.Send,
                onImeActionPerformed = {
                    MessagesState.info("did IME action")
                },
                modifier = LayoutPadding(4.dp)
            )
        }
    }
}


@Preview
@Composable
fun previewMessagesView() {
    // another bug? It seems modaldrawerlayout not yet supported in preview
    MaterialTheme(colors = palette) {
        MessagesContent()
    }
}
