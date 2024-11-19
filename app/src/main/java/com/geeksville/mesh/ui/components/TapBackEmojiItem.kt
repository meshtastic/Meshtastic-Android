package com.geeksville.mesh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geeksville.mesh.ui.theme.AppTheme

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
