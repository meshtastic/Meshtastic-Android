package com.geeksville.mesh.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.geeksville.android.Logging
import com.geeksville.mesh.databinding.MessagesFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.service.MeshService

// Allows usage like email.on(EditorInfo.IME_ACTION_NEXT, { confirm() })
fun EditText.on(actionId: Int, func: () -> Unit) {
    setImeOptions(EditorInfo.IME_ACTION_SEND) // Force "SEND" IME Action
    setRawInputType(InputType.TYPE_CLASS_TEXT) // Suppress ENTER but allow textMultiLine
    setOnEditorActionListener { _, receivedActionId, _ ->

        if (actionId == receivedActionId) {
            func()
        }

        true
    }
}

class MessagesFragment : ScreenFragment("Messages"), Logging {

    private var _binding: MessagesFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = MessagesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.sendButton.setOnClickListener {
            debug("sendButton click")

            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty())
                model.messagesState.sendMessage(str)
            binding.messageInputText.setText("") // blow away the string the user just entered

            // requireActivity().hideKeyboard()
        }

        binding.messageInputText.on(EditorInfo.IME_ACTION_SEND) {
            debug("did IME action")

            val str = binding.messageInputText.text.toString().trim()
            if (str.isNotEmpty())
                model.messagesState.sendMessage(str)
            binding.messageInputText.setText("") // blow away the string the user just entered

            // requireActivity().hideKeyboard()
        }
        val adapter = MessagesAdapter()
        binding.messageListView.adapter = adapter

        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true // We want the last rows to always be shown
        binding.messageListView.layoutManager = layoutManager

        model.messagesState.messages.observe(viewLifecycleOwner, Observer {
            debug("New messages received: ${it.size}")
            adapter.onMessagesChanged(it)
        })

        // If connection state _OR_ myID changes we have to fix our ability to edit outgoing messages
        fun updateTextEnabled() {
            binding.textInputLayout.isEnabled =
                model.isConnected.value != MeshService.ConnectionState.DISCONNECTED

        // Just being connected is enough to allow sending texts I think
        // && model.nodeDB.myId.value != null && model.radioConfig.value != null
        }

        model.isConnected.observe(viewLifecycleOwner, Observer { _ ->
            // If we don't know our node ID and we are offline don't let user try to send
            updateTextEnabled()
        })

        /* model.nodeDB.myId.observe(viewLifecycleOwner, Observer { _ ->
            // If we don't know our node ID and we are offline don't let user try to send
            updateTextEnabled()
        })

        model.radioConfig.observe(viewLifecycleOwner, Observer { _ ->
            // If we don't know our node ID and we are offline don't let user try to send
            updateTextEnabled()
        }) */
    }

}

