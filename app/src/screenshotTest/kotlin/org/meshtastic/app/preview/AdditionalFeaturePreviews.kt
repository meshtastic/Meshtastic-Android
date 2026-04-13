/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
@file:Suppress("TooManyFunctions", "MagicNumber")

package org.meshtastic.app.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.meshtastic.core.model.Node
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.ui.component.QrDialog
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.feature.node.component.NotesSection
import org.meshtastic.feature.settings.debugging.DebugActiveFilters
import org.meshtastic.feature.settings.debugging.FilterMode
import org.meshtastic.feature.settings.radio.ResponseState
import org.meshtastic.feature.settings.radio.component.PacketResponseStateDialog
import org.meshtastic.proto.User

// ---------------------------------------------------------------------------
// QrDialog previews
// ---------------------------------------------------------------------------

@MultiPreview
@Composable
fun QrDialogPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            QrDialog(
                title = "Share Channel",
                uriString = "https://meshtastic.org/e/#CgMSAR...",
                qrPainter = null,
                onDismiss = {},
            )
        }
    }
}

// ---------------------------------------------------------------------------
// NotesSection previews
// ---------------------------------------------------------------------------

@MultiPreview
@Composable
fun NotesSectionPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            NotesSection(
                node =
                Node(
                    num = 1955,
                    user = User(id = "mickeyMouseId", long_name = "Mickey Mouse", short_name = "MM"),
                    isFavorite = true,
                    notes = "Relay node on hilltop. Solar powered.",
                ),
                onSaveNotes = { _, _ -> },
            )
        }
    }
}

@MultiPreview
@Composable
fun NotesSectionEmptyPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface { NotesSection(node = Node(num = 1928, isFavorite = true), onSaveNotes = { _, _ -> }) }
    }
}

// ---------------------------------------------------------------------------
// PacketResponseStateDialog previews
// ---------------------------------------------------------------------------

@MultiPreview
@Composable
fun PacketResponseLoadingPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            PacketResponseStateDialog<Unit>(
                state = ResponseState.Loading(total = 5, completed = 2, status = "Sending config..."),
            )
        }
    }
}

@MultiPreview
@Composable
fun PacketResponseSuccessPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            PacketResponseStateDialog(state = ResponseState.Success(result = Unit))
        }
    }
}

@MultiPreview
@Composable
fun PacketResponseErrorPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Box(modifier = Modifier.fillMaxSize()) {
            PacketResponseStateDialog<Unit>(
                state = ResponseState.Error(error = UiText.DynamicString("Connection timed out")),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// DebugActiveFilters previews
// ---------------------------------------------------------------------------

@MultiPreview
@Composable
fun DebugActiveFiltersPreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                DebugActiveFilters(
                    filterTexts = listOf("GPS", "position", "telemetry"),
                    onFilterTextsChange = {},
                    filterMode = FilterMode.AND,
                    onFilterModeChange = {},
                )
            }
        }
    }
}

@MultiPreview
@Composable
fun DebugActiveFiltersOrModePreview() {
    AppTheme(isSystemInDarkTheme()) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                DebugActiveFilters(
                    filterTexts = listOf("error", "warning"),
                    onFilterTextsChange = {},
                    filterMode = FilterMode.OR,
                    onFilterModeChange = {},
                )
            }
        }
    }
}
