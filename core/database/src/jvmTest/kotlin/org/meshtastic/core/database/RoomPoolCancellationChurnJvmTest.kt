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
package org.meshtastic.core.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.database.MeshtasticDatabase.Companion.configureCommon
import org.meshtastic.core.database.entity.MeshLog
import org.meshtastic.core.database.entity.NodeEntity
import org.meshtastic.proto.User
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

private const val NODES = 200
private const val LOGS = 2_000
private const val ROUNDS = 1_000
private const val COLLECTORS_PER_ROUND = 8
private const val MAX_COLLECTOR_LIFE_MS = 4L
private const val READS_AFTER = 20
private const val READ_TIMEOUT_MS = 5_000L
private const val LOG_LIMIT = 5_000
private const val LOG_NODE = 7
private const val LOG_PORT = 67

/**
 * The reader pool has stopped serving before under cancellation: every reader free, no permit left, each read waiting
 * for good. This cancels collectors mid-query on a file database built the way the app builds it, with a writer
 * running, and then requires the pool to still serve reads.
 */
class RoomPoolCancellationChurnJvmTest {

    private fun node(num: Int, heard: Int) =
        NodeEntity(num = num, user = User.Builder().also { it.id = "!%08x".format(num) }.build(), lastHeard = heard)

    private fun log(id: String, time: Long) = MeshLog(
        uuid = id,
        message_type = "Packet",
        received_date = time,
        raw_message = "x".repeat(200),
        fromNum = LOG_NODE,
        portNum = LOG_PORT,
    )

    @Test
    fun `the pool still serves reads after collectors are cancelled mid-query`() = runBlocking(Dispatchers.Default) {
        val file = Files.createTempDirectory("pool-churn").resolve("churn.db").toFile()
        val database =
            Room.databaseBuilder<MeshtasticDatabase>(
                name = file.absolutePath,
                factory = { MeshtasticDatabaseConstructor.initialize() },
            )
                .configureCommon()
                .setDriver(BusyTimeoutSQLiteDriver(BundledSQLiteDriver()))
                .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writes = AtomicInteger()
        try {
            database.nodeInfoDao().putAll((1..NODES).map { node(it, it) })
            (0 until LOGS)
                .map { log("log-$it", it.toLong()) }
                .chunked(500)
                .forEach {
                    database.meshLogDao().insertIgnore(it)
                }
            val writer = scope.launch {
                var i = 0
                while (isActive) {
                    database.meshLogDao().insert(log("churn-$i", LOGS + i.toLong()))
                    database.nodeInfoDao().upsert(node(1 + i % NODES, LOGS + i))
                    writes.incrementAndGet()
                    i++
                }
            }
            val random = Random(seed = 42)
            repeat(ROUNDS) {
                val collectors =
                    List(COLLECTORS_PER_ROUND) { n ->
                        scope.launch {
                            if (n % 2 == 0) {
                                database.nodeInfoDao().nodeDBbyNum().collect {}
                            } else {
                                database.meshLogDao().getLogsFrom(LOG_NODE, LOG_PORT, LOG_LIMIT).collect {}
                            }
                        }
                    }
                delay(random.nextLong(0, MAX_COLLECTOR_LIFE_MS))
                collectors.forEach { it.cancel() }
            }
            writer.cancelAndJoin()
            scope.coroutineContext[Job]!!.cancelAndJoin()

            repeat(READS_AFTER) {
                withTimeout(READ_TIMEOUT_MS) {
                    database.nodeInfoDao().nodeDBbyNum().first()
                    database.meshLogDao().getLogsFrom(LOG_NODE, LOG_PORT, LOG_LIMIT).first()
                }
            }
            assertTrue(writes.get() > 0, "the writer made no progress during the churn")
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            database.close()
        }
    }
}
