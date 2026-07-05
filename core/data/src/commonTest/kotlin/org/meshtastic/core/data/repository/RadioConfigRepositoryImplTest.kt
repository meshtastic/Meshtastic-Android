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
package org.meshtastic.core.data.repository

import androidx.datastore.core.DataStore
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.meshtastic.core.datastore.ChannelSetDataSource
import org.meshtastic.core.datastore.LocalConfigDataSource
import org.meshtastic.core.datastore.ModuleConfigDataSource
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.FileInfo
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LocalModuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RadioConfigRepositoryImplTest {

    private val nodeDB = mock<NodeRepository>(MockMode.autofill)

    // The DataSources are final classes (can't be mocked directly) that just wrap a DataStore<T> --
    // mock the DataStore instead. None of these are touched by the addFileInfo/fileManifestFlow/
    // clearFileManifest behavior under test here.
    private val channelSetDataSource = ChannelSetDataSource(mock<DataStore<ChannelSet>>(MockMode.autofill))
    private val localConfigDataSource = LocalConfigDataSource(mock<DataStore<LocalConfig>>(MockMode.autofill))
    private val moduleConfigDataSource = ModuleConfigDataSource(mock<DataStore<LocalModuleConfig>>(MockMode.autofill))

    private fun createRepository() =
        RadioConfigRepositoryImpl(nodeDB, channelSetDataSource, localConfigDataSource, moduleConfigDataSource)

    // Regression guard for a real growth vector found via adversarial mesh-fuzz testing: a rogue/hostile
    // peer can emit FileInfo packets in a tight loop for the entire lifetime of a session -- nothing but
    // the *next* handshake's clearFileManifest() bounds this accumulator otherwise (addFileInfo used to be
    // an unconditional `_fileManifestFlow.value += info`). A real device's manifest is bounded by its own
    // flash storage, so this only bites under an adversarial/malformed stream, but a rogue node hitting it
    // for the life of a long-running session is a real unbounded-memory-growth DoS.
    @Test
    fun `addFileInfo caps the manifest instead of growing without bound`() = runTest {
        val repository = createRepository()
        // Mirrors the private MAX_FILE_MANIFEST_ENTRIES cap in RadioConfigRepositoryImpl -- kept as a
        // literal here rather than exposing the constant, since the cap value itself isn't the contract
        // under test (that it caps at all, deterministically, is).
        val cap = 4096

        repeat(cap + 500) { i -> repository.addFileInfo(FileInfo(file_name = "file$i.bin", size_bytes = i)) }

        val manifest = repository.fileManifestFlow.first()
        assertEquals(cap, manifest.size)
    }

    // Regression guard for the read-then-write race in addFileInfo (flagged in review): handleFileInfo
    // dispatches each packet on its own scope.handledLaunch, so addFileInfo runs concurrently. The old
    // `val c = flow.value; flow.value = c + info` could drop updates (two callers read the same list,
    // one append is lost) or bypass the cap. update{} makes it atomic. Firing many concurrent adds below
    // the cap must land every one -- no lost updates. (Trivially true when single-threaded; actually
    // exercises the race on multi-threaded targets like the JVM.)
    @Test
    fun `concurrent addFileInfo does not lose updates`() = runTest {
        val repository = createRepository()
        val n = 2000 // below the 4096 cap, so every add must be retained

        withContext(Dispatchers.Default) {
            coroutineScope {
                (0 until n)
                    .map { i -> async { repository.addFileInfo(FileInfo(file_name = "f$i.bin", size_bytes = i)) } }
                    .awaitAll()
            }
        }

        assertEquals(n, repository.fileManifestFlow.first().size)
    }

    @Test
    fun `clearFileManifest empties the manifest so a new handshake can accumulate again`() = runTest {
        val repository = createRepository()
        repository.addFileInfo(FileInfo(file_name = "a.bin", size_bytes = 1))
        repository.addFileInfo(FileInfo(file_name = "b.bin", size_bytes = 2))
        assertEquals(2, repository.fileManifestFlow.first().size)

        repository.clearFileManifest()
        assertEquals(0, repository.fileManifestFlow.first().size)

        repository.addFileInfo(FileInfo(file_name = "c.bin", size_bytes = 3))
        assertEquals(1, repository.fileManifestFlow.first().size)
    }
}
