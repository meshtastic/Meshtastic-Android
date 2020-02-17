package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.mutableStateOf
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.core.TextField
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.Row
import androidx.ui.material.Emphasis
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.material.surface.Surface
import androidx.ui.text.TextStyle
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.android.Logging
import com.geeksville.mesh.ui.MessagesState.messages
import java.text.SimpleDateFormat
import java.util.*


/**
 * the model object for a text message
 */
data class TextMessage(val from: String, val text: String, val date: Date = Date())


object MessagesState : Logging {
    val testTexts = listOf(
        TextMessage("+6508675310", "I found the cache"),
        TextMessage("+6508675311", "Help! I've fallen and I can't get up.")
    )

    // If the following (unused otherwise) line is commented out, the IDE preview window works.
    // if left in the preview always renders as empty.
    val messages = mutableStateOf(MessagesState.testTexts, { a, b ->
        a.size == b.size // If the # of messages changes, consider it important for rerender
    })

    fun addMessage(m: TextMessage) {
        val l = messages.value.toMutableList()
        l.add(m)
        messages.value = l
    }
}

private val dateFormat = SimpleDateFormat("h:mm a")

val TimestampEmphasis = object : Emphasis {
    override fun emphasize(color: Color) = color.copy(alpha = 0.12f)
}


/**
 * A pretty version the text, with user icon to the left, name and time of arrival (copy slack look and feel)
 */
@Composable
fun MessageCard(msg: TextMessage, modifier: Modifier = Modifier.None) {


    Row(modifier = modifier) {
        UserIcon(null)

        Column(modifier = LayoutPadding(left = 12.dp)) {
            Row {
                val nodes = NodeDB.nodes.value

                // If we can't find the sender, just use the ID
                val node = nodes?.get(msg.from)
                val user = node?.user
                val senderName = user?.longName ?: msg.from
                Text(text = senderName)
                ProvideEmphasis(emphasis = TimestampEmphasis) {
                    Text(
                        text = dateFormat.format(msg.date),
                        modifier = LayoutPadding(left = 8.dp),
                        style = MaterialTheme.typography().caption
                    )
                }

            }
            Text(
                text = msg.text
            )
        }
    }
}

@Composable
fun MessagesContent() {
    Column(modifier = LayoutSize.Fill) {

        val sidePad = 8.dp
        val topPad = 4.dp

        Column(modifier = LayoutFlexible(1.0f)) {
            messages.value.forEach {
                MessageCard(
                    it, modifier = LayoutPadding(
                        left = sidePad,
                        right = sidePad,
                        top = topPad,
                        bottom = topPad
                    )
                )
            }
        }

        val message = state { "text message" }
        val backgroundColor = palette.secondary.copy(alpha = 0.12f)
        Surface(
            modifier = LayoutPadding(8.dp),
            color = backgroundColor,
            shape = RoundedCornerShape(4.dp)
        ) {
            TextField(
                value = message.value,
                onValueChange = { message.value = it },
                textStyle = TextStyle(
                    color = palette.onSecondary.copy(alpha = 0.8f)
                ),
                imeAction = ImeAction.Send,
                onImeActionPerformed = {
                    MessagesState.info("did IME action")
                    MessagesState.addMessage(TextMessage("fixme", message.value))
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
