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
package org.meshtastic.feature.docs.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.koin.core.annotation.Single
import org.meshtastic.feature.docs.model.AIDocAssistantSessionState

/**
 * Global Chirpy conversation state shared across all docs panes.
 *
 * Registered as a Koin singleton so both the list and detail entries access the same conversation. Uses Compose
 * snapshot state so reads trigger recomposition automatically.
 */
@Single
class ChirpySessionHolder {
    var showSheet by mutableStateOf(false)
    var sessionState by mutableStateOf(AIDocAssistantSessionState())
}
