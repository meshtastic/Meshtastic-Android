package com.geeksville.mesh.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.NodeInfo
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.databinding.AdapterMessageLayoutBinding
import com.geeksville.mesh.databinding.MessagesFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.util.Utf8ByteLengthFilter
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.*

// return time if within 24 hours, otherwise date/time
internal fun getShortDateTime(date: Date): String {
    val isWithin24Hours = System.currentTimeMillis() - date.time <= 24 * 60 * 60 * 1000L

    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }
}

@AndroidEntryPoint
class MessagesFragment : Fragment(), Logging {

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var _binding: MessagesFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    // Provide a direct reference to each of the views within a data item
    // Used to cache the views within the item layout for fast access
    class ViewHolder(itemView: AdapterMessageLayoutBinding) :
        RecyclerView.ViewHolder(itemView.root) {
        val username: Chip = itemView.username
        val messageText: TextView = itemView.messageText
        val messageTime: TextView = itemView.messageTime
        val messageStatusIcon: ImageView = itemView.messageStatusIcon
        val card: CardView = itemView.Card
    }

    private val messagesAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(requireContext())

            // Inflate the custom layout
            val contactViewBinding = AdapterMessageLayoutBinding.inflate(inflater, parent, false)

            // Return a new holder instance
            return ViewHolder(contactViewBinding)
        }

        var messages = listOf<Packet>()
        var selectedList = ArrayList<Packet>()
        val layoutManager get() = binding.messageListView.layoutManager as LinearLayoutManager

        fun scrollToBottom() {
            if (itemCount > 0) layoutManager.scrollToPosition(itemCount - 1)
        }

        override fun getItemCount(): Int = messages.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val packet = messages[position]
            val msg = packet.data
            val nodes = model.nodeDB.nodes.value
            val node = nodes[msg.from]
            // Determine if this is my message (originated on this device)
            val isLocal = msg.from == DataPacket.ID_LOCAL

            // Set cardview offset and color.
            val marginParams = holder.card.layoutParams as ViewGroup.MarginLayoutParams
            val messageOffset = resources.getDimensionPixelOffset(R.dimen.message_offset)
            if (isLocal) {
                marginParams.leftMargin = messageOffset
                marginParams.rightMargin = 0
                holder.messageText.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
                context?.let {
                    holder.card.setCardBackgroundColor(
                        ContextCompat.getColor(
                            it,
                            R.color.colorMyMsg
                        )
                    )
                }
            } else {
                marginParams.rightMargin = messageOffset
                marginParams.leftMargin = 0
                holder.messageText.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                context?.let {
                    holder.card.setCardBackgroundColor(
                        ContextCompat.getColor(
                            it,
                            R.color.colorMsg
                        )
                    )
                }
            }

            // Hide the username chip for my messages
            if (isLocal) {
                holder.username.visibility = View.GONE
            } else {
                holder.username.visibility = View.VISIBLE
                // If we can't find the sender, just use the ID
                val user = node?.user
                holder.username.text = user?.shortName ?: msg.from

                holder.username.setOnClickListener {
                    node?.let { openNodeInfo(it) }
                }
            }

            if (msg.errorMessage != null) {
                context?.let { holder.card.setCardBackgroundColor(Color.RED) }
                holder.messageText.text = msg.errorMessage
            } else {
                holder.messageText.text = msg.text
            }

            holder.messageTime.text = getShortDateTime(Date(msg.time))

            val icon = when (msg.status) {
                MessageStatus.RECEIVED -> R.drawable.ic_twotone_how_to_reg_24
                MessageStatus.QUEUED -> R.drawable.ic_twotone_cloud_upload_24
                MessageStatus.DELIVERED -> R.drawable.cloud_on
                MessageStatus.ENROUTE -> R.drawable.ic_twotone_cloud_24
                MessageStatus.ERROR -> R.drawable.cloud_off
                else -> null
            }

            if (icon != null && isLocal) {
                holder.messageStatusIcon.setImageResource(icon)
                holder.messageStatusIcon.visibility = View.VISIBLE
            } else
                holder.messageStatusIcon.visibility = View.GONE

            holder.messageStatusIcon.setOnClickListener {
                if (isAdded) {
                    Toast.makeText(context, "${msg.status}", Toast.LENGTH_SHORT).show()
                }
            }

            holder.itemView.setOnLongClickListener {
                clickItem(position)
                if (actionMode == null) {
                    actionMode =
                        (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
                }
                true
            }
            holder.itemView.setOnClickListener {
                if (actionMode != null) clickItem(position)
            }

            if (selectedList.contains(packet)) {
                holder.itemView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor(Color.rgb(127, 127, 127))
                }
            } else {
                holder.itemView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor(ContextCompat.getColor(holder.itemView.context, R.color.colorAdvancedBackground))
                }
            }
        }

        private fun clickItem(position: Int) {
            val message = messages[position]
            selectedList.apply {
                if (contains(message)) remove(message) else add(message)
            }
            if (selectedList.isEmpty()) {
                // finish action mode when no items selected
                actionMode?.finish()
            } else {
                // show total items selected on action mode title
                actionMode?.title = selectedList.size.toString()
            }
            notifyItemChanged(position)
        }

        /// Called when our node DB changes
        fun onMessagesChanged(messages: List<Packet>) {
            val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
            val shouldScrollToBottom =
                lastVisibleItemPosition <= 0 || lastVisibleItemPosition == itemCount - 1

            this.messages = messages
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all messages

            if (shouldScrollToBottom) scrollToBottom()
        }
    }

    override fun onPause() {
        actionMode?.finish()
        super.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MessagesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        fun sendMessageInputText() {
            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty()) {
                model.sendMessage(str)
                messagesAdapter.scrollToBottom()
            }
            binding.messageInputText.setText("") // blow away the string the user just entered
            // requireActivity().hideKeyboard()
        }

        binding.sendButton.setOnClickListener {
            debug("User clicked sendButton")
            sendMessageInputText()
        }

        // max payload length should be 237 bytes but anything over 235 bytes crashes the radio
        binding.messageInputText.filters += Utf8ByteLengthFilter(234)

        binding.messageListView.adapter = messagesAdapter
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // We want the last rows to always be shown
        binding.messageListView.layoutManager = layoutManager

        model.messages.observe(viewLifecycleOwner) {
            if (it.isNotEmpty() && it.first().contact_key != model.contactKey.value) return@observe
            debug("New messages received: ${it.size}")
            messagesAdapter.onMessagesChanged(it)
        }

        // If connection state _OR_ myID changes we have to fix our ability to edit outgoing messages
        model.connectionState.observe(viewLifecycleOwner) {
            // If we don't know our node ID and we are offline don't let user try to send
            val isConnected = model.isConnected()
            binding.textInputLayout.isEnabled = isConnected
            binding.sendButton.isEnabled = isConnected
            for (subView: View in binding.quickChatLayout.allViews) {
                if (subView is Button) {
                    subView.isEnabled = isConnected
                }
            }
        }

        model.contactKey.asLiveData().observe(viewLifecycleOwner) {
            binding.messageTitle.text = model.getContactName(it)
        }

        model.quickChatActions.asLiveData().observe(viewLifecycleOwner) { actions ->
            actions?.let {
                // This seems kinda hacky it might be better to replace with a recycler view
                binding.quickChatLayout.removeAllViews()
                for (action in actions) {
                    val button = Button(context)
                    button.text = action.name
                    button.isEnabled = model.isConnected()
                    if (action.mode == QuickChatAction.Mode.Instant) {
                        button.backgroundTintList = ContextCompat.getColorStateList(requireActivity(), R.color.colorMyMsg)
                    }
                    button.setOnClickListener {
                        if (action.mode == QuickChatAction.Mode.Append) {
                            val originalText = binding.messageInputText.text ?: ""
                            val needsSpace = !originalText.endsWith(' ') && originalText.isNotEmpty()
                            val newText = buildString {
                                append(originalText)
                                if (needsSpace) append(' ')
                                append(action.message)
                            }
                            binding.messageInputText.setText(newText)
                            binding.messageInputText.setSelection(newText.length)
                        } else {
                            model.sendMessage(action.message)
                            messagesAdapter.scrollToBottom()
                        }
                    }
                    binding.quickChatLayout.addView(button)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        actionMode = null
        _binding = null
    }

    private inner class ActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_messages, menu)
            menu.findItem(R.id.muteButton).isVisible = false
            mode.title = "1"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.deleteButton -> {
                    val selectedList = messagesAdapter.selectedList
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        selectedList.size,
                        selectedList.size
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            // all items selected --> deleteAllMessages()
                            val messagesTotal = model.packets.value.filter { it.port_num == 1 }
                            if (selectedList.size == messagesTotal.size) {
                                model.deleteAllMessages()
                            } else {
                                model.deleteMessages(selectedList.map { it.uuid })
                            }
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }
                R.id.selectAllButton -> {
                    // if all selected -> unselect all
                    if (messagesAdapter.selectedList.size == messagesAdapter.messages.size) {
                        messagesAdapter.selectedList.clear()
                        mode.finish()
                    } else {
                        // else --> select all
                        messagesAdapter.selectedList.clear()
                        messagesAdapter.selectedList.addAll(messagesAdapter.messages)
                    }
                    actionMode?.title = messagesAdapter.selectedList.size.toString()
                    messagesAdapter.notifyDataSetChanged()
                }
                R.id.resendButton -> {
                    debug("User clicked resendButton")
                    val selectedList = messagesAdapter.selectedList
                    var resendText = ""
                    selectedList.forEach {
                        resendText = resendText + it.data.text + System.lineSeparator()
                    }
                    if (resendText!="")
                        resendText = resendText.substring(0, resendText.length - 1)
                    binding.messageInputText.setText(resendText)
                    mode.finish()
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            messagesAdapter.selectedList.clear()
            messagesAdapter.notifyDataSetChanged()
            actionMode = null
        }
    }

    private fun openNodeInfo(node: NodeInfo) {
        parentFragmentManager.popBackStack()
        model.focusUserNode(node)
    }

}
