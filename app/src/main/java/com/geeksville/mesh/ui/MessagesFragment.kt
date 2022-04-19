package com.geeksville.mesh.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.MessageStatus
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdapterMessageLayoutBinding
import com.geeksville.mesh.databinding.MessagesFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
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

@AndroidEntryPoint
class MessagesFragment : Fragment(), Logging {

    private var actionMode: ActionMode? = null
    private var _binding: MessagesFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var contactId: String = DataPacket.ID_BROADCAST
    private var contactName: String = DataPacket.ID_BROADCAST

    private val model: UIViewModel by activityViewModels()

    // Allows textMultiline with IME_ACTION_SEND
    private fun EditText.onActionSend(func: () -> Unit) {
        imeOptions = EditorInfo.IME_ACTION_SEND
        InputType.TYPE_TEXT_FLAG_MULTI_LINE
        setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        setOnEditorActionListener { _, actionId, _ ->

            if (actionId == EditorInfo.IME_ACTION_SEND) {
                func()
            }
            true
        }
    }

    private val dateTimeFormat: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    private val timeFormat: DateFormat =
        DateFormat.getTimeInstance(DateFormat.SHORT)

    private fun getShortDateTime(time: Date): String {
        // return time if within 24 hours, otherwise date/time
        val oneDayMsec = 60 * 60 * 24 * 1000L
        return if (System.currentTimeMillis() - time.time > oneDayMsec) {
            dateTimeFormat.format(time)
        } else timeFormat.format(time)
    }

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
            val contactViewBinding = AdapterMessageLayoutBinding.inflate(inflater, parent, false)

            // Return a new holder instance
            return ViewHolder(contactViewBinding)
        }

        var messages = arrayOf<DataPacket>()
        var selectedList = ArrayList<DataPacket>()

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
            val node = nodes.get(msg.from)
            // Determine if this is my message (originated on this device)
            val isLocal = msg.from == DataPacket.ID_LOCAL
            val isBroadcast = (msg.to == DataPacket.ID_BROADCAST
                    || msg.delayed == 1) // MeshProtos.MeshPacket.Delayed.DELAYED_BROADCAST_VALUE == 1

            // Filter messages by contactId
            if (contactId == DataPacket.ID_BROADCAST) {
                if (isBroadcast) {
                    holder.card.visibility = View.VISIBLE
                } else holder.card.visibility = View.GONE
            } else {
                if (msg.from == contactId && msg.to != DataPacket.ID_BROADCAST
                        || msg.from == DataPacket.ID_LOCAL && msg.to == contactId) {
                    holder.card.visibility = View.VISIBLE
                } else holder.card.visibility = View.GONE
            }

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
            }
            if (msg.errorMessage != null) {
                context?.let { holder.card.setCardBackgroundColor(Color.RED) }
                holder.messageText.text = msg.errorMessage
            } else {
                holder.messageText.text = msg.text
            }

            holder.messageTime.text = getShortDateTime(Date(msg.time))

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

            holder.itemView.setOnLongClickListener {
                if (actionMode == null) {
                    actionMode = (activity as MainActivity).startActionMode(object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            mode.menuInflater.inflate(R.menu.menu_messages, menu)
                            mode.title = "1"
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                            clickItem(holder)
                            return true
                        }

                        override fun onActionItemClicked(
                            mode: ActionMode,
                            item: MenuItem
                        ): Boolean {
                            when (item.itemId) {
                                R.id.deleteButton -> {
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
                                            if (selectedList.size == messages.size) {
                                                model.messagesState.deleteAllMessages()
                                            } else {
                                                selectedList.forEach {
                                                    model.messagesState.deleteMessage(it)
                                                }
                                            }
                                            mode.finish()
                                        }
                                        .setNeutralButton(R.string.cancel) { _, _ ->
                                        }
                                        .show()
                                }
                                R.id.selectAllButton -> {
                                    // filter messages by ContactId
                                    val messagesByContactId = messages.filter {
                                        if (contactId == DataPacket.ID_BROADCAST)
                                            it.to == DataPacket.ID_BROADCAST
                                        else
                                            it.from == contactId && it.to != DataPacket.ID_BROADCAST
                                                    || it.from == DataPacket.ID_LOCAL && it.to == contactId
                                    }
                                    // if all selected -> unselect all
                                    if (selectedList.size == messagesByContactId.size) {
                                        selectedList.clear()
                                        mode.finish()
                                    } else {
                                        // else --> select all
                                        selectedList.clear()
                                        selectedList.addAll(messagesByContactId)
                                    }
                                    actionMode?.title = selectedList.size.toString()
                                    notifyDataSetChanged()
                                }
                                R.id.resendButton -> {
                                    debug("User clicked resendButton")
                                    var resendText = ""
                                    selectedList.forEach {
                                        resendText = resendText + it.text + System.lineSeparator()
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
                            selectedList.clear()
                            notifyDataSetChanged()
                            actionMode = null
                        }
                    })
                } else {
                    // when action mode is enabled
                    clickItem(holder)
                }
                true
            }
            holder.itemView.setOnClickListener {
                if (actionMode != null) clickItem(holder)
            }

            if (selectedList.contains(msg)) {
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

        private fun clickItem(holder: ViewHolder) {
            val position = holder.bindingAdapterPosition
            if (!selectedList.contains(messages[position])) {
                selectedList.add(messages[position])
            } else {
                selectedList.remove(messages[position])
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
        fun onMessagesChanged(msgIn: Collection<DataPacket>) {
            messages = msgIn.toTypedArray()
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all messages

            // scroll to the last line
            if (itemCount != 0)
                binding.messageListView.scrollToPosition(itemCount - 1)
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

        setFragmentResultListener("requestKey") { _, bundle->
            // get the result from bundle
            contactId = bundle.getString("contactId").toString()
            contactName = bundle.getString("contactName").toString()
            binding.messageTitle.text = contactName
        }

        binding.sendButton.setOnClickListener {
            debug("User clicked sendButton")

            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty())
                model.messagesState.sendMessage(str, contactId)
            binding.messageInputText.setText("") // blow away the string the user just entered

            // requireActivity().hideKeyboard()
        }

        binding.messageInputText.onActionSend {
            debug("did IME action")

            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty())
                model.messagesState.sendMessage(str)
            binding.messageInputText.setText("") // blow away the string the user just entered

            // requireActivity().hideKeyboard()
        }

        binding.messageListView.adapter = messagesAdapter
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // We want the last rows to always be shown
        binding.messageListView.layoutManager = layoutManager

        model.messagesState.messages.observe(viewLifecycleOwner) {
            debug("New messages received: ${it.size}")
            messagesAdapter.onMessagesChanged(it)
        }

        // If connection state _OR_ myID changes we have to fix our ability to edit outgoing messages
        model.isConnected.observe(viewLifecycleOwner) { connectionState ->
            // If we don't know our node ID and we are offline don't let user try to send
            val connected = connectionState == MeshService.ConnectionState.CONNECTED
            binding.textInputLayout.isEnabled = connected
            binding.sendButton.isEnabled = connected

            // Just being connected is enough to allow sending texts I think
            // && model.nodeDB.myId.value != null && model.radioConfig.value != null
        }
    }
}
