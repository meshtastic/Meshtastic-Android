package com.geeksville.mesh.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.databinding.QuickChatSettingsFragmentBinding
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.dragContainer
import com.geeksville.mesh.ui.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.components.rememberDragDropState
import com.geeksville.mesh.ui.theme.AppTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatSettingsFragment : ScreenFragment("Quick Chat Settings"), Logging {
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

        binding.quickChatSettingsToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.quickChatSettingsCreateButton.setOnClickListener {
            val builder = createEditDialog(requireContext(), R.string.quick_chat_new)

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

        binding.quickChatSettingsView.setContent {
            val actions by model.quickChatActions.collectAsStateWithLifecycle()

            val listState = rememberLazyListState()
            val dragDropState = rememberDragDropState(listState) { fromIndex, toIndex ->
                val list = actions.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
                model.updateActionPositions(list)
            }

            AppTheme {
                LazyColumn(
                    modifier = Modifier.dragContainer(
                        dragDropState = dragDropState,
                        haptics = LocalHapticFeedback.current,
                    ),
                    state = listState,
                    // contentPadding = PaddingValues(16.dp),
                ) {
                    dragDropItemsIndexed(
                        items = actions,
                        dragDropState = dragDropState,
                        key = { _, item -> item.uuid },
                    ) { _, action, isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 4.dp)
                        QuickChatItem(
                            elevation = elevation,
                            action = action,
                            onEditClick = ::onEditAction,
                        )
                    }
                }
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

    private fun createEditDialog(context: Context, @StringRes title: Int): DialogBuilder {
        val builder = MaterialAlertDialogBuilder(context)
        builder.setTitle(title)

        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_add_quick_chat, null)

        val nameInput: EditText = layout.findViewById(R.id.addQuickChatName)
        val messageInput: EditText = layout.findViewById(R.id.addQuickChatMessage)
        val modeSwitch: SwitchMaterial = layout.findViewById(R.id.addQuickChatMode)
        val instantImage: ImageView = layout.findViewById(R.id.addQuickChatInsant)
        instantImage.visibility = if (modeSwitch.isChecked) View.VISIBLE else View.INVISIBLE

        // don't change action name on edits
        var nameHasChanged = title == R.string.quick_chat_edit

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

    private fun onEditAction(action: QuickChatAction) {
        val builder = createEditDialog(requireContext(), R.string.quick_chat_edit)
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
                    if (builder.modeSwitch.isChecked) {
                        QuickChatAction.Mode.Instant
                    } else {
                        QuickChatAction.Mode.Append
                    }
                )
            }
        }
        val dialog = builder.builder.create()
        dialog.show()
    }
}

@Composable
internal fun QuickChatItem(
    action: QuickChatAction,
    modifier: Modifier = Modifier,
    onEditClick: (QuickChatAction) -> Unit = {},
    elevation: Dp = 4.dp,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = elevation,
        shape = RoundedCornerShape(12.dp),
    ) {
        Surface {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val showInstantIcon = action.mode == QuickChatAction.Mode.Instant
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_fast_forward_24),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp),
                    tint = if (showInstantIcon) LocalContentColor.current else Color.Transparent,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = action.name,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Text(
                        text = action.message,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                IconButton(
                    onClick = { onEditClick(action) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                        contentDescription = null
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_drag_handle_24),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun QuickChatItemPreview() {
    AppTheme {
        QuickChatItem(
            action = QuickChatAction(
                uuid = 0L,
                name = "TST",
                message = "Test",
                mode = QuickChatAction.Mode.Instant,
                position = 0,
            ),
        )
    }
}
