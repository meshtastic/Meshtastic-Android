package com.geeksville.mesh.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.geeksville.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.QuickChatSettingsFragmentBinding
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatSettingsFragment : ScreenFragment("Quick Chat settings"), Logging {
    private var _binding: QuickChatSettingsFragmentBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QuickChatSettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        Log.d(TAG, "viewCreated")

        binding.quickChatSettingsCreateButton.setOnClickListener {
            Log.d(TAG, "Create quick chat")

            val builder = createEditDialog(requireContext(), "New quick chat")

            builder.builder.setPositiveButton("Add") { view, x ->

                val name = builder.nameInput.text.toString().trim()
                val message = builder.messageInput.text.toString()
                if (builder.isNotEmpty())
                    model.addQuickChatAction(
                        name, message,
                        if (builder.modeSwitch.isChecked) QuickChatAction.Mode.Instant else QuickChatAction.Mode.Append
                    )
            }

            val dialog = builder.builder.create()
            dialog.show()
        }

        val quickChatActionAdapter =
            QuickChatActionAdapter(requireContext()) { action: QuickChatAction ->
                val builder = createEditDialog(requireContext(), "Edit quick chat")
                builder.nameInput.setText(action.name)
                builder.messageInput.setText(action.message)
                builder.modeSwitch.isChecked = action.mode == QuickChatAction.Mode.Instant

                builder.builder.setNegativeButton(R.string.delete) { _, _ ->
                    model.deleteQuickChatAction(action)
                }
                builder.builder.setPositiveButton(R.string.save_btn) { _, _ ->
                    if (builder.isNotEmpty()) {
                        model.updateQuickChatAction(
                            action,
                            builder.nameInput.text.toString(),
                            builder.messageInput.text.toString(),
                            if (builder.modeSwitch.isChecked) QuickChatAction.Mode.Instant else QuickChatAction.Mode.Append
                        )
                    }
                }
                val dialog = builder.builder.create()
                dialog.show()
            }

        val dragCallback = DragManageAdapter(quickChatActionAdapter, requireContext(), ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        val helper = ItemTouchHelper(dragCallback)

        binding.quickChatSettingsView.apply {
            this.layoutManager = LinearLayoutManager(requireContext())
            this.adapter = quickChatActionAdapter
            helper.attachToRecyclerView(this)
        }


        model.quickChatActions.asLiveData().observe(viewLifecycleOwner) { actions ->
            actions?.let { quickChatActionAdapter.setActions(actions) }
        }

        Log.d(TAG, "viewCreation done")
    }

    data class DialogBuilder(
        val builder: MaterialAlertDialogBuilder,
        val nameInput: EditText,
        val messageInput: EditText,
        val modeSwitch: SwitchMaterial
    ) {
        fun isNotEmpty(): Boolean = nameInput.text.isNotEmpty() and messageInput.text.isNotEmpty()
    }

    private fun getMessageName(message: String): String {
        return if (message.length <= 3) {
            message.uppercase()
        } else {
            buildString {
                append(message.first().uppercase())
                append(message[message.length / 2].uppercase())
                append(message.last().uppercase())
            }
        }
    }

    private fun createEditDialog(context: Context, title: String): DialogBuilder {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)

        val layout =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_quick_chat, null)

        val nameInput: EditText = layout.findViewById(R.id.addQuickChatName)
        val messageInput: EditText = layout.findViewById(R.id.addQuickChatMessage)
        val modeSwitch: SwitchMaterial = layout.findViewById(R.id.addQuickChatMode)

        var nameHasChanged = false

        modeSwitch.setOnCheckedChangeListener { _, _ ->
            modeSwitch.setText(if (modeSwitch.isChecked) R.string.mode_instant else R.string.mode_append)
        }

        messageInput.addTextChangedListener { text ->
            if (!nameHasChanged) {
                nameInput.setText(getMessageName(text.toString()))
            }
        }

        nameInput.addTextChangedListener {
            if (nameInput.isFocused) nameHasChanged = true
        }

        builder.setView(layout)

        return DialogBuilder(builder, nameInput, messageInput, modeSwitch)
    }
}