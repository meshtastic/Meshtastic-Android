package com.geeksville.mesh.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.ContactSettings
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.databinding.AdapterContactLayoutBinding
import com.geeksville.mesh.databinding.FragmentContactsBinding
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.Date
import java.util.concurrent.TimeUnit

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
        val mutedIcon = itemView.mutedIcon
    }

    private val contactsAdapter = object : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(requireContext())

            // Inflate the custom layout
            val contactsView = AdapterContactLayoutBinding.inflate(inflater, parent, false)

            // Return a new holder instance
            return ViewHolder(contactsView)
        }

        var contacts = arrayOf<Packet>()
        var selectedList = ArrayList<String>()

        var contactSettings = mapOf<String, ContactSettings>()
        val isAllMuted get() = selectedList.all { contactSettings[it]?.isMuted == true }

        override fun getItemCount(): Int = contacts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val packet = contacts[position]
            val contact = packet.data

            // Determine if this is my message (originated on this device)
            val fromLocal = contact.from == DataPacket.ID_LOCAL
            val toBroadcast = contact.to == DataPacket.ID_BROADCAST


            if (toBroadcast) {
                //grab channel names from DeviceConfig
                val channels = model.channelSet
                val channelName = if (channels.settingsCount > contact.channel)
                    Channel(channels.settingsList[contact.channel], channels.loraConfig).name else null

                holder.shortName.text = "${contact.channel}"
                holder.longName.text = channelName ?:  getString(R.string.channel_name)

                if (fromLocal) {
                    holder.lastMessageText.text = contact.text
                } else {
                    var node = model.nodeDB.getNode(contact.from!!)
                    holder.lastMessageText.text = "${node.user.shortName}: ${contact.text}"
                }

            } else {
                val node = if (fromLocal) model.nodeDB.getNode(contact.to!!) else model.nodeDB.getNode(contact.from!!)
                holder.shortName.text = node.user.shortName
                holder.longName.text = node.user.longName
                holder.lastMessageText.text = if (fromLocal) contact.text else "${node.user.shortName}: ${contact.text}"
            }


            if (contact.time != 0L) {
                holder.lastMessageTime.visibility = View.VISIBLE
                holder.lastMessageTime.text = getShortDateTime(Date(contact.time))
            } else holder.lastMessageTime.visibility = View.INVISIBLE

            holder.mutedIcon.isVisible = contactSettings[packet.contact_key]?.isMuted == true

            holder.itemView.setOnLongClickListener {
                clickItem(holder, packet.contact_key)
                if (actionMode == null) {
                    actionMode =
                        (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
                }
                true
            }
            holder.itemView.setOnClickListener {
                if (actionMode != null) clickItem(holder, packet.contact_key)
                else {
                    debug("calling MessagesFragment filter:${packet.contact_key}")
                    model.setContactKey(packet.contact_key)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.mainActivityLayout, MessagesFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }

            if (selectedList.contains(packet.contact_key)) {
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

        private fun clickItem(holder: ViewHolder, contactKey: String) {
            val position = holder.bindingAdapterPosition
            if (!selectedList.contains(contactKey)) {
                selectedList.add(contactKey)
            } else {
                selectedList.remove(contactKey)
            }
            if (selectedList.isEmpty()) {
                // finish action mode when no items selected
                actionMode?.finish()
            } else {
                // show total items selected on action mode title
                actionMode?.title = selectedList.size.toString()
            }
            actionMode?.invalidate()
            notifyItemChanged(position)
        }

        fun onContactsChanged(contacts: Map<String, Packet>) {
            this.contacts = contacts.values.toTypedArray()
            notifyDataSetChanged() // FIXME, this is super expensive and redraws all nodes
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

        model.nodeDB.nodes.asLiveData().observe(viewLifecycleOwner) {
            contactsAdapter.notifyDataSetChanged()
        }

        model.contacts.observe(viewLifecycleOwner) {
            debug("New contacts received: ${it.size}")
            contactsAdapter.onContactsChanged(it)
        }

        model.contactSettings.asLiveData().observe(viewLifecycleOwner) {
            contactsAdapter.contactSettings = it
            contactsAdapter.notifyDataSetChanged()
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
            menu.findItem(R.id.resendButton).isVisible = false
            mode.title = "1"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.findItem(R.id.muteButton).setIcon(
                if (contactsAdapter.isAllMuted) {
                    R.drawable.ic_twotone_volume_up_24
                } else {
                    R.drawable.ic_twotone_volume_off_24
                }
            )
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.muteButton -> if (contactsAdapter.isAllMuted) {
                    model.setMuteUntil(contactsAdapter.selectedList.toList(), 0L)
                    mode.finish()
                } else {
                    var muteUntil: Long = Long.MAX_VALUE
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.mute_notifications)
                        .setSingleChoiceItems(
                            setOf(
                                R.string.mute_8_hours,
                                R.string.mute_1_week,
                                R.string.mute_always,
                            ).map(::getString).toTypedArray(),
                            2
                        ) { _, which ->
                            muteUntil = when (which) {
                                0 -> System.currentTimeMillis() + TimeUnit.HOURS.toMillis(8)
                                1 -> System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
                                else -> Long.MAX_VALUE // always
                            }
                        }
                        .setPositiveButton(getString(R.string.okay)) { _, _ ->
                            debug("User clicked muteButton")
                            model.setMuteUntil(contactsAdapter.selectedList.toList(), muteUntil)
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }

                R.id.deleteButton -> {
                    val messagesTotal = model.packets.value.filter { it.port_num == 1 }
                    val selectedList = contactsAdapter.selectedList
                    val deleteList = ArrayList<Packet>()
                    // find messages for each contactId
                    selectedList.forEach { contact ->
                        deleteList += messagesTotal.filter { it.contact_key == contact }
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
                                model.deleteAllMessages()
                            } else {
                                model.deleteMessages(deleteList.map { it.uuid })
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
                            contactsAdapter.selectedList.add(it.contact_key)
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
