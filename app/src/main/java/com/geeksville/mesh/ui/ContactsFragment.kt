package com.geeksville.mesh.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.MainActivity
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdapterContactLayoutBinding
import com.geeksville.mesh.databinding.FragmentContactsBinding
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.text.DateFormat
import java.util.*

@AndroidEntryPoint
class ContactsFragment : ScreenFragment("Messages"), Logging {

    private var actionMode: ActionMode? = null
    private var _binding: FragmentContactsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

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
    class ViewHolder(itemView: AdapterContactLayoutBinding) :
        RecyclerView.ViewHolder(itemView.root) {
        val shortName = itemView.shortName
        val longName = itemView.longName
        val lastMessageTime = itemView.lastMessageTime
        val lastMessageText = itemView.lastMessageText
    }

    private val contactsAdapter = object : RecyclerView.Adapter<ViewHolder>() {

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
            val contactsView = AdapterContactLayoutBinding.inflate(inflater, parent, false)

            // Return a new holder instance
            return ViewHolder(contactsView)
        }

        private var messages = arrayOf<DataPacket>()
        private var contacts = arrayOf<DataPacket>()
        private var selectedList = ArrayList<String>()

        /**
         * Returns the total number of items in the data set held by the adapter.
         *
         * @return The total number of items in this adapter.
         */
        override fun getItemCount(): Int = contacts.size

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
            val contact = contacts[position]

            // Determine if this is my message (originated on this device)
            val isLocal = contact.from == DataPacket.ID_LOCAL
            val isBroadcast = contact.to == DataPacket.ID_BROADCAST
            val contactId = if (isLocal || isBroadcast) contact.to else contact.from

            // grab usernames from NodeInfo
            val nodes = model.nodeDB.nodes.value!!
            val node = nodes[if (isLocal) contact.to else contact.from]

            //grab channel names from RadioConfig
            val channels = model.channels.value
            val primaryChannel = channels?.primaryChannel

            val shortName = node?.user?.shortName ?: "???"
            val longName =
                if (isBroadcast) primaryChannel?.name ?: getString(R.string.channel_name)
                else node?.user?.longName ?: getString(R.string.unknown_username)

            holder.shortName.text = if (isBroadcast) "All" else shortName
            holder.longName.text = longName

            val text = if (isLocal) contact.text else "$shortName: ${contact.text}"
            holder.lastMessageText.text = text

            if (contact.time != 0L) {
                holder.lastMessageTime.visibility = View.VISIBLE
                holder.lastMessageTime.text = getShortDateTime(Date(contact.time))
            } else holder.lastMessageTime.visibility = View.INVISIBLE

            holder.itemView.setOnLongClickListener {
                if (actionMode == null) {
                    actionMode =
                        (activity as MainActivity).startActionMode(object : ActionMode.Callback {
                            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                                mode.menuInflater.inflate(R.menu.menu_messages, menu)
                                mode.title = "1"
                                return true
                            }

                            override fun onPrepareActionMode(
                                mode: ActionMode,
                                menu: Menu
                            ): Boolean {
                                clickItem(holder, contactId)
                                return true
                            }

                            override fun onActionItemClicked(
                                mode: ActionMode,
                                item: MenuItem
                            ): Boolean {
                                when (item.itemId) {
                                    R.id.deleteButton -> {
                                        val messagesByContactId = ArrayList<DataPacket>()
                                        selectedList.forEach { contactId ->
                                            messagesByContactId += messages.filter {
                                                if (contactId == DataPacket.ID_BROADCAST)
                                                    it.to == DataPacket.ID_BROADCAST
                                                else
                                                    it.from == contactId && it.to != DataPacket.ID_BROADCAST
                                                            || it.from == DataPacket.ID_LOCAL && it.to == contactId
                                            }
                                        }
                                        val deleteMessagesString = resources.getQuantityString(
                                            R.plurals.delete_messages,
                                            messagesByContactId.size,
                                            messagesByContactId.size
                                        )
                                        MaterialAlertDialogBuilder(requireContext())
                                            .setMessage(deleteMessagesString)
                                            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                                                debug("User clicked deleteButton")
                                                // all items selected --> deleteAllMessages()
                                                if (messagesByContactId.size == messages.size) {
                                                    model.messagesState.deleteAllMessages()
                                                } else {
                                                    messagesByContactId.forEach {
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
                                        // if all selected -> unselect all
                                        if (selectedList.size == contacts.size) {
                                            selectedList.clear()
                                            mode.finish()
                                        } else {
                                            // else --> select all
                                            selectedList.clear()

                                            contacts.forEach {
                                                if (it.from == DataPacket.ID_LOCAL || it.to == DataPacket.ID_BROADCAST)
                                                    selectedList.add(it.to!!) else selectedList.add(it.from!!)
                                            }
                                        }
                                        actionMode?.title = selectedList.size.toString()
                                        notifyDataSetChanged()
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
                    clickItem(holder, contactId)
                }
                true
            }
            holder.itemView.setOnClickListener {
                if (actionMode != null) clickItem(holder, contactId)
                else {
                    debug("calling MessagesFragment filter:$contactId")
                    setFragmentResult(
                        "requestKey",
                        bundleOf("contactId" to contactId, "contactName" to longName)
                    )
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.mainActivityLayout, MessagesFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }

            if (selectedList.contains(contactId)) {
                holder.itemView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor(Color.rgb(127, 127, 127))
                }
            } else {
                holder.itemView.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 32f
                    setColor(
                        ContextCompat.getColor(
                            holder.itemView.context,
                            R.color.colorAdvancedBackground
                        )
                    )
                }
            }
        }

        private fun clickItem(
            holder: ViewHolder,
            contactId: String? = DataPacket.ID_BROADCAST
        ) {
            val position = holder.bindingAdapterPosition
            if (contactId != null && !selectedList.contains(contactId)) {
                selectedList.add(contactId)
            } else {
                selectedList.remove(contactId)
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

        /// Called when our contacts DB changes
        fun onContactsChanged(contactsIn: Collection<DataPacket>) {
            contacts = contactsIn.sortedByDescending { it.time }.toTypedArray()
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all nodes
        }

        /// Called when our message DB changes
        fun onMessagesChanged(msgIn: Collection<DataPacket>) {
            messages = msgIn.toTypedArray()
        }

        fun onChannelsChanged() {
            val oldBroadcast = contacts.find { it.to == DataPacket.ID_BROADCAST }
            if (oldBroadcast != null) {
                notifyItemChanged(contacts.indexOf(oldBroadcast))
            }
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
        _binding = FragmentContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.contactsView.adapter = contactsAdapter
        binding.contactsView.layoutManager = LinearLayoutManager(requireContext())

        model.channels.observe(viewLifecycleOwner) {
            contactsAdapter.onChannelsChanged()
        }

        model.nodeDB.nodes.observe(viewLifecycleOwner) {
            contactsAdapter.notifyDataSetChanged()
        }

        model.messagesState.contacts.observe(viewLifecycleOwner) {
            debug("New contacts received: ${it.size}")
            contactsAdapter.onContactsChanged(it.values)
        }

        model.messagesState.messages.observe(viewLifecycleOwner) {
            contactsAdapter.onMessagesChanged(it)
        }
    }
}
