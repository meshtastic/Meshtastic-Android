package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ContactsFragment : ScreenFragment("Messages"), Logging {

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private val model: UIViewModel by activityViewModels()

    private val contacts get() = model.contactList.value
    private val selectedList = emptyList<String>().toMutableStateList()

    private val selectedContacts get() = contacts.filter { it.contactKey in selectedList }
    private val isAllMuted get() = selectedContacts.all { it.isMuted }
    private val selectedCount get() = selectedContacts.sumOf { it.messageCount }

    private fun onClick(contact: Contact) {
        if (actionMode != null) {
            onLongClick(contact)
        } else {
            debug("calling MessagesFragment filter:${contact.contactKey}")
            parentFragmentManager.navigateToMessages(contact.contactKey, contact.longName)
        }
    }

    private fun onLongClick(contact: Contact) {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }

        selectedList.apply {
            if (!remove(contact.contactKey)) add(contact.contactKey)
        }
        if (selectedList.isEmpty()) {
            // finish action mode when no items selected
            actionMode?.finish()
        } else {
            actionMode?.invalidate()
        }
    }

    override fun onPause() {
        actionMode?.finish()
        super.onPause()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val contacts by model.contactList.collectAsStateWithLifecycle()

                AppTheme {
                    ContactListView(
                        contacts = contacts,
                        selectedList = selectedList,
                        onClick = ::onClick,
                        onLongClick = ::onLongClick,
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        actionMode?.finish()
        actionMode = null
    }

    private inner class ActionModeCallback : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_messages, menu)
            menu.findItem(R.id.resendButton).isVisible = false
            mode.title = "1"
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            mode.title = selectedList.size.toString()
            menu.findItem(R.id.muteButton).setIcon(
                if (isAllMuted) {
                    R.drawable.ic_twotone_volume_up_24
                } else {
                    R.drawable.ic_twotone_volume_off_24
                }
            )
            return false
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.muteButton -> if (isAllMuted) {
                    model.setMuteUntil(selectedList.toList(), 0L)
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
                            model.setMuteUntil(selectedList.toList(), muteUntil)
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }

                R.id.deleteButton -> {
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        selectedCount,
                        selectedCount
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            model.deleteContacts(selectedList.toList())
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
                        selectedList.addAll(contacts.map { it.contactKey })
                        actionMode?.title = contacts.size.toString()
                    }
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedList.clear()
            actionMode = null
        }
    }
}

@Composable
fun ContactListView(
    contacts: List<Contact>,
    selectedList: List<String>,
    onClick: (Contact) -> Unit,
    onLongClick: (Contact) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
    ) {
        items(contacts, key = { it.contactKey }) { contact ->
            val selected by remember { derivedStateOf { selectedList.contains(contact.contactKey) } }

            ContactItem(
                contact = contact,
                selected = selected,
                onClick = { onClick(contact) },
                onLongClick = {
                    onLongClick(contact)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                },
            )
        }
    }
}
