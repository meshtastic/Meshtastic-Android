/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.web.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.web_database_asking_other_tab
import org.meshtastic.core.resources.web_database_busy
import org.meshtastic.core.resources.web_database_moved
import org.meshtastic.core.resources.web_database_other_tab_silent
import org.meshtastic.core.resources.web_database_use_here
import org.meshtastic.core.ui.theme.AppTheme
import kotlin.time.Duration.Companion.seconds

/** A holder that answers reloads within a second or two; past this the asking tab stops waiting on it. */
private val HOLDER_ANSWER_TIMEOUT = 6.seconds

/** Shown while the database worker starts, before this tab knows whether it holds the database. */
@Composable
internal fun WebLoading() {
    StartupSurface { CircularProgressIndicator() }
}

/** Shown instead of the app while this tab cannot hold the database; nothing has opened it yet. */
@Composable
internal fun DatabaseUnavailable(message: StringResource) {
    StartupSurface { StartupMessage(message) }
}

/**
 * Another tab holds the database. This tab gets it on its own once that tab closes; "Use here" asks that tab to let go
 * now, and if it does not answer, says to close it. [alreadyAsked] is true when the user asked from an earlier screen.
 */
@Composable
internal fun DatabaseBusy(alreadyAsked: Boolean, onUseHere: () -> Unit) {
    var asked by remember { mutableStateOf(alreadyAsked) }
    var unanswered by remember { mutableStateOf(false) }
    if (asked) {
        LaunchedEffect(Unit) {
            delay(HOLDER_ANSWER_TIMEOUT)
            unanswered = true
        }
    }
    StartupSurface {
        when {
            unanswered -> StartupMessage(Res.string.web_database_other_tab_silent)

            asked -> {
                CircularProgressIndicator()
                StartupMessage(Res.string.web_database_asking_other_tab)
            }

            else -> {
                StartupMessage(Res.string.web_database_busy)
                Button(
                    onClick = {
                        asked = true
                        onUseHere()
                    },
                ) {
                    Text(stringResource(Res.string.web_database_use_here))
                }
            }
        }
    }
}

/** This tab gave the database to another one; "Use here" takes it back the same way. */
@Composable
internal fun MovedToAnotherTab(onUseHere: () -> Unit) {
    StartupSurface {
        StartupMessage(Res.string.web_database_moved)
        Button(onClick = onUseHere) { Text(stringResource(Res.string.web_database_use_here)) }
    }
}

@Composable
private fun StartupMessage(message: StringResource) {
    Text(
        text = stringResource(message),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 480.dp),
    )
}

@Composable
private fun StartupSurface(content: @Composable () -> Unit) {
    AppTheme(darkTheme = isSystemInDarkTheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        }
    }
}
