package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.TextMessage
import com.geeksville.mesh.model.UIViewModel
import kotlinx.android.synthetic.main.messages_fragment.*


class MessagesFragment : ScreenFragment("Messages"), Logging {

    private val model: UIViewModel by activityViewModels()

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    }

    private val messagesAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        /**
         * Called when RecyclerView needs a new [ViewHolder] of the given type to represent
         * an item.
         *
         *
         * This new ViewHolder should be constructed with a new View that can represent the items
         * of the given type. You can either create a new View manually or inflate it from an XML
         * layout file.
         *
         *
         * The new ViewHolder will be used to display items of the adapter using
         * [.onBindViewHolder]. Since it will be re-used to display
         * different items in the data set, it is a good idea to cache references to sub views of
         * the View to avoid unnecessary [View.findViewById] calls.
         *
         * @param parent The ViewGroup into which the new View will be added after it is bound to
         * an adapter position.
         * @param viewType The view type of the new View.
         *
         * @return A new ViewHolder that holds a View of the given view type.
         * @see .getItemViewType
         * @see .onBindViewHolder
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(requireContext())

            // Inflate the custom layout

            // Inflate the custom layout
            val contactView: View = inflater.inflate(R.layout.adapter_node_layout, parent, false)

            // Return a new holder instance
            return ViewHolder(contactView)
        }

        /**
         * Returns the total number of items in the data set held by the adapter.
         *
         * @return The total number of items in this adapter.
         */
        override fun getItemCount(): Int = messages.size

        /**
         * Called by RecyclerView to display the data at the specified position. This method should
         * update the contents of the [ViewHolder.itemView] to reflect the item at the given
         * position.
         *
         *
         * Note that unlike [android.widget.ListView], RecyclerView will not call this method
         * again if the position of the item changes in the data set unless the item itself is
         * invalidated or the new position cannot be determined. For this reason, you should only
         * use the `position` parameter while acquiring the related data item inside
         * this method and should not keep a copy of it. If you need the position of an item later
         * on (e.g. in a click listener), use [ViewHolder.getAdapterPosition] which will
         * have the updated adapter position.
         *
         * Override [.onBindViewHolder] instead if Adapter can
         * handle efficient partial bind.
         *
         * @param holder The ViewHolder which should be updated to represent the contents of the
         * item at the given position in the data set.
         * @param position The position of the item within the adapter's data set.
         */
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val n = messages[position]
        }

        private var messages = arrayOf<TextMessage>()

        /// Called when our node DB changes
        fun onMessagesChanged(nodesIn: Collection<TextMessage>) {
            messages = nodesIn.toTypedArray()
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all messages
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.messages_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messageListView.adapter = messagesAdapter
        messageListView.layoutManager = LinearLayoutManager(requireContext())

        model.messagesState.messages.observe(viewLifecycleOwner, Observer { it ->
            messagesAdapter.onMessagesChanged(it)
        })
    }
}

/*
import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
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


/// A pretty version the text, with user icon to the left, name and time of arrival (copy slack look and feel)
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
                        style = MaterialTheme.typography.caption
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
            modifier = LayoutWeight(1f)
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


*/