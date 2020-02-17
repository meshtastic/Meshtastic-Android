package com.geeksville.mesh.ui

import android.os.RemoteException
import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.core.TextField
import androidx.ui.foundation.VerticalScroller
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
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.model.MessagesState
import com.geeksville.mesh.model.MessagesState.messages
import com.geeksville.mesh.model.NodeDB
import com.geeksville.mesh.model.TextMessage
import com.geeksville.mesh.model.UIState
import com.geeksville.mesh.utf8
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
        UserIcon(NodeDB.nodes.value[msg.from])

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
        }

        // Spacer(LayoutFlexible(1f))

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

                    val str = message.value

                    var error: String? = null
                    val service = UIState.meshService
                    if (service != null)
                        try {
                            service.sendData(
                                null,
                                str.toByteArray(utf8),
                                MeshProtos.Data.Type.CLEAR_TEXT_VALUE
                            )
                        } catch (ex: RemoteException) {
                            error = "Error: ${ex.message}"
                        }
                    else
                        error = "Error: No Mesh service"

                    MessagesState.addMessage(
                        TextMessage(
                            NodeDB.myId.value,
                            str,
                            errorMessage = error
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
