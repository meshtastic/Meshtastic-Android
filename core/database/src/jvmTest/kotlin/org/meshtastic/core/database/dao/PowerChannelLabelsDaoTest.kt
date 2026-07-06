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
package org.meshtastic.core.database.dao

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.core.database.getInMemoryDatabaseBuilder
import org.meshtastic.proto.HardwareModel
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Covers the local-only power-channel labels: the direct setter round-trips through the [List]<[String]> column
 * converter, and a routine device-sync upsert (which never carries labels) must not clobber the user's saved labels.
 *
 * JVM-only: the preservation case re-`upsert`s an existing row, and that nested suspend `@Transaction` (upsert reading
 * back a `@Relation` row) throws a bare SQLException under Robolectric's SQLite; the logic itself is pure Kotlin.
 */
class PowerChannelLabelsDaoTest {

    private val database: MeshtasticDatabase = getInMemoryDatabaseBuilder().build()
    private val dao: NodeInfoDao = database.nodeInfoDao()

    @AfterTest fun tearDown() = database.close()

    private fun node(num: Int, labels: List<String> = emptyList()) = NodeEntity(
        num = num,
        user = User(id = "!$num", long_name = "Node $num", hw_model = HardwareModel.TBEAM),
        powerChannelLabels = labels,
    )

    @Test
    fun setterRoundTripsThroughConverter() = runTest {
        dao.upsert(node(7))
        dao.setPowerChannelLabels(7, listOf("Solar", "Battery"))

        assertEquals(listOf("Solar", "Battery"), dao.getNodeByNum(7)?.node?.powerChannelLabels)
    }

    @Test
    fun upsertWithoutLabelsPreservesExisting() = runTest {
        dao.upsert(node(8, labels = listOf("Solar", "Battery")))
        // A device-sync update carries no labels; re-upserting must keep the user's saved ones.
        dao.upsert(node(8))

        val result = dao.getNodeByNum(8)
        assertNotNull(result)
        assertEquals(listOf("Solar", "Battery"), result.node.powerChannelLabels)
    }

    @Test
    fun updateMergesFreshFromDbSoChannelsDontClobber() = runTest {
        dao.upsert(node(9))
        // Each call reads the current labels from the DB inside its transaction, so setting a second
        // channel keeps the first — the race the read-modify-write in the ViewModel used to allow.
        dao.updatePowerChannelLabel(9, channelIndex = 0, label = "Solar")
        dao.updatePowerChannelLabel(9, channelIndex = 1, label = "Battery")

        assertEquals(listOf("Solar", "Battery"), dao.getNodeByNum(9)?.node?.powerChannelLabels)
    }

    @Test
    fun updatePadsTrimsAndDropsTrailingBlanks() = runTest {
        dao.upsert(node(10))
        dao.updatePowerChannelLabel(10, channelIndex = 2, label = "  Load  ")
        assertEquals(listOf("", "", "Load"), dao.getNodeByNum(10)?.node?.powerChannelLabels)

        // Clearing the last label shrinks the stored list rather than leaving trailing blanks.
        dao.updatePowerChannelLabel(10, channelIndex = 2, label = "")
        assertEquals(emptyList(), dao.getNodeByNum(10)?.node?.powerChannelLabels)
    }
}
