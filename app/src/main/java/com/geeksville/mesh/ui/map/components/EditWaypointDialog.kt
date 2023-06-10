package com.geeksville.mesh.ui.map.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.IconButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import com.geeksville.mesh.MeshProtos.Waypoint
import com.geeksville.mesh.R
import com.geeksville.mesh.copy
import com.geeksville.mesh.ui.components.EditTextPreference
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.CustomRecentEmojiProvider
import com.geeksville.mesh.waypoint

@Composable
fun EditWaypointDialog(
    waypoint: Waypoint,
    onSendClicked: (Waypoint) -> Unit,
    onDeleteClicked: (Waypoint) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var waypointInput by remember { mutableStateOf(waypoint) }
    val title = if (waypoint.id == 0) R.string.waypoint_new else R.string.waypoint_edit
    val emoji = if (waypointInput.icon == 0) 128205 else waypointInput.icon
    var showEmojiPickerView by remember { mutableStateOf(false) }

    if (!showEmojiPickerView) AlertDialog(
        onDismissRequest = onDismissRequest,
        text = {
            Column(modifier = modifier.fillMaxWidth()) {
                Text(stringResource(title))
                EditTextPreference(title = stringResource(R.string.name),
                    value = waypointInput.name,
                    maxSize = 29, // name max_size:30
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { /*TODO*/ }),
                    onValueChanged = { waypointInput = waypointInput.copy { name = it } },
                    trailingIcon = {
                        IconButton(onClick = { showEmojiPickerView = true }) {
                            Text(String(Character.toChars(emoji)), fontSize = 24.sp)
                        }
                    },
                )
                EditTextPreference(title = stringResource(R.string.description),
                    value = waypointInput.description,
                    maxSize = 99, // description max_size:100
                    enabled = true,
                    isError = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Text, imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { /*TODO*/ }),
                    onValueChanged = { waypointInput = waypointInput.copy { description = it } }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .size(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_twotone_lock_24),
                        contentDescription = stringResource(R.string.locked),
                    )
                    Text(stringResource(R.string.locked))
                    Switch(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentWidth(Alignment.End),
                        checked = waypointInput.lockedTo != 0,
                        onCheckedChange = {
                            waypointInput =
                                waypointInput.copy { lockedTo = if (it) 1 else 0 }
                        }
                    )
                }
            }
        },
        buttons = {
            Row(
                modifier = modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = onDismissRequest
                ) { Text(stringResource(R.string.cancel)) }
                if (waypoint.id != 0) Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = { onDeleteClicked(waypointInput) },
                    enabled = waypointInput.name.isNotEmpty(),
                ) { Text(stringResource(R.string.delete)) }
                Button(
                    modifier = modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = { onSendClicked(waypointInput) },
                    enabled = waypointInput.name.isNotEmpty(),
                ) { Text(stringResource(R.string.send)) }
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) else AndroidView(
        factory = { context ->
            EmojiPickerView(context).apply {
                clipToOutline = true
                setRecentEmojiProvider(
                    RecentEmojiProviderAdapter(CustomRecentEmojiProvider(context))
                )
                setOnEmojiPickedListener { emoji ->
                    showEmojiPickerView = false
                    waypointInput = waypointInput.copy { icon = emoji.emoji.codePointAt(0) }
                }
            }
        },
        modifier = Modifier
            .fillMaxHeight(0.4f) // FIXME
            .background(colorResource(R.color.colorAdvancedBackground))
    )
}

@Preview(showBackground = true)
@Composable
fun EditWaypointFormPreview() {
    AppTheme {
        EditWaypointDialog(
            waypoint = waypoint {
                id = 123
                name = "Test 123"
                description = "This is only a test"
                icon = 128169
            },
            onSendClicked = { },
            onDeleteClicked = { },
            onDismissRequest = { },
        )
    }
}
