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

import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertFalse
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
    fun candidatesLeaveOutTheRemoteNodeAndNodesWithoutATrustedPublicKey() = runTest {
        nodeRepository.setNodes(
            listOf(
                node(REMOTE, "Remote"),
                node(KEYED, "Keyed"),
                node(KEYLESS, "Keyless", ByteString.EMPTY),
                node(DISPUTED, "Disputed", Node.ERROR_BYTE_STRING),
            ),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.candidates.collect {} }

        assertEquals(listOf(KEYED), vm.candidates.value.map { it.num })
    }

    @Test
    fun everyActionSendsItsOwnWriteToTheRemoteNode() = runTest {
        val vm = viewModel()
        val expected =
            mapOf(
                RemoteContactAction.FAVORITE to "setRemoteFavorite",
                RemoteContactAction.UNFAVORITE to "removeRemoteFavorite",
                RemoteContactAction.IGNORE to "setRemoteIgnored",
                RemoteContactAction.UNIGNORE to "removeRemoteIgnored",
                RemoteContactAction.REMOVE to "removeRemoteNode",
            )

        expected.keys.forEach { vm.perform(node(KEYED, "Keyed"), it) }

        assertEquals(expected.values.toList(), remoteWrites().map { it.kind })
        assertTrue(remoteWrites().all { it.destNum == REMOTE && it.payload == KEYED })
    }

    @Test
    fun aSecondWriteWhileOneIsInFlightIsDropped() = runTest {
        val session = CompletableDeferred<EnsureSessionResult>()
        everySuspend { ensureSession(any()) } calls { session.await() }
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.FAVORITE)
        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.REMOVE)
        vm.addContactFromUrl("https://example.com/not-a-contact")
        session.complete(EnsureSessionResult.AlreadyActive)

        assertEquals(listOf("setRemoteFavorite"), remoteWrites().map { it.kind })
        assertEquals(
            RemoteWriteResult(KEYED, RemoteContactAction.FAVORITE, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
    }

    @Test
    fun addContactSendsThatNodesContactToTheRemoteNodeAndReportsOnItsRow() = runTest {
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.ADD_CONTACT)

        assertEquals(
            RemoteWriteResult(KEYED, RemoteContactAction.ADD_CONTACT, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
        val write = remoteWrites().single()
        assertEquals("addRemoteContact", write.kind)
        assertEquals(REMOTE, write.destNum)
        assertEquals(KEY, (write.payload as SharedContact).user?.public_key)
    }

    @Test
    fun noWriteIsSentWithoutAUsableAdminSession() = runTest {
        everySuspend { ensureSession(any()) } returns EnsureSessionResult.Timeout
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.FAVORITE)

        assertEquals(RemoteWriteStatus.NOT_SENT, vm.lastResult.value?.status)
        assertTrue(remoteWrites().isEmpty())
    }

    @Test
    fun aWriteTheRemoteNodeDoesNotAcknowledgeIsReportedAsFailed() = runTest {
        radioController.remoteNodeDbAck = false
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.UNFAVORITE)

        assertEquals(RemoteWriteStatus.NOT_ACKNOWLEDGED, vm.lastResult.value?.status)
        assertEquals("removeRemoteFavorite", remoteWrites().single().kind)
    }

    @Test
    fun contactUrlIsSentToTheRemoteNodeAsAContactNeverABlock() = runTest {
        val blocking = node(KEYED, "Keyed").toSharedContact().newBuilder().also { wb -> wb.should_ignore = true }.build()
        val vm = viewModel()

        vm.addContactFromUrl(blocking.getSharedContactUrl().toString())

        assertEquals(
            RemoteWriteResult(null, RemoteContactAction.ADD_CONTACT, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
        val sent = remoteWrites().single().payload as SharedContact
        assertEquals(KEYED, sent.node_num)
        assertFalse(sent.should_ignore)
    }

    @Test
    fun badKeylessZeroKeyOrSelfContactUrlsAreRejectedWithoutSending() = runTest {
        fun urlFor(n: Node) = n.toSharedContact().getSharedContactUrl().toString()
        val vm = viewModel()

        vm.addContactFromUrl("https://example.com/not-a-contact")
        assertEquals(RemoteWriteStatus.INVALID_URL, vm.lastResult.value?.status)
        vm.addContactFromUrl(urlFor(node(KEYLESS, "Keyless", ByteString.EMPTY)))
        assertEquals(RemoteWriteStatus.NO_PUBLIC_KEY, vm.lastResult.value?.status)
        vm.addContactFromUrl(urlFor(node(DISPUTED, "Disputed", Node.ERROR_BYTE_STRING)))
        assertEquals(RemoteWriteStatus.NO_PUBLIC_KEY, vm.lastResult.value?.status)
        vm.addContactFromUrl(urlFor(node(REMOTE, "Remote")))
        assertEquals(RemoteWriteStatus.SELF_CONTACT, vm.lastResult.value?.status)

        assertTrue(remoteWrites().isEmpty())
    }

    private companion object {
        const val REMOTE = 0x42C91376
        const val KEYED = 0x5264FF52
        const val KEYLESS = 0x1234ABCD
        const val DISPUTED = 0x0BADC0DE
        val KEY = ByteArray(32) { (it + 1).toByte() }.toByteString()
    }
}
