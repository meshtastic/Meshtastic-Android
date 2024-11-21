package com.geeksville.mesh.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Badge
import androidx.compose.material.BadgedBox
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProviderAdapter
import com.geeksville.mesh.database.entity.TapBack
import com.geeksville.mesh.ui.theme.AppTheme
import com.geeksville.mesh.util.CustomRecentEmojiProvider

@Composable
fun TapBackEmojiItem(
    emoji: String,
    isAddEmojiItem: Boolean = false,
    emojiCount: Int = 1,
    emojiTapped: () -> Unit = {},
) {
    BadgedBox(
        modifier = Modifier.padding(8.dp),
        badge = {
            if (emojiCount > 1) {
                Badge(
                    backgroundColor = MaterialTheme.colors.onBackground,
                    contentColor = MaterialTheme.colors.background,
                ) {
                    Text(
                        fontWeight = FontWeight.Bold,
                        text = emojiCount.toString()
                    )
                }
            }
        }
    ) {
        Surface(
            modifier = Modifier
                .clickable { emojiTapped() },
            color = MaterialTheme.colors.primary,
            shape = RoundedCornerShape(32.dp),
            elevation = 4.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isAddEmojiItem) {
                    Icon(
                        imageVector = Icons.TwoTone.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Text(
                    text = emoji,
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TapBackRow(
    fromLocal: Boolean,
    emojis: List<TapBack> = emptyList(),
    onSendTapBack: (String) -> Unit = { _ -> },
) {
    val emojiList by remember {
        mutableStateOf(
            reduceEmojis(
                if (fromLocal) {
                    emojis.map { it.emoji }
                } else {
                    emojis.map { it.emoji }.reversed()
                }
            ).entries
        )
    }
    var showEmojiPickerView by remember { mutableStateOf(false) }
    if (showEmojiPickerView) {
        EmojiPickerView(
            emojiSelected = {
                showEmojiPickerView = false
                onSendTapBack(it)
            },
            dismissPickerView = { showEmojiPickerView = false }
        )
    }
    @Composable
    fun AddEmojiItem() {
        TapBackEmojiItem(
            emoji = "\uD83D\uDE42",
            isAddEmojiItem = true,
            emojiTapped = {
                showEmojiPickerView = true
            }
        )
    }

    @Composable
    fun EmojiList() {
        emojiList.forEach { entry ->
            TapBackEmojiItem(
                emoji = entry.key,
                emojiCount = entry.value,
                emojiTapped = {
                    onSendTapBack(entry.key)
                }
            )
        }
    }

    if (fromLocal) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            EmojiList()
            AddEmojiItem()
        }
    } else {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            AddEmojiItem()
            EmojiList()
        }
    }
}

fun reduceEmojis(emojis: List<String>): Map<String, Int> {
    return emojis.groupingBy { it }.eachCount()
}

@Suppress("MagicNumber")
@Composable
fun EmojiPickerView(
    emojiSelected: (String) -> Unit,
    dismissPickerView: () -> Unit = {},
) {
    Dialog(
        onDismissRequest = { dismissPickerView() }
    ) {
        Column(
            verticalArrangement = Arrangement.Bottom
        ) {
            BackHandler {
                dismissPickerView()
            }
            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        clipToOutline = true
                        setRecentEmojiProvider(
                            RecentEmojiProviderAdapter(CustomRecentEmojiProvider(context))
                        )
                        setOnEmojiPickedListener { emoji ->
                            dismissPickerView()
                            emojiSelected(emoji.emoji)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .background(MaterialTheme.colors.background)
            )
        }
    }
}

@PreviewLightDark
@Composable
fun TapBackEmojiPreview() {
    AppTheme {
        Column(
            modifier = Modifier.background(MaterialTheme.colors.background)
        ) {
            TapBackEmojiItem(emoji = "\uD83D\uDE42")
            TapBackEmojiItem(emoji = "\uD83D\uDE42", emojiCount = 2)
            TapBackEmojiItem(emoji = "\uD83D\uDE42", isAddEmojiItem = true)
        }
    }
}

@Preview
@Composable
fun TapBackRowPreview() {
    AppTheme {

        TapBackRow(
            fromLocal = true, emojis = listOf(
                TapBack(
                    messageId = 1,
                    userId = "1",
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L
                ),
                TapBack(
                    messageId = 1,
                    userId = "1",
                    emoji = "\uD83D\uDE42",
                    timestamp = 1L
                ),
            )
        )
    }
}
