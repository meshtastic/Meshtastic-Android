/*
 * Copyright (c) 2025 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.okay

@Composable
fun SimpleAlertDialog(
    title: String,
    message: String?,
    html: String? = null,
    onDismissRequest: () -> Unit,
    onConfirmRequest: () -> Unit = onDismissRequest, // Default confirm to dismiss
) {
    val annotatedString =
        html?.let {
            AnnotatedString.fromHtml(
                html,
                linkStyles =
                TextLinkStyles(
                    style =
                    SpanStyle(
                        textDecoration = TextDecoration.Underline,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                ),
            )
        }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            if (annotatedString != null) {
                Text(text = annotatedString)
            } else {
                Text(text = message.orEmpty())
            }
        },
        confirmButton = { TextButton(onClick = onConfirmRequest) { Text(stringResource(Res.string.okay)) } },
    )
}

// For Rationale Dialogs
@Composable
fun MultipleChoiceAlertDialog(
    title: String,
    message: String?,
    choices: Map<String, () -> Unit>,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                message?.let { Text(text = it, modifier = Modifier.padding(bottom = 8.dp)) }
                choices.forEach { (choice, action) ->
                    Button(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        onClick = {
                            action()
                            onDismissRequest()
                        },
                    ) {
                        Text(text = choice)
                    }
                }
            }
        },
        confirmButton = {},
    )
}
