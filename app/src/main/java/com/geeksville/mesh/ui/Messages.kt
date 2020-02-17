package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.core.TextField
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.layout.*
import androidx.ui.material.Emphasis
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.material.surface.Surface
import androidx.ui.text.TextStyle
import androidx.ui.tooling.preview.Preview
import androidx.ui.unit.dp
import com.geeksville.mesh.model.MessagesState
import com.geeksville.mesh.model.MessagesState.messages
import com.geeksville.mesh.model.NodeDB
import com.geeksville.mesh.model.TextMessage
import java.text.SimpleDateFormat


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

        // modifier = LayoutFlexible(1.0f)
        Column {
            messages.value.forEach { msg ->
                MessageCard(
                    msg, modifier = LayoutPadding(
                        left = sidePad,
                        right = sidePad,
                        top = topPad,
                        bottom = topPad
                    )
                )
            }
        }

        Spacer(LayoutFlexible(1f))

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
                    MessagesState.addMessage(
                        TextMessage(
                            "fixme",
                            message.value
                        )
                    )
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
