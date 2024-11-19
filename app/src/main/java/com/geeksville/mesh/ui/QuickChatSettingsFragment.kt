package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.R
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.components.dragContainer
import com.geeksville.mesh.ui.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.components.rememberDragDropState
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class QuickChatSettingsFragment : ScreenFragment("Quick Chat Settings"), Logging {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorAdvancedBackground))
            setContent {
                AppTheme {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(id = R.string.quick_chat)) },
                                navigationIcon = {
                                    IconButton(onClick = { parentFragmentManager.popBackStack() }) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            stringResource(id = R.string.navigate_back),
                                        )
                                    }
                                },
                            )
                        },
                    ) { innerPadding ->
                        QuickChatScreen(
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun QuickChatScreen(
    viewModel: UIViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val actions by viewModel.quickChatActions.collectAsStateWithLifecycle()
    var showActionDialog by remember { mutableStateOf<QuickChatAction?>(null) }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState) { fromIndex, toIndex ->
        val list = actions.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        viewModel.updateActionPositions(list)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showActionDialog != null) {
            val action = showActionDialog ?: return
            EditQuickChatDialog(
                action = action,
                onSave = viewModel::addQuickChatAction,
                onDelete = viewModel::deleteQuickChatAction,
            ) { showActionDialog = null }
        }

        FloatingActionButton(
            onClick = {
                showActionDialog = QuickChatAction(position = actions.size)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(id = R.string.add),
            )
        }

        LazyColumn(
            modifier = Modifier.dragContainer(
                dragDropState = dragDropState,
                haptics = LocalHapticFeedback.current,
            ),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            dragDropItemsIndexed(
                items = actions,
                dragDropState = dragDropState,
                key = { _, item -> item.uuid },
            ) { _, action, isDragging ->
                val elevation by animateDpAsState(
                    targetValue = if (isDragging) 8.dp else 4.dp,
                    label = "DragAndDropElevationAnimation",
                )
                QuickChatItem(
                    elevation = elevation,
                    action = action,
                    onEdit = { showActionDialog = it },
                )
            }
        }
    }
}

@Suppress("MagicNumber")
private fun getMessageName(message: String): String = if (message.length <= 3) {
    message.uppercase()
} else {
    buildString {
        append(message.first().uppercase())
        append(message[message.length / 2].uppercase())
        append(message.last().uppercase())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
private fun EditQuickChatDialog(
    action: QuickChatAction,
    onSave: (QuickChatAction) -> Unit,
    onDelete: (QuickChatAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var actionInput by remember { mutableStateOf(action) }
    val newQuickChat = action.uuid == 0L
    val isInstant = actionInput.mode == QuickChatAction.Mode.Instant
    val title = if (newQuickChat) R.string.quick_chat_new else R.string.quick_chat_edit

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        backgroundColor = MaterialTheme.colors.background,
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(id = title),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.h6.copy(
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextFieldWithCounter(
                    label = stringResource(R.string.name),
                    value = actionInput.name,
                    maxSize = 5,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                ) { actionInput = actionInput.copy(name = it.uppercase()) }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextFieldWithCounter(
                    label = stringResource(id = R.string.message),
                    value = actionInput.message,
                    maxSize = 235,
                    getSize = { it.toByteArray().size + 1 },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    actionInput = actionInput.copy(message = it)
                    if (newQuickChat) {
                        actionInput = actionInput.copy(name = getMessageName(it))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val (text, icon) = if (isInstant) {
                    R.string.quick_chat_instant to Icons.Default.FastForward
                } else {
                    R.string.quick_chat_append to Icons.Default.Add
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isInstant) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(id = text),
                        )
                        Spacer(Modifier.width(12.dp))
                    }

                    Text(
                        text = stringResource(text),
                        modifier = Modifier.weight(1f),
                    )

                    Switch(
                        checked = isInstant,
                        onCheckedChange = { checked ->
                            actionInput = actionInput.copy(
                                mode = when (checked) {
                                    true -> QuickChatAction.Mode.Instant
                                    false -> QuickChatAction.Mode.Append
                                }
                            )
                        },
                    )
                }
            }
        },
        buttons = {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                ) { Text(stringResource(R.string.cancel)) }

                if (!newQuickChat) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDelete(actionInput)
                            onDismiss()
                        },
                    ) { Text(text = stringResource(R.string.delete)) }
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSave(actionInput)
                        onDismiss()
                    },
                    enabled = actionInput.name.isNotEmpty() && actionInput.message.isNotEmpty(),
                ) { Text(text = stringResource(R.string.save)) }
            }
        },
    )
}

@Composable
private fun OutlinedTextFieldWithCounter(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxSize: Int,
    getSize: (String) -> Int = { it.length },
    onValueChange: (String) -> Unit = {},
) = Column(modifier) {
    var isFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = {
            if (getSize(it) <= maxSize) {
                onValueChange(it)
            }
        },
        modifier = Modifier.onFocusEvent { isFocused = it.isFocused },
        label = { Text(text = label) },
        singleLine = singleLine,
    )
    if (isFocused) {
        Text(
            text = "${getSize(value)}/$maxSize",
            style = MaterialTheme.typography.caption,
            modifier = Modifier
                .align(Alignment.End)
                .padding(top = 4.dp, end = 16.dp)
        )
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun QuickChatItem(
    action: QuickChatAction,
    modifier: Modifier = Modifier,
    onEdit: (QuickChatAction) -> Unit = {},
    elevation: Dp = 4.dp,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = elevation,
        shape = RoundedCornerShape(12.dp),
    ) {
        ListItem(
            icon = {
                if (action.mode == QuickChatAction.Mode.Instant) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = stringResource(id = R.string.quick_chat_instant),
                    )
                }
            },
            text = { Text(text = action.name) },
            secondaryText = { Text(text = action.message) },
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onEdit(action) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                            contentDescription = stringResource(id = R.string.quick_chat_edit),
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_drag_handle_24),
                        contentDescription = stringResource(id = R.string.quick_chat),
                    )
                }
            }
        )
    }
}

@PreviewLightDark
@Composable
private fun QuickChatItemPreview() {
    AppTheme {
        QuickChatItem(
            action = QuickChatAction(
                name = "TST",
                message = "Test",
                position = 0,
            ),
        )
    }
}

@PreviewLightDark
@Composable
private fun EditQuickChatDialogPreview() {
    AppTheme {
        EditQuickChatDialog(
            action = QuickChatAction(
                name = "TST",
                message = "Test",
                position = 0,
            ),
            onSave = {},
            onDelete = {},
            onDismiss = {}
        )
    }
}
