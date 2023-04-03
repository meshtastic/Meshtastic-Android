package com.geeksville.mesh.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.databinding.QuickChatSettingsFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import java.util.*

@AndroidEntryPoint
class QuickChatSettingsFragment : ScreenFragment("Quick Chat Settings"), Logging {
    private var _binding: QuickChatSettingsFragmentBinding? = null

    private val binding get() = _binding!!

    private val model: UIViewModel by activityViewModels()

    private lateinit var actions: List<QuickChatAction>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QuickChatSettingsFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.quickChatSettingsCreateButton.setOnClickListener {
            val builder = createEditDialog(requireContext(), getString(R.string.quick_chat_new))

            builder.builder.setPositiveButton(R.string.add) { _, _ ->

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
            QuickChatActionAdapter(requireContext(), { action: QuickChatAction ->
                val builder = createEditDialog(requireContext(), getString(R.string.quick_chat_edit))
                builder.nameInput.setText(action.name)
                builder.messageInput.setText(action.message)
                val isInstant = action.mode == QuickChatAction.Mode.Instant
                builder.modeSwitch.isChecked = isInstant
                builder.instantImage.visibility = if (isInstant) View.VISIBLE else View.INVISIBLE

                builder.builder.setNegativeButton(R.string.delete) { _, _ ->
                    model.deleteQuickChatAction(action)
                }
                builder.builder.setPositiveButton(R.string.save) { _, _ ->
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
            }, { fromPos, toPos ->
                Collections.swap(actions, fromPos, toPos)
            }, {
                model.updateActionPositions(actions)
            })

        val dragCallback =
            DragManageAdapter(quickChatActionAdapter, ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
        val helper = ItemTouchHelper(dragCallback)

        binding.quickChatSettingsView.apply {
            this.layoutManager = LinearLayoutManager(requireContext())
            this.adapter = quickChatActionAdapter
            helper.attachToRecyclerView(this)
        }

        model.quickChatActions.asLiveData().observe(viewLifecycleOwner) { actions ->
            actions?.let {
                quickChatActionAdapter.setActions(actions)
                this.actions = actions
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class DialogBuilder(
        val builder: MaterialAlertDialogBuilder,
        val nameInput: EditText,
        val messageInput: EditText,
        val modeSwitch: SwitchMaterial,
        val instantImage: ImageView
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
        val instantImage: ImageView = layout.findViewById(R.id.addQuickChatInsant)
        instantImage.visibility = if (modeSwitch.isChecked) View.VISIBLE else View.INVISIBLE

        var nameHasChanged = false

        modeSwitch.setOnCheckedChangeListener { _, _ ->
            if (modeSwitch.isChecked) {
                modeSwitch.setText(R.string.quick_chat_instant)
                instantImage.visibility = View.VISIBLE
            } else {
                modeSwitch.setText(R.string.quick_chat_append)
                instantImage.visibility = View.INVISIBLE
            }
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

        return DialogBuilder(builder, nameInput, messageInput, modeSwitch, instantImage)
    }
}