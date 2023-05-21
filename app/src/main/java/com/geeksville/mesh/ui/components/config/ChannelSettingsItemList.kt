package com.geeksville.mesh.ui.components.config

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Card
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ChannelProtos.ChannelSettings
import com.geeksville.mesh.R
import com.geeksville.mesh.channelSettings
import com.geeksville.mesh.model.Channel
import com.geeksville.mesh.ui.components.PreferenceCategory
import com.geeksville.mesh.ui.components.PreferenceFooter

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ChannelCard(
    index: Int,
    title: String,
    enabled: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = enabled) { onEditClick() },
        elevation = 4.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Chip(onClick = onEditClick) { Text("$index") }
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (!enabled) MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled) else Color.Unspecified,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onDeleteClick() }) {
                Icon(
                    Icons.TwoTone.Close,
                    stringResource(R.string.delete),
                    modifier = Modifier.wrapContentSize(),
                )
            }
        }
    }
}

@Composable
fun ChannelSettingsItemList(
    settingsList: List<ChannelSettings>,
    maxChannels: Int = 8,
    enabled: Boolean,
    focusManager: FocusManager,
    onSaveClicked: (List<ChannelSettings>) -> Unit,
) {
    ChannelSettingsItemList(
        settingsList = settingsList,
        maxChannels = maxChannels,
        enabled = enabled,
        focusManager = focusManager,
        onPositiveClicked = onSaveClicked,
        onNegativeClicked = { }
    )
}

@Composable
fun ChannelSettingsItemList(
    settingsList: List<ChannelSettings>,
    modemPresetName: String = "Default",
    maxChannels: Int = 8,
    enabled: Boolean,
    focusManager: FocusManager,
    onNegativeClicked: () -> Unit,
    @StringRes positiveText: Int = R.string.send,
    onPositiveClicked: (List<ChannelSettings>) -> Unit,
    ) {
    val settingsListInput = remember {
        mutableStateListOf<ChannelSettings>().apply { addAll(settingsList) }
    }
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
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            item { PreferenceCategory(text = "Channels") }

            itemsIndexed(settingsListInput) { index, channel ->
                ChannelCard(
                    index = index,
                    title = channel.name.ifEmpty { modemPresetName },
                    enabled = enabled,
                    onEditClick = { showEditChannelDialog = index },
                    onDeleteClick = { settingsListInput.removeAt(index) }
                )
            }

            item {
                PreferenceFooter(
                    // FIXME workaround until we use navigation in ChannelFragment
                    enabled = positiveText != R.string.send
                            || !settingsListInput.containsAll(settingsList)
                            || !settingsList.containsAll(settingsListInput),
                    negativeText = R.string.cancel,
                    onNegativeClicked = {
                        focusManager.clearFocus()
                        settingsListInput.clear()
                        settingsListInput.addAll(settingsList)
                        onNegativeClicked()
                    },
                    positiveText = positiveText,
                    onPositiveClicked = { onPositiveClicked(settingsListInput) }
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
                    settingsListInput.add(channelSettings {
                        psk = Channel.default.settings.psk
                    })
                    showEditChannelDialog = settingsListInput.lastIndex
                },
                modifier = Modifier.padding(16.dp)
            ) { Icon(Icons.TwoTone.Add, stringResource(R.string.add)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ChannelSettingsPreview() {
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
        focusManager = LocalFocusManager.current,
        onSaveClicked = { },
    )
}
