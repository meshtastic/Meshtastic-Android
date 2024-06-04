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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.AdapterContactLayoutBinding
import com.geeksville.mesh.databinding.FragmentContactsBinding
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.ContactsViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ContactsFragment : ScreenFragment("Messages"), Logging {

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var _binding: FragmentContactsBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: ContactsViewModel by activityViewModels()

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

        var contacts = arrayOf<Contact>()
        var selectedList = ArrayList<String>()

        private val selectedContacts get() = contacts.filter { it.contactKey in selectedList }
        val isAllMuted get() = selectedContacts.all { it.isMuted }
        val selectedCount get() = selectedContacts.sumOf { it.messageCount }

        override fun getItemCount(): Int = contacts.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]

            holder.shortName.text = contact.shortName
            holder.longName.text = contact.longName
            holder.lastMessageText.text = contact.lastMessageText

            if (contact.lastMessageTime != null) {
                holder.lastMessageTime.visibility = View.VISIBLE
                holder.lastMessageTime.text = contact.lastMessageTime
            } else holder.lastMessageTime.visibility = View.INVISIBLE

            holder.mutedIcon.isVisible = contact.isMuted

            holder.itemView.setOnLongClickListener {
                clickItem(holder, contact.contactKey)
                if (actionMode == null) {
                    actionMode =
                        (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
                }
                true
            }
            holder.itemView.setOnClickListener {
                if (actionMode != null) clickItem(holder, contact.contactKey)
                else {
                    debug("calling MessagesFragment filter:${contact.contactKey}")
                    // TODO model.setContactKey(contact.contactKey)
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.mainActivityLayout, MessagesFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }

            if (selectedList.contains(contact.contactKey)) {
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

        fun onContactsChanged(contacts: List<Contact>) {
            this.contacts = contacts.toTypedArray()
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

        model.contactList.observe(viewLifecycleOwner) {
            debug("New contacts received: ${it.size}")
            contactsAdapter.onContactsChanged(it)
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
                    val selectedCount = contactsAdapter.selectedCount
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        selectedCount,
                        selectedCount
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            model.deleteContacts(contactsAdapter.selectedList.toList())
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
                            contactsAdapter.selectedList.add(it.contactKey)
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
