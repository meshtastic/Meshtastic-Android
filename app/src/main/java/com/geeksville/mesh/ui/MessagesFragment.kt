package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.adapter_message_layout.view.*
import kotlinx.android.synthetic.main.messages_fragment.*
import java.text.SimpleDateFormat
import java.util.*

// Allows usage like email.on(EditorInfo.IME_ACTION_NEXT, { confirm() })
fun EditText.on(actionId: Int, func: () -> Unit) {
    setOnEditorActionListener { _, receivedActionId, _ ->

        if (actionId == receivedActionId) {
            func()
        }

        true
    }
}

class MessagesFragment : ScreenFragment("Messages"), Logging {

    private val model: UIViewModel by activityViewModels()

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val username: Chip = itemView.username
        val messageText: TextView = itemView.messageText
        val messageTime: TextView = itemView.messageTime
        val messageStatusIcon: ImageView = itemView.messageStatusIcon
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
            val contactView: View = inflater.inflate(R.layout.adapter_message_layout, parent, false)

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
            val msg = messages[position]

            val nodes = model.nodeDB.nodes.value!!

            // If we can't find the sender, just use the ID
            val node = nodes.get(msg.from)
            val user = node?.user
            holder.username.text = user?.shortName ?: msg.from

            if (msg.errorMessage != null) {
                // FIXME, set the style to show a red error message
                holder.messageText.text = msg.errorMessage
            } else {
                holder.messageText.text = msg.text
            }

            holder.messageTime.text = dateFormat.format(Date(msg.time))

            val icon = when (msg.status) {
                MessageStatus.QUEUED -> R.drawable.ic_twotone_cloud_upload_24
                MessageStatus.DELIVERED -> R.drawable.cloud_on
                MessageStatus.ENROUTE -> R.drawable.ic_twotone_cloud_24
                MessageStatus.ERROR -> R.drawable.cloud_off
                else -> null
            }

            if (icon != null) {
                holder.messageStatusIcon.setImageResource(icon)
                holder.messageStatusIcon.visibility = View.VISIBLE
            } else
                holder.messageStatusIcon.visibility = View.INVISIBLE
        }

        private var messages = arrayOf<DataPacket>()

        /// Called when our node DB changes
        fun onMessagesChanged(msgIn: Collection<DataPacket>) {
            messages = msgIn.toTypedArray()
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all messages

            // scroll to the last line
            if (itemCount != 0)
                messageListView.scrollToPosition(itemCount - 1)
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

        messageInputText.on(EditorInfo.IME_ACTION_DONE) {
            debug("did IME action")

            val str = messageInputText.text.toString().trim()
            if (str.isNotEmpty())
                model.messagesState.sendMessage(str)
            messageInputText.setText("") // blow away the string the user just entered

            // requireActivity().hideKeyboard()
        }

        messageListView.adapter = messagesAdapter
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // We want the last rows to always be shown
        messageListView.layoutManager = layoutManager

        model.messagesState.messages.observe(viewLifecycleOwner, Observer { it ->
            messagesAdapter.onMessagesChanged(it)
        })

        // If connection state _OR_ myID changes we have to fix our ability to edit outgoing messages
        model.isConnected.observe(viewLifecycleOwner, Observer { connected ->
            // If we don't know our node ID and we are offline don't let user try to send
            textInputLayout.isEnabled =
                connected != MeshService.ConnectionState.DISCONNECTED && model.nodeDB.myId.value != null
        })

        model.nodeDB.myId.observe(viewLifecycleOwner, Observer { myId ->
            // If we don't know our node ID and we are offline don't let user try to send
            textInputLayout.isEnabled =
                model.isConnected.value != MeshService.ConnectionState.DISCONNECTED && myId != null
        })
    }

    private val dateFormat = SimpleDateFormat("h:mm a")
}

