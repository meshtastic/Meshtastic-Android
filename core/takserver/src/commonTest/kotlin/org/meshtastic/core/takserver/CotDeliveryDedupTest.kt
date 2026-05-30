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
package org.meshtastic.core.takserver

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CotDeliveryDedupTest {

    private var clock = Instant.fromEpochSeconds(1_000_000)

    @Test
    fun `first sighting admitted then immediate exact repeat dropped`() {
        val dedup = CotDeliveryDedup(ttl = 2.minutes, now = { clock })
        val xml = """<event uid="TAKTALK-MESSAGE-abc" type="m-t-t"><detail><text>hi</text></detail></event>"""
        assertTrue(dedup.admit(xml), "first copy delivered")
        assertFalse(dedup.admit(xml), "identical copy within TTL dropped")
        assertFalse(dedup.admit(xml), "and again")
    }

    @Test
    fun `distinct events are all admitted`() {
        val dedup = CotDeliveryDedup(now = { clock })
        assertTrue(dedup.admit("""<event uid="A"/>"""))
        assertTrue(dedup.admit("""<event uid="B"/>"""))
    }

    @Test
    fun `same uid but changed content is not a duplicate`() {
        // PLIs reuse the device uid every position update — keying on exact XML lets updates through.
        val dedup = CotDeliveryDedup(now = { clock })
        assertTrue(dedup.admit("""<event uid="N" type="a-f-G-U-C"><point lat="1.0"/></event>"""))
        assertTrue(dedup.admit("""<event uid="N" type="a-f-G-U-C"><point lat="2.0"/></event>"""))
    }

    @Test
    fun `duplicate is re-admitted once the TTL window elapses`() {
        val dedup = CotDeliveryDedup(ttl = 2.minutes, now = { clock })
        val xml = """<event uid="X"/>"""
        assertTrue(dedup.admit(xml))
        clock += 60.seconds
        assertFalse(dedup.admit(xml), "still within the 2-minute window")
        clock += 90.seconds // 150s since first sighting > 120s TTL
        assertTrue(dedup.admit(xml), "window expired, delivered again")
    }

    @Test
    fun `cache is bounded by maxEntries and evicts oldest`() {
        val dedup = CotDeliveryDedup(ttl = 60.minutes, maxEntries = 2, now = { clock })
        assertTrue(dedup.admit("a"))
        assertTrue(dedup.admit("b"))
        assertTrue(dedup.admit("c")) // evicts oldest ("a")
        assertTrue(dedup.admit("a"), "oldest was evicted, so treated as new")
        assertFalse(dedup.admit("c"), "still cached")
    }
}
