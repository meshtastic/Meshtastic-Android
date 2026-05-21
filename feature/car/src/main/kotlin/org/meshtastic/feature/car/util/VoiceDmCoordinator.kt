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
package org.meshtastic.feature.car.util

import org.koin.core.annotation.Factory

/**
 * Coordinates voice-initiated DM flow from NodeDashboard. When a user taps "Message" on a node detail screen, this
 * helper provides context for voice-first composition.
 */
@Factory
class VoiceDmCoordinator(
    private val fuzzyNodeNameResolver: FuzzyNodeNameResolver,
    private val ttsEngine: CarTtsEngine,
) {

    /** Initiates a voice DM to the specified node. Announces the target node name via TTS for confirmation. */
    fun initiateVoiceDm(nodeName: String) {
        ttsEngine.readAloud("System", "Composing message to $nodeName")
    }

    /** Resolves a spoken node name to a node number for voice-initiated DMs. */
    fun resolveSpokenTarget(
        spokenName: String,
        availableNodes: List<Pair<Int, String>>,
    ): FuzzyNodeNameResolver.ResolvedNode? = fuzzyNodeNameResolver.resolve(spokenName, availableNodes)
}
