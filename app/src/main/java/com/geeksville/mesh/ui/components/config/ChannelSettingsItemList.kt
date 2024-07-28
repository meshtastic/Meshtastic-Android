package com.geeksville.mesh.ui.components.config

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.Chip
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter
import com.geeksville.mesh.ui.components.dragContainer
import com.geeksville.mesh.ui.components.dragDropItemsIndexed
import com.geeksville.mesh.ui.components.rememberDragDropState

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ChannelItem(
    index: Int,
    title: String,
    enabled: Boolean,
    onClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onClick() },
        elevation = elevation,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            val textColor = if (enabled) Color.Unspecified
            else MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)

            Chip(onClick = onClick) { Text("$index") }
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = textColor,
                style = MaterialTheme.typography.body1,
            )
            content()
        }
    }
}

@Composable
fun ChannelCard(
    index: Int,
    title: String,
    enabled: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    elevation: Dp = 4.dp,
) = ChannelItem(
    index = index,
    title = title,
    enabled = enabled,
    onClick = onEditClick,
    elevation = elevation,
) {
    IconButton(onClick = { onDeleteClick() }) {
        Icon(
            imageVector = Icons.TwoTone.Close,
            contentDescription = stringResource(R.string.delete),
            modifier = Modifier.wrapContentSize(),
        )
    }
}

@Composable
fun ChannelSelection(
    index: Int,
    title: String,
    enabled: Boolean,
    isSelected: Boolean,
    onSelected: (Boolean) -> Unit
) = ChannelItem(
    index = index,
    title = title,
    enabled = enabled,
    onClick = {},
) {
    Checkbox(
        enabled = enabled,
        checked = isSelected,
        onCheckedChange = onSelected,
    )
}

@Composable
fun ChannelSettingsItemList(
    settingsList: List<ChannelSettings>,
    modemPresetName: String = "Default",
    maxChannels: Int = 8,
    enabled: Boolean,
    onNegativeClicked: () -> Unit = { },
    onPositiveClicked: (List<ChannelSettings>) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val settingsListInput = remember(settingsList) { settingsList.toMutableStateList() }

    val listState = rememberLazyListState()
    val dragDropState = rememberDragDropState(listState, headerCount = 1) { fromIndex, toIndex ->
        if (toIndex in settingsListInput.indices && fromIndex in settingsListInput.indices) {
            settingsListInput.apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val isEditing: Boolean = settingsList.size != settingsListInput.size
            || settingsList.zip(settingsListInput).any { (item1, item2) -> item1 != item2 }

    var showEditChannelDialog: Int? by remember { mutableStateOf(null) }

    if (showEditChannelDialog != null) {
        val index = showEditChannelDialog ?: return
        EditChannelDialog(
            channelSettings = with(settingsListInput) {
                if (size > index) get(index) else channelSettings { }
            },
            modemPresetName = modemPresetName,
            onAddClick = {
                if (settingsListInput.size > index) settingsListInput[index] = it
                else settingsListInput.add(it)
                showEditChannelDialog = null
            },
            onDismissRequest = { showEditChannelDialog = null }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = { }, enabled = false)
    ) {
        LazyColumn(
            modifier = Modifier.dragContainer(
                dragDropState = dragDropState,
                haptics = LocalHapticFeedback.current,
            ),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            item { PreferenceCategory(text = "Channels") }

            dragDropItemsIndexed(
                items = settingsListInput,
                dragDropState = dragDropState,
            ) { index, channel, isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 4.dp, label = "drag")
                ChannelCard(
                    elevation = elevation,
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    onEditClick = { showEditChannelDialog = index },
                    onDeleteClick = { settingsListInput.removeAt(index) }
                )
            }

            item {
                PreferenceFooter(
                    enabled = isEditing,
                    negativeText = R.string.cancel,
                    onNegativeClicked = {
                        focusManager.clearFocus()
                        settingsListInput.clear()
                        settingsListInput.addAll(settingsList)
                        onNegativeClicked()
                    },
                    positiveText = R.string.send,
                    onPositiveClicked = {
                        focusManager.clearFocus()
                        onPositiveClicked(settingsListInput)
                    }
                )
            }
        }

        AnimatedVisibility(
            visible = maxChannels > settingsListInput.size,
            modifier = Modifier.align(Alignment.BottomEnd),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        ) {
            FloatingActionButton(
                onClick = {
                    if (maxChannels > settingsListInput.size) {
                        settingsListInput.add(channelSettings {
                            psk = Channel.default.settings.psk
                        })
                        showEditChannelDialog = settingsListInput.lastIndex
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) { Icon(Icons.TwoTone.Add, stringResource(R.string.add)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChannelSettingsPreview() {
    ChannelSettingsItemList(
        settingsList = listOf(
            channelSettings {
                psk = Channel.default.settings.psk
                name = Channel.default.name
            },
            channelSettings {
                name = stringResource(R.string.channel_name)
            },
        ),
        enabled = true,
        onPositiveClicked = { },
    )
}
