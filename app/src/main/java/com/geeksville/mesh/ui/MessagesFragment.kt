/*
 * Copyright (c) 2024 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.geeksville.mesh.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.toMutableStateList
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.android.toast
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.databinding.MessagesFragmentBinding
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.model.getChannel
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.Utf8ByteLengthFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal fun FragmentManager.navigateToMessages(contactKey: String) {
    val messagesFragment = MessagesFragment().apply {
        arguments = bundleOf("contactKey" to contactKey)
    }
    beginTransaction()
        .add(R.id.mainActivityLayout, messagesFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class MessagesFragment : Fragment(), Logging {

    private val actionModeCallback: ActionModeCallback = ActionModeCallback()
    private var actionMode: ActionMode? = null
    private var _binding: MessagesFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    private lateinit var contactKey: String

    private val selectedList = emptyList<Message>().toMutableStateList()

    private fun onClick(message: Message) {
        if (actionMode != null) {
            onLongClick(message)
        }
    }

    private fun onLongClick(message: Message) {
        if (actionMode == null) {
            actionMode = (activity as AppCompatActivity).startSupportActionMode(actionModeCallback)
        }
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
        _binding = MessagesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        contactKey = arguments?.getString("contactKey").toString()
        val channelIndex = contactKey[0].digitToIntOrNull()
        val nodeId = contactKey.substring(1)
        val channelName = channelIndex?.let { model.channels.value.getChannel(it)?.name }
            ?: "Unknown Channel"

        binding.toolbar.title = when (nodeId) {
            DataPacket.ID_BROADCAST -> channelName
            else -> model.getUser(nodeId).longName
        }

        if (channelIndex == DataPacket.PKC_CHANNEL_INDEX) {
            binding.toolbar.title = "${binding.toolbar.title}🔒"
        } else if (nodeId != DataPacket.ID_BROADCAST) {
            binding.toolbar.subtitle = "(ch: $channelIndex - $channelName)"
        }

        fun sendMessageInputText() {
            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty()) {
                model.sendMessage(str, contactKey)
            }
            binding.messageInputText.setText("") // blow away the string the user just entered
            // requireActivity().hideKeyboard()
        }

        binding.sendButton.setOnClickListener {
            debug("User clicked sendButton")
            sendMessageInputText()
        }

        // max payload length should be 237 bytes but anything over 200 becomes less reliable
        binding.messageInputText.filters += Utf8ByteLengthFilter(200)

        binding.messageListView.setContent {
            val messages by model.getMessagesFrom(contactKey).collectAsStateWithLifecycle(listOf())

            AppTheme {
                if (messages.isNotEmpty()) {
                    MessageListView(
                        messages = messages,
                        selectedList = selectedList,
                        onClick = ::onClick,
                        onLongClick = ::onLongClick,
                        onChipClick = ::openNodeInfo,
                        onUnreadChanged = { model.clearUnreadCount(contactKey, it) },
                    )
                }
            }
        }

        // If connection state _OR_ myID changes we have to fix our ability to edit outgoing messages
        model.connectionState.asLiveData().observe(viewLifecycleOwner) {
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

        model.quickChatActions.asLiveData().observe(viewLifecycleOwner) { actions ->
            actions?.let {
                // This seems kinda hacky it might be better to replace with a recycler view
                binding.quickChatLayout.removeAllViews()
                for (action in actions) {
                    val button = Button(context)
                    button.text = action.name
                    button.isEnabled = model.isConnected()
                    if (action.mode == QuickChatAction.Mode.Instant) {
                        button.backgroundTintList =
                            ContextCompat.getColorStateList(requireActivity(), R.color.colorMyMsg)
                    }
                    button.setOnClickListener {
                        if (action.mode == QuickChatAction.Mode.Append) {
                            val originalText = binding.messageInputText.text ?: ""
                            val needsSpace =
                                !originalText.endsWith(' ') && originalText.isNotEmpty()
                            val newText = buildString {
                                append(originalText)
                                if (needsSpace) append(' ')
                                append(action.message)
                            }
                            binding.messageInputText.setText(newText)
                            binding.messageInputText.setSelection(newText.length)
                        } else {
                            model.sendMessage(action.message, contactKey)
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
                    val deleteMessagesString = resources.getQuantityString(
                        R.plurals.delete_messages,
                        selectedList.size,
                        selectedList.size
                    )
                    MaterialAlertDialogBuilder(requireContext())
                        .setMessage(deleteMessagesString)
                        .setPositiveButton(getString(R.string.delete)) { _, _ ->
                            debug("User clicked deleteButton")
                            model.deleteMessages(selectedList.map { it.uuid })
                            mode.finish()
                        }
                        .setNeutralButton(R.string.cancel) { _, _ ->
                        }
                        .show()
                }
                R.id.selectAllButton -> lifecycleScope.launch {
                    model.getMessagesFrom(contactKey).firstOrNull()?.let { messages ->
                        if (selectedList.size == messages.size) {
                            // if all selected -> unselect all
                            selectedList.clear()
                            mode.finish()
                        } else {
                            // else --> select all
                            selectedList.clear()
                            selectedList.addAll(messages)
                        }
                        actionMode?.title = selectedList.size.toString()
                    }
                }
                R.id.resendButton -> lifecycleScope.launch {
                    debug("User clicked resendButton")
                    var resendText = getSelectedMessagesText()
                    binding.messageInputText.setText(resendText)
                    mode.finish()
                }
                R.id.copyButton -> lifecycleScope.launch {
                    var copyText = getSelectedMessagesText()
                    val clipboardManager = requireActivity().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("message text", copyText))
                    requireActivity().toast(getString(R.string.copied))
                    mode.finish()
                }
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            selectedList.clear()
            actionMode = null
        }
    }

    private fun openNodeInfo(msg: Message) = lifecycleScope.launch {
        model.nodeList.firstOrNull()?.find { it.user.id == msg.user.id }?.let { node ->
            parentFragmentManager.popBackStack()
            model.focusUserNode(node)
        }
    }

    private fun getSelectedMessagesText(): String {
        var messageText = ""
        selectedList.forEach {
            messageText = messageText + it.text + System.lineSeparator()
        }
        if (messageText != "") {
            messageText = messageText.substring(0, messageText.length - 1)
        }
        return messageText
    }
}
