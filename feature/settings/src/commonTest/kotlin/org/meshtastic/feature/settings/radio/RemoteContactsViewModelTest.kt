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
package org.meshtastic.feature.settings.radio

import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.meshtastic.core.domain.usecase.session.EnsureRemoteAdminSessionUseCase
import org.meshtastic.core.domain.usecase.session.EnsureSessionResult
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.util.getSharedContactUrl
import org.meshtastic.core.model.util.toSharedContact
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.FakeRadioController
import org.meshtastic.proto.SharedContact
import org.meshtastic.proto.User
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteContactsViewModelTest {

    private val nodeRepository = FakeNodeRepository()
    private val radioController = FakeRadioController()
    private val ensureSession: EnsureRemoteAdminSessionUseCase = mock()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        everySuspend { ensureSession(any()) } returns EnsureSessionResult.AlreadyActive
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RemoteContactsViewModel(REMOTE, nodeRepository, radioController, ensureSession)

    private fun node(num: Int, name: String, key: ByteString = KEY) = Node(
        num = num,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!" + num.toUInt().toString(16).padStart(8, '0')
                wb.long_name = name
                wb.public_key = key
            }
            .build(),
    )

    private fun remoteWrites() = radioController.adminRequests.filter { it.kind.contains("Remote") }

    @Test
    fun candidatesLeaveOutTheRemoteNodeAndNodesWithoutAPublicKey() = runTest {
        nodeRepository.setNodes(
            listOf(node(REMOTE, "Remote"), node(KEYED, "Keyed"), node(KEYLESS, "Keyless", ByteString.EMPTY)),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.candidates.collect {} }

        assertEquals(listOf(KEYED), vm.candidates.value.map { it.num })
    }

    @Test
    fun addContactSendsThatNodesContactToTheRemoteNode() = runTest {
        val target = node(KEYED, "Keyed")
        val vm = viewModel()

        vm.addContact(target)

        assertEquals(RemoteContactsEvent.Acknowledged, vm.events.first())
        val write = remoteWrites().single()
        assertEquals("addRemoteContact", write.kind)
        assertEquals(REMOTE, write.destNum)
        assertEquals(KEY, (write.payload as SharedContact).user?.public_key)
    }

    @Test
    fun noWriteIsSentWithoutAUsableAdminSession() = runTest {
        everySuspend { ensureSession(any()) } returns EnsureSessionResult.Timeout
        val vm = viewModel()

        vm.setFavorite(node(KEYED, "Keyed"), favorite = true)

        assertEquals(RemoteContactsEvent.NotAcknowledged, vm.events.first())
        assertTrue(remoteWrites().isEmpty())
    }

    @Test
    fun aWriteTheRemoteNodeDoesNotAcknowledgeIsReportedAsFailed() = runTest {
        radioController.remoteNodeDbAck = false
        val vm = viewModel()

        vm.setFavorite(node(KEYED, "Keyed"), favorite = false)

        assertEquals(RemoteContactsEvent.NotAcknowledged, vm.events.first())
        assertEquals("removeRemoteFavorite", remoteWrites().single().kind)
    }

    @Test
    fun contactUrlIsDecodedAndSentToTheRemoteNode() = runTest {
        val url = node(KEYED, "Keyed").toSharedContact().getSharedContactUrl().toString()
        val vm = viewModel()

        vm.addContactFromUrl(url)

        assertEquals(RemoteContactsEvent.Acknowledged, vm.events.first())
        assertEquals(KEYED, (remoteWrites().single().payload as SharedContact).node_num)
    }

    @Test
    fun badOrKeylessContactUrlsAreRejectedWithoutSending() = runTest {
        val keylessUrl = node(KEYLESS, "Keyless", ByteString.EMPTY).toSharedContact().getSharedContactUrl().toString()
        val vm = viewModel()

        vm.addContactFromUrl("https://example.com/not-a-contact")
        assertEquals(RemoteContactsEvent.InvalidUrl, vm.events.first())
        vm.addContactFromUrl(keylessUrl)
        assertEquals(RemoteContactsEvent.NoPublicKey, vm.events.first())

        assertTrue(remoteWrites().isEmpty())
    }

    private companion object {
        const val REMOTE = 0x42C91376
        const val KEYED = 0x5264FF52
        const val KEYLESS = 0x1234ABCD
        val KEY = ByteArray(32) { (it + 1).toByte() }.toByteString()
    }
}
