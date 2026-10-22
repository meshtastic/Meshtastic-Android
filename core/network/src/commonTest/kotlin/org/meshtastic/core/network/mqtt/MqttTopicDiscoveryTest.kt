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

import okio.ByteString.Companion.toByteString
import org.meshtastic.core.network.mqtt.MqttRootTopic.Problem
import org.meshtastic.core.network.mqtt.MqttTopicDiscovery.Kind
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.CITY
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.COMMUNITY
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.COUNTRY
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.REGION
import org.meshtastic.core.network.mqtt.TopicSuggestion.Kind.SUBDIVISION
import org.meshtastic.core.repository.GeocodedPlace
import org.meshtastic.proto.Data
import org.meshtastic.proto.MapReport
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ServiceEnvelope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MqttTopicDiscoveryTest {
    @Test
    fun rootRulesReportTheFirstProblem() {
        assertEquals(Problem.EMPTY, MqttRootTopic.problem(""))
        assertEquals(Problem.WHITESPACE, MqttRootTopic.problem("msh/US WA"))
        assertEquals(Problem.NON_ASCII, MqttRootTopic.problem("msh/NO/Østlandet"))
        assertEquals(Problem.WILDCARD, MqttRootTopic.problem("msh/US/+"))
        assertEquals(Problem.LEADING_SLASH, MqttRootTopic.problem("/msh/US"))
        assertEquals(Problem.TRAILING_SLASH, MqttRootTopic.problem("msh/US/"))
        assertEquals(Problem.EMPTY_SEGMENT, MqttRootTopic.problem("msh//US"))
        assertEquals(Problem.ENDS_WITH_VERSION, MqttRootTopic.problem("msh/US/CA/US/2"))
        assertEquals(Problem.TOO_LONG, MqttRootTopic.problem("msh/US/WA/abcdefghijklmnopqrstuv"))
        assertNull(MqttRootTopic.problem("msh/US/WA/seattle"))
    }

    @Test
    fun sanitizedCollapsesSlashesAndDropsOneTrailingSlash() {
        assertEquals("msh/US/WA", MqttRootTopic.sanitized("  msh//US///WA/ "))
    }

    @Test
    fun prefixIsSegmentWiseAndIgnoresCase() {
        assertTrue(MqttRootTopic.hasPrefix("msh/US/WA/seattle", "msh/us/wa"))
        assertTrue(!MqttRootTopic.hasPrefix("msh/US/WAX", "msh/US/WA"))
        assertTrue(!MqttRootTopic.hasPrefix("msh/US", "msh/US/WA"))
    }

    @Test
    fun observationParsesPacketAndMapTopics() {
        assertEquals(
            MqttTopicDiscovery.Observation("msh/US/WA", Kind.Packet("LongFast", "!0a1b2c3d")),
            MqttTopicDiscovery.observation("msh/US/WA/2/e/LongFast/!0a1b2c3d"),
        )
        assertEquals(
            MqttTopicDiscovery.Observation("msh/US", Kind.MapReport),
            MqttTopicDiscovery.observation("msh/US/2/map/"),
        )
    }

    @Test
    fun observationRejectsOtherTopicShapes() {
        assertNull(MqttTopicDiscovery.observation("msh/US/2/e/LongFast/!0A1B2C3D"))
        assertNull(MqttTopicDiscovery.observation("msh/US/2/json/LongFast/!0a1b2c3d"))
        assertNull(MqttTopicDiscovery.observation("msh/US/1/c/LongFast/!0a1b2c3d"))
        assertNull(MqttTopicDiscovery.observation("foo/US/2/map/"))
    }

    @Test
    fun observationCreditsAStraySpaceToTheSaveableSpelling() {
        assertEquals("msh/US/DK", MqttTopicDiscovery.observation("msh/US/DK /2/map/")?.root)
    }

    @Test
    fun tallyCountsGatewaysSendersAndMapReportPositions() {
        val tally = MqttTopicDiscovery.Tally()
        tally.record("msh/US/WA/2/e/LongFast/!00000001", envelope(from = 2).encode())
        tally.record("msh/US/WA/2/map/", mapEnvelope(gateway = "!00000003", lat = 476_062_000, lon = -1_223_321_000))
        val activity = tally.snapshot().getValue("msh/US/WA")
        assertEquals(setOf("!00000001", "!00000002", "!00000003"), activity.nodes)
        assertEquals(1, activity.packets)
        assertEquals(1, activity.mapReports)
        val position = activity.positions.getValue("!00000003")
        assertEquals(47.6062, position.latitude, absoluteTolerance = 1e-9)
        assertEquals(-122.3321, position.longitude, absoluteTolerance = 1e-9)
        assertEquals(setOf("LongFast"), activity.channels)
    }

    @Test
    fun tallyDropsNewRootsPastTheCap() {
        val tally = MqttTopicDiscovery.Tally()
        repeat(MqttTopicDiscovery.Tally.MAX_ROOTS + 3) { tally.record("msh/US/r$it/2/map/", ByteArray(0)) }
        assertEquals(MqttTopicDiscovery.Tally.MAX_ROOTS, tally.snapshot().size)
        assertEquals(3, tally.droppedRoots)
    }

    @Test
    fun segmentFoldsLatinAndDropsOtherScripts() {
        assertEquals("stjohns", TopicPlan.segment("St. John's", lowercased = true))
        assertEquals("coeurdalene", TopicPlan.segment("Coeur d'Alene", lowercased = true))
        assertEquals("munchen", TopicPlan.segment("München", lowercased = true))
        assertEquals("Ostlandet", TopicPlan.segment("Østlandet", lowercased = false))
        assertEquals("strasse", TopicPlan.segment("Straße", lowercased = true))
        assertNull(TopicPlan.segment("東京都", lowercased = true))
    }

    @Test
    fun usLadderMapsTheStateNameToItsCode() {
        val plan = plan("US", GeocodedPlace("US", "Washington", "Seattle"))
        assertEquals(
            listOf(REGION to "msh/US", SUBDIVISION to "msh/US/WA", CITY to "msh/US/WA/seattle"),
            plan.suggestions.map { it.kind to it.topic },
        )
    }

    @Test
    fun countryLevelIsOfferedWhenTheBrokerUsesIt() {
        val heard = mapOf("msh/EU_868/DE/S-H" to activity(nodeCount = 3))
        val plan = plan("EU_868", GeocodedPlace("DE", "Bayern", "München"), heard)
        assertEquals(
            listOf(
                REGION to "msh/EU_868",
                COUNTRY to "msh/EU_868/DE",
                SUBDIVISION to "msh/EU_868/DE/Bayern",
                CITY to "msh/EU_868/DE/Bayern/munchen",
            ),
            plan.suggestions.map { it.kind to it.topic },
        )
    }

    @Test
    fun noCountryLevelUntilTheBrokerShowsOne() {
        val plan = plan("EU_868", GeocodedPlace("DE", "Bayern", "München"))
        assertEquals(
            listOf(REGION to "msh/EU_868", SUBDIVISION to "msh/EU_868/Bayern", CITY to "msh/EU_868/Bayern/munchen"),
            plan.suggestions.map { it.kind to it.topic },
        )
    }

    @Test
    fun aFloodedCountryRootPrefixesTheSubdivisionButIsNotOffered() {
        val tally = MqttTopicDiscovery.Tally()
        repeat(MqttTopicSampler.REGION_PACKET_CAP) {
            tally.record("msh/EU_868/DE/2/e/LongFast/!0000000a", ByteArray(0))
        }
        val plan = plan("EU_868", GeocodedPlace("DE", "Bayern", "München"), tally.snapshot())
        assertEquals(
            listOf(
                REGION to "msh/EU_868",
                SUBDIVISION to "msh/EU_868/DE/Bayern",
                CITY to "msh/EU_868/DE/Bayern/munchen",
            ),
            plan.suggestions.map { it.kind to it.topic },
        )
    }

    @Test
    fun aRootRepeatingTheRegionIsNotACountryLevel() {
        val heard = mapOf("msh/US/US" to activity(nodeCount = 60), "msh/US/US/NY/WNY" to activity(nodeCount = 24))
        val plan = plan("US", GeocodedPlace("US", "Washington", "Seattle"), heard)
        assertEquals(listOf("msh/US", "msh/US/WA", "msh/US/WA/seattle"), plan.suggestions.map { it.topic })
    }

    @Test
    fun cityIsTrimmedToTheByteCapAndDroppedWhenNothingFits() {
        val plan = plan("US", GeocodedPlace("US", "Washington", "Abcdefghijklmnopqrstuvwxyz"))
        val city = plan.suggestions.single { it.kind == CITY }.topic
        assertEquals(MqttRootTopic.BYTE_CAP, city.encodeToByteArray().size)
        assertTrue(city.startsWith("msh/US/WA/abcdef"))
    }

    @Test
    fun noPlaceLeavesTheRegionAlone() {
        assertEquals(listOf("msh/US"), plan("US", null).suggestions.map { it.topic })
    }

    @Test
    fun communityEntryIsSuggestedBySubdivisionOrRadius() {
        val directory =
            US_DIRECTORY.copy(
                entries =
                listOf(
                    MqttRootEntry("msh/US/DMV", "DMV", subdivisions = setOf("US-DC", "US-MD", "US-VA")),
                    MqttRootEntry("msh/US/MeshSTL", "St. Louis", center = LatLon(38.627, -90.199), radiusKm = 80.0),
                ),
            )
        val arlington = TopicPlan.build("US", GeocodedPlace("US", "Virginia", "Arlington"), null, directory)
        assertTrue(arlington.suggestions.any { it.kind == COMMUNITY && it.topic == "msh/US/DMV" })
        val bellevilleIl =
            TopicPlan.build("US", GeocodedPlace("US", "Illinois", "Belleville"), LatLon(38.52, -89.98), directory)
        assertTrue(bellevilleIl.suggestions.any { it.kind == COMMUNITY && it.topic == "msh/US/MeshSTL" })
    }

    @Test
    fun rankingPutsOwnAreaThenNeighborsThenTheRest() {
        val plan = plan("US", GeocodedPlace("US", "Texas", "Texarkana"))
        val activity =
            mapOf(
                "msh/US/CA/socalmesh" to activity(nodeCount = 50),
                "msh/US/AR" to activity(nodeCount = 2),
                "msh/US/TX/dfw" to activity(nodeCount = 1),
                "msh/US/TX" to activity(nodeCount = 9),
            )
        val ranked = plan.ranked(activity, from = null)
        assertEquals(listOf("msh/US/TX/dfw", "msh/US/AR", "msh/US/CA/socalmesh"), ranked.map { it.root })
        assertEquals(
            listOf(TopicPlan.TIER_OWN_AREA, TopicPlan.TIER_NEARBY, TopicPlan.TIER_ELSEWHERE),
            ranked.map { it.tier },
        )
    }

    @Test
    fun anotherSubdivisionUnderTheCountryIsNotOwnArea() {
        val heard = mapOf("msh/EU_868/DE/S-H" to activity(nodeCount = 3))
        val plan = plan("EU_868", GeocodedPlace("DE", "Bayern", "München"), heard)
        val ranked =
            plan.ranked(
                mapOf(
                    "msh/EU_868/DE/S-H" to activity(nodeCount = 3),
                    "msh/EU_868/DE/Bayern/augsburg" to activity(nodeCount = 1),
                ),
                from = null,
            )
        assertEquals(listOf("msh/EU_868/DE/Bayern/augsburg", "msh/EU_868/DE/S-H"), ranked.map { it.root })
        assertEquals(listOf(TopicPlan.TIER_OWN_AREA, TopicPlan.TIER_ELSEWHERE), ranked.map { it.tier })
    }

    @Test
    fun activityForPrefersTheExactSpellingThenTheLowestVariant() {
        val activity =
            mapOf("msh/US/KS/topeka" to activity(nodeCount = 1), "msh/US/KS/Topeka" to activity(nodeCount = 2))
        assertEquals("msh/US/KS/topeka", TopicPlan.activityFor("msh/US/KS/topeka", activity)?.first)
        assertEquals("msh/US/KS/Topeka", TopicPlan.activityFor("msh/US/ks/TOPEKA", activity)?.first)
    }

    private fun plan(
        region: String,
        place: GeocodedPlace?,
        heard: Map<String, MqttTopicDiscovery.Activity> = emptyMap(),
    ) = TopicPlan.build(region, place, null, US_DIRECTORY, heard)

    private fun activity(nodeCount: Int): MqttTopicDiscovery.Activity = MqttTopicDiscovery.Activity().apply {
        repeat(nodeCount) { nodes += "!${it.toString(radix = 16).padStart(length = 8, padChar = '0')}" }
    }

    private fun envelope(from: Int): ServiceEnvelope = ServiceEnvelope.Builder()
        .also { wb ->
            wb.packet = MeshPacket.Builder().also { pb -> pb.from = from }.build()
            wb.gateway_id = "!00000001"
        }
        .build()

    private fun mapEnvelope(gateway: String, lat: Int, lon: Int): ByteArray {
        val report =
            MapReport.Builder()
                .also { wb ->
                    wb.latitude_i = lat
                    wb.longitude_i = lon
                    wb.num_online_local_nodes = 4
                }
                .build()
        val data =
            Data.Builder()
                .also { wb ->
                    wb.portnum = PortNum.MAP_REPORT_APP
                    wb.payload = report.encode().toByteString()
                }
                .build()
        val packet =
            MeshPacket.Builder()
                .also { wb ->
                    wb.from = 3
                    wb.decoded = data
                }
                .build()
        return ServiceEnvelope.Builder()
            .also { wb ->
                wb.packet = packet
                wb.gateway_id = gateway
            }
            .build()
            .encode()
    }

    private companion object {
        /** The slice of the published directory these plans touch. */
        val US_DIRECTORY =
            MqttRootDirectory(
                entries = emptyList(),
                neighbors =
                mapOf(
                    "US-TX" to setOf("US-AR", "US-LA", "US-NM", "US-OK"),
                    "US-AR" to setOf("US-TX"),
                    "US-LA" to setOf("US-TX"),
                    "US-NM" to setOf("US-TX"),
                    "US-OK" to setOf("US-TX"),
                ),
                subdivisionSegments =
                mapOf("US" to mapOf("illinois" to "IL", "texas" to "TX", "virginia" to "VA", "washington" to "WA")),
            )
    }
}
