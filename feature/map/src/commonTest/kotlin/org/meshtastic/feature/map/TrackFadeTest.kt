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
package org.meshtastic.feature.map

import kotlin.test.Test
import kotlin.test.assertEquals

class TrackFadeTest {

    @Test
    fun `recency follows time across the track span`() {
        assertEquals(listOf(0f, 0.9f, 1f), trackRecency(listOf(1_000L, 10_000L, 11_000L)))
    }

    @Test
    fun `a track with an unknown time fades by order`() {
        assertEquals(listOf(0f, 0.5f, 1f), trackRecency(listOf(1_000L, null, 11_000L)))
        assertEquals(listOf(0f, 0.5f, 1f), trackRecency(listOf(0L, 5_000L, 11_000L)))
    }

    @Test
    fun `a track whose fixes share one time fades by order`() {
        assertEquals(listOf(0f, 1f), trackRecency(listOf(500L, 500L)))
    }

    @Test
    fun `a single point is fully recent`() {
        assertEquals(listOf(1f), trackRecency(listOf(500L)))
        assertEquals(emptyList(), trackRecency(emptyList()))
    }

    @Test
    fun `the fade runs from the oldest alpha to solid`() {
        assertEquals(OLDEST_TRACK_ALPHA, trackFadeAlpha(0f))
        assertEquals(1f, trackFadeAlpha(1f))
    }

    @Test
    fun `the newest point lands in the top level and the oldest in the bottom`() {
        assertEquals(TRACK_FADE_LEVELS - 1, trackFadeLevel(1f))
        assertEquals(0, trackFadeLevel(0f))
        assertEquals(1f, trackFadeLevelAlpha(TRACK_FADE_LEVELS - 1))
        assertEquals(OLDEST_TRACK_ALPHA, trackFadeLevelAlpha(0))
    }
}
