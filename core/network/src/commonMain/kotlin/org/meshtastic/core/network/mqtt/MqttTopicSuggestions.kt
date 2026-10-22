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
package org.meshtastic.core.network.mqtt

import org.meshtastic.core.network.mqtt.MqttTopicDiscovery.Activity
import org.meshtastic.core.repository.GeocodedPlace

data class TopicSuggestion(val kind: Kind, val topic: String, val placeName: String) {
    enum class Kind {
        REGION,
        COUNTRY,
        SUBDIVISION,
        CITY,
        COMMUNITY,
    }
}

data class RankedRoot(val root: String, val activity: Activity, val tier: Int, val distanceMeters: Double?)

/** The topics worth offering for a place, and every other heard root ordered by how near it is likely to be. */
data class TopicPlan(
    val suggestions: List<TopicSuggestion>,
    /** Roots of the subdivisions bordering the user's, which rank with the community roots. */
    val neighborRoots: List<String>,
) {
    fun ranked(activity: Map<String, Activity>, from: LatLon?): List<RankedRoot> {
        val shown = suggestions.mapNotNull { activityFor(it.topic, activity)?.first }.toSet()
        val ladder = suggestions.filter { it.kind != TopicSuggestion.Kind.COMMUNITY }
        // The finest rungs above the region; a country rung counts only when nothing finer is known.
        val ownArea =
            ladder
                .filter { it.kind == TopicSuggestion.Kind.SUBDIVISION || it.kind == TopicSuggestion.Kind.CITY }
                .ifEmpty { ladder.filter { it.kind == TopicSuggestion.Kind.COUNTRY } }
                .map { it.topic }
        val nearby = suggestions.filter { it.kind == TopicSuggestion.Kind.COMMUNITY }.map { it.topic } + neighborRoots
        return activity
            .filter { (root, _) -> root !in shown && MqttRootTopic.problem(root) == null }
            .map { (root, entry) ->
                val depth = ladder.count { MqttRootTopic.hasPrefix(root, it.topic) }
                val tier =
                    when {
                        ownArea.any { MqttRootTopic.hasPrefix(root, it) } -> TIER_OWN_AREA
                        nearby.any { MqttRootTopic.hasPrefix(root, it) } -> TIER_NEARBY
                        else -> TIER_ELSEWHERE
                    }
                Ranked(RankedRoot(root, entry, tier, entry.nearestDistanceMeters(from)), depth)
            }
            .sortedWith(rankOrder)
            .map { it.root }
    }

    private class Ranked(val root: RankedRoot, val depth: Int)

    companion object {
        const val TIER_OWN_AREA = 0
        const val TIER_NEARBY = 1
        const val TIER_ELSEWHERE = 2

        private val rankOrder =
            compareBy<Ranked> { it.root.tier }
                .thenByDescending { it.depth }
                .thenBy(nullsLast()) { it.root.distanceMeters }
                .thenByDescending { it.root.activity.nodeCount }
                .thenByDescending { it.root.activity.messageCount }
                .thenBy { it.root.root.lowercase() }

        /**
         * Region, then the country when the sample shows the broker uses it, then subdivision and city, then the
         * community roots that serve the place. Each ladder rung is trimmed from the end of its last segment to fit the
         * byte cap; a rung that empties is dropped with everything below it. Rebuilt as [activity] grows.
         */
        fun build(
            regionName: String,
            place: GeocodedPlace?,
            at: LatLon?,
            directory: MqttRootDirectory,
            activity: Map<String, Activity> = emptyMap(),
        ): TopicPlan {
            val regionTopic = "msh/$regionName"
            val suggestions = mutableListOf(TopicSuggestion(TopicSuggestion.Kind.REGION, regionTopic, regionName))
            val countryCode = place?.countryCode?.uppercase()?.takeIf { it.length == 2 }
            val country = countryLevel(regionTopic, regionName, countryCode, activity)
            val base = country?.topic ?: regionTopic
            if (country != null && !country.flooded) {
                suggestions += TopicSuggestion(TopicSuggestion.Kind.COUNTRY, country.topic, country.code)
            }
            val subdivisionName = place?.subdivision
            val subdivisionSegment = subdivisionName?.let {
                segment(directory.subdivisionSegment(countryCode, it), lowercased = false)
            }
            val subdivisionTopic = subdivisionSegment?.let { fitted(base, it) }
            if (subdivisionName != null && subdivisionTopic != null) {
                suggestions += TopicSuggestion(TopicSuggestion.Kind.SUBDIVISION, subdivisionTopic, subdivisionName)
                val locality = place.locality
                val cityTopic = locality?.let { segment(it, lowercased = true) }?.let { fitted(subdivisionTopic, it) }
                if (locality != null && cityTopic != null) {
                    suggestions += TopicSuggestion(TopicSuggestion.Kind.CITY, cityTopic, locality)
                }
            }
            val isoCode =
                if (countryCode != null && subdivisionSegment != null) "$countryCode-$subdivisionSegment" else null
            directory.entriesServing(isoCode, at).forEach { entry ->
                if (suggestions.none { MqttRootTopic.rootsMatch(it.topic, entry.root) }) {
                    suggestions += TopicSuggestion(TopicSuggestion.Kind.COMMUNITY, entry.root, entry.name)
                }
            }
            val neighborRoots =
                directory.neighbors[isoCode].orEmpty().sorted().map { "$base/${it.substringAfter('-')}" }
            return TopicPlan(suggestions, neighborRoots)
        }

        private class CountryLevel(val code: String, val topic: String, val flooded: Boolean)

        /**
         * The country level, decided from the broker being sampled rather than a list of regions: present when some
         * heard root under the region has the country code as its next segment. A country code equal to the region name
         * ("msh/US/US") is a republisher's doubled root, not a country level. A country root as busy as the region's
         * own stream is no better to downlink, so it still prefixes the subdivision but is not offered.
         */
        private fun countryLevel(
            regionTopic: String,
            regionName: String,
            countryCode: String?,
            activity: Map<String, Activity>,
        ): CountryLevel? {
            val code = countryCode?.takeUnless { it.equals(regionName, ignoreCase = true) }
            val topic = code?.let { "$regionTopic/$it" }
            val inUse = topic != null && activity.keys.any { MqttRootTopic.hasPrefix(it, topic) }
            return if (code == null || topic == null || !inUse) {
                null
            } else {
                val packets = activityFor(topic, activity)?.second?.packets ?: 0
                CountryLevel(code, topic, flooded = packets >= MqttTopicSampler.REGION_PACKET_CAP)
            }
        }

        /**
         * The activity recorded for a topic, with the spelling the broker used: the topic's own spelling when the
         * broker carries it, otherwise the lowest case-insensitive match, so a row does not flip between variants from
         * one snapshot to the next.
         */
        fun activityFor(topic: String, activity: Map<String, Activity>): Pair<String, Activity>? {
            val key =
                if (topic in activity) {
                    topic
                } else {
                    activity.keys.filter { MqttRootTopic.rootsMatch(it, topic) }.minOrNull()
                }
            return key?.let { it to activity.getValue(it) }
        }

        /**
         * Diacritics stripped, Latin letters folded to ASCII, then only `[A-Za-z0-9_-]` kept, lowercased when asked.
         * Subdivisions keep their case ("WA"); cities follow the community's lowercase convention. Null when nothing is
         * left: a name in a non-Latin script gets no rung rather than a romanization the local mesh would not use.
         */
        fun segment(place: String, lowercased: Boolean): String? {
            val kept =
                foldToAscii(place).filter {
                    it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-'
                }
            if (kept.isEmpty()) return null
            return if (lowercased) kept.lowercase() else kept
        }

        private fun fitted(base: String, lastSegment: String): String? {
            var segment = lastSegment
            while (segment.isNotEmpty() && "$base/$segment".encodeToByteArray().size > MqttRootTopic.BYTE_CAP) {
                segment = segment.dropLast(1)
            }
            return if (segment.isEmpty()) null else "$base/$segment"
        }
    }
}
