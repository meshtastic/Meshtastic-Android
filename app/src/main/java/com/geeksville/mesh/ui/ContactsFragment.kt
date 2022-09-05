package com.geeksville.mesh.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.DataPacket
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

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var _binding: FragmentContactsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(requireContext())

            // Inflate the custom layout
            val contactsView = AdapterContactLayoutBinding.inflate(inflater, parent, false)

            // Return a new holder instance
            return ViewHolder(contactsView)
        }

        var contacts = arrayOf<DataPacket>()
        var selectedList = ArrayList<String>()

        override fun getItemCount(): Int = contacts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]

            // Determine if this is my message (originated on this device)
            val isLocal = contact.from == DataPacket.ID_LOCAL
            val isBroadcast = contact.to == DataPacket.ID_BROADCAST
            val contactId = if (isLocal || isBroadcast) contact.to else contact.from

            // grab usernames from NodeInfo
            val nodes = model.nodeDB.nodes.value!!
            val node = nodes[if (isLocal) contact.to else contact.from]

            //grab channel names from DeviceConfig
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
                clickItem(holder, contactId)
                if (actionMode == null) {
                    actionMode =
                        (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
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

        private fun clickItem(holder: ViewHolder, contactId: String? = DataPacket.ID_BROADCAST) {
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
    }

    private inner class ActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_messages, menu)
            menu.findItem(R.id.resendButton).isVisible = false
            mode.title = "1"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.deleteButton -> {
                    val messagesTotal = model.messagesState.messages.value!!
                    val selectedList = contactsAdapter.selectedList
                    val deleteList = ArrayList<DataPacket>()
                    // find messages for each contactId
                    selectedList.forEach { contactId ->
                        deleteList += messagesTotal.filter {
                            if (contactId == DataPacket.ID_BROADCAST) it.to == DataPacket.ID_BROADCAST
                            else it.from == contactId && it.to != DataPacket.ID_BROADCAST || it.from == DataPacket.ID_LOCAL && it.to == contactId
                        }
                    }
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        deleteList.size,
                        deleteList.size
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            // all items selected --> deleteAllMessages()
                            if (deleteList.size == messagesTotal.size) {
                                model.messagesState.deleteAllMessages()
                            } else {
                                model.messagesState.deleteMessages(deleteList)
                            }
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }
                R.id.selectAllButton -> {
                    // if all selected -> unselect all
                    if (contactsAdapter.selectedList.size == contactsAdapter.contacts.size) {
                        contactsAdapter.selectedList.clear()
                        mode.finish()
                    } else {
                        // else --> select all
                        contactsAdapter.selectedList.clear()
                        contactsAdapter.contacts.forEach {
                            if (it.from == DataPacket.ID_LOCAL || it.to == DataPacket.ID_BROADCAST)
                                contactsAdapter.selectedList.add(it.to!!)
                            else contactsAdapter.selectedList.add(it.from!!)
                        }
                    }
                    actionMode?.title = contactsAdapter.selectedList.size.toString()
                    contactsAdapter.notifyDataSetChanged()
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            contactsAdapter.selectedList.clear()
            contactsAdapter.notifyDataSetChanged()
            actionMode = null
        }
    }
}
