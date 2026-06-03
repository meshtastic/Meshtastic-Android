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

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Suppresses duplicate CoT events before they are injected into the local TAK client.
 *
 * The mesh can hand the bridge the same logical CoT more than once — a packet relayed over several LoRa paths, or a
 * sender that retransmits — which makes ATAK show duplicate chat / TAK-Talk messages. This is a small TTL cache keyed
 * by the exact, already-normalized CoT XML: an identical event seen within [ttl] is dropped, while genuine updates (a
 * PLI with a new position, a moved marker, …) differ in content and pass through untouched.
 *
 * Scope: this only dedupes what the *bridge* injects. It cannot see copies the TAK client also receives over another
 * transport (e.g. Wi-Fi network SA); for those the client dedupes by `uid`, which is why the SDK preserves the original
 * uid/timestamps across the mesh round-trip so the two copies collapse client-side.
 *
 * Not thread-safe by design: it is confined to the single mesh-packet collector coroutine (`meshPacketFlow.collect {
 * handleMeshPacket(it) }`), which processes packets sequentially.
 */
internal class CotDeliveryDedup(
    private val ttl: Duration = DEFAULT_TTL,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val now: () -> Instant = Clock.System::now,
) {
    // normalized XML -> last-seen time. Insertion-ordered: entries are re-inserted on each
    // sighting so the iteration order is oldest-first, which makes [evictExpired] cheap.
    private val seen = LinkedHashMap<String, Instant>()

    /**
     * @return true if [normalizedXml] should be delivered (it is new, or its previous copy has aged past [ttl]); false
     *   if it is a duplicate already delivered within [ttl] (drop it).
     */
    fun admit(normalizedXml: String): Boolean {
        val t = now()
        evictExpired(t)
        if (seen.containsKey(normalizedXml)) return false // survived eviction => still within ttl
        seen[normalizedXml] = t
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
        return true
    }

    private fun evictExpired(t: Instant) {
        val it = seen.entries.iterator()
        while (it.hasNext()) {
            // Oldest-first: stop at the first entry still within the window.
            if (t - it.next().value > ttl) it.remove() else break
        }
    }

    companion object {
        val DEFAULT_TTL: Duration = 2.minutes
        const val DEFAULT_MAX_ENTRIES: Int = 512
    }
}
