package com.geeksville.mesh.ui

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.graphics.Color
import androidx.ui.input.ImeAction
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.Row
import androidx.ui.material.Emphasis
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
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
    override fun emphasize(color: Color) = color.copy(alpha = 0.25f)
}


/**
 * A pretty version the text, with user icon to the left, name and time of arrival (copy slack look and feel)
 */
@Composable
fun MessageCard(msg: TextMessage, modifier: Modifier = Modifier.None) {
    Row(modifier = modifier) {
        UserIcon(NodeDB.nodes[msg.from])

        Column(modifier = LayoutPadding(start = 12.dp)) {
            Row {
                val nodes = NodeDB.nodes

                // If we can't find the sender, just use the ID
                val node = nodes.get(msg.from)
                val user = node?.user
                val senderName = user?.longName ?: msg.from
                Text(text = senderName)
                ProvideEmphasis(emphasis = TimestampEmphasis) {
                    Text(
                        text = dateFormat.format(msg.date),
                        modifier = LayoutPadding(start = 8.dp),
                        style = MaterialTheme.typography().caption
                    )
                }
            }
            if (msg.errorMessage != null)
                Text(text = msg.errorMessage, style = TextStyle(color = palette.error))
            else
                Text(text = msg.text)
        }
    }
}


@Composable
fun MessagesContent() {
    Column(modifier = LayoutSize.Fill) {

        val sidePad = 8.dp
        val topPad = 4.dp

        VerticalScroller(
            modifier = LayoutFlexible(1f)
        ) {
            Column {
                messages.forEach { msg ->
                    MessageCard(
                        msg, modifier = LayoutPadding(
                            start = sidePad,
                            end = sidePad,
                            top = topPad,
                            bottom = topPad
                        )
                    )
                }
            }
        }

        // Spacer(LayoutFlexible(1f))

        val message = state { "" }
        StyledTextField(
            value = message.value,
            onValueChange = { message.value = it },
            textStyle = TextStyle(
                color = palette.onSecondary.copy(alpha = 0.8f)
            ),
            imeAction = ImeAction.Send,
            onImeActionPerformed = {
                MessagesState.info("did IME action")

                val str = message.value
                MessagesState.sendMessage(str)
                message.value = "" // blow away the string the user just entered
            },
            hintText = "Type your message here..."
        )
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
