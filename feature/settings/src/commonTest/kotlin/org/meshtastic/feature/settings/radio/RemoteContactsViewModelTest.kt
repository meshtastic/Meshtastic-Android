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
import kotlinx.coroutines.CancellationException
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
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.proto.Config
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

    private fun node(
        num: Int,
        name: String,
        key: ByteString = KEY,
        role: Config.DeviceConfig.Role = Config.DeviceConfig.Role.CLIENT,
    ) = Node(
        num = num,
        user =
        User.Builder()
            .also { wb ->
                wb.id = "!" + num.toUInt().toString(16).padStart(8, '0')
                wb.long_name = name
                wb.public_key = key
                wb.role = role
            }
            .build(),
    )

    private fun remoteIs(role: Config.DeviceConfig.Role) {
        nodeRepository.setNodes(listOf(node(REMOTE, "Remote", role = role), node(KEYED, "Keyed")))
    }

    private fun remoteWrites() = radioController.adminRequests.filter { it.kind.contains("Remote") }

    @Test
    fun candidatesLeaveOutOnlyTheRemoteNode() = runTest {
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

        assertEquals(setOf(KEYED, KEYLESS, DISPUTED), vm.candidates.value.map { it.num }.toSet())
    }

    @Test
    fun candidatesPutOurOwnNodeFirstThenFavoritesThenTheMostRecentlyHeard() = runTest {
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = OWN))
        nodeRepository.setNodes(
            listOf(
                node(REMOTE, "Remote").copy(lastHeard = 999),
                node(KEYLESS, "Stale").copy(lastHeard = 100),
                node(DISPUTED, "Recent").copy(lastHeard = 500),
                node(KEYED, "Favorite").copy(isFavorite = true, lastHeard = 50),
                node(OWN, "Own").copy(lastHeard = 10),
            ),
        )
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.candidates.collect {} }

        assertEquals(listOf(OWN, KEYED, DISPUTED, KEYLESS), vm.candidates.value.map { it.num })

        vm.setQuery("recent")

        assertEquals(listOf(DISPUTED), vm.candidates.value.map { it.num })
    }

    // remove_favorite_node, set_ignored_node and remove_by_nodenum carry a node number only (admin.proto), so a node
    // the radio holds no key for can still be unfavorited, ignored and removed on the remote node.
    @Test
    fun aNodeWithoutATrustedKeyCanStillBeUnfavoritedIgnoredAndRemoved() = runTest {
        val vm = viewModel()
        val keyless = node(KEYLESS, "Keyless", ByteString.EMPTY)
        val disputed = node(KEYED, "Disputed").copy(keyMatch = false)

        vm.perform(keyless, RemoteContactAction.UNFAVORITE)
        vm.perform(keyless, RemoteContactAction.IGNORE)
        vm.perform(disputed, RemoteContactAction.REMOVE)

        assertEquals(
            listOf("removeRemoteFavorite", "setRemoteIgnored", "removeRemoteNode"),
            remoteWrites().map { it.kind },
        )
        assertEquals(listOf<Any?>(KEYLESS, KEYLESS, KEYED), remoteWrites().map { it.payload })
    }

    @Test
    fun sendingANodeWithoutATrustedKeyAsAContactSendsNothingAndSaysWhy() = runTest {
        val vm = viewModel()
        val untrusted =
            listOf(
                node(KEYLESS, "Keyless", ByteString.EMPTY),
                node(DISPUTED, "Disputed", Node.ERROR_BYTE_STRING),
                node(KEYED, "Mismatched").copy(keyMatch = false),
            )

        untrusted.forEach { candidate ->
            vm.perform(candidate, RemoteContactAction.SEND_CONTACT)
            assertEquals(
                RemoteWriteResult(candidate.num, RemoteContactAction.SEND_CONTACT, RemoteWriteStatus.NO_PUBLIC_KEY),
                vm.lastResult.value,
            )
        }

        assertTrue(remoteWrites().isEmpty())
    }

    @Test
    fun everyActionSendsItsOwnWriteToTheRemoteNode() = runTest {
        val vm = viewModel()
        val expected =
            mapOf(
                RemoteContactAction.SEND_CONTACT to "addRemoteContact",
                RemoteContactAction.UNFAVORITE to "removeRemoteFavorite",
                RemoteContactAction.IGNORE to "setRemoteIgnored",
                RemoteContactAction.UNIGNORE to "removeRemoteIgnored",
                RemoteContactAction.REMOVE to "removeRemoteNode",
            )

        assertEquals(RemoteContactAction.entries.toSet(), expected.keys)
        expected.keys.forEach { vm.perform(node(KEYED, "Keyed"), it) }

        assertEquals(expected.values.toList(), remoteWrites().map { it.kind })
        assertTrue(remoteWrites().all { it.destNum == REMOTE })
        assertEquals(KEYED, (remoteWrites().first().payload as SharedContact).node_num)
        assertTrue(remoteWrites().drop(1).all { it.payload == KEYED })
    }

    // set_favorite_node is acknowledged but changes nothing for a node the target does not hold (AdminModule.cpp), so
    // it may only ever follow an acknowledged add_contact, and only on a CLIENT_BASE, where add_contact skips it.
    @Test
    fun onlySendingAContactToAClientBaseEverSendsAFavorite() = runTest {
        fun favoritesSentBy(action: RemoteContactAction, remoteRole: Config.DeviceConfig.Role): Int {
            remoteIs(remoteRole)
            radioController.adminRequests.clear()
            viewModel().perform(node(KEYED, "Keyed"), action)
            return remoteWrites().count { it.kind == "setRemoteFavorite" }
        }

        val sent =
            RemoteContactAction.entries.associateWith { action ->
                favoritesSentBy(action, Config.DeviceConfig.Role.CLIENT) to
                    favoritesSentBy(action, Config.DeviceConfig.Role.CLIENT_BASE)
            }

        val none = RemoteContactAction.entries.associateWith { 0 to 0 }
        assertEquals(none + (RemoteContactAction.SEND_CONTACT to (0 to 1)), sent)
    }

    @Test
    fun aSecondWriteWhileOneIsInFlightIsDropped() = runTest {
        val session = CompletableDeferred<EnsureSessionResult>()
        everySuspend { ensureSession(any()) } calls { session.await() }
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.UNFAVORITE)
        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.REMOVE)
        vm.addContactFromUrl("https://example.com/not-a-contact")
        session.complete(EnsureSessionResult.AlreadyActive)

        assertEquals(listOf("removeRemoteFavorite"), remoteWrites().map { it.kind })
        assertEquals(
            RemoteWriteResult(KEYED, RemoteContactAction.UNFAVORITE, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
    }

    @Test
    fun sendContactSendsThatNodesContactToTheRemoteNodeAndReportsOnItsRow() = runTest {
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        assertEquals(
            RemoteWriteResult(KEYED, RemoteContactAction.SEND_CONTACT, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
        val write = remoteWrites().single()
        assertEquals("addRemoteContact", write.kind)
        assertEquals(REMOTE, write.destNum)
        assertEquals(KEY, (write.payload as SharedContact).user?.public_key)
    }

    // Firmware's addFromContact leaves is_favorite alone on CLIENT_BASE (NodeDB.cpp), and set_favorite_node is a no-op
    // for a node the target does not hold (AdminModule.cpp), so the favorite must follow the contact's ACK.
    @Test
    fun sendContactOnAClientBaseFavoritesOnlyAfterTheContactIsAcknowledged() = runTest {
        remoteIs(Config.DeviceConfig.Role.CLIENT_BASE)
        val contactAck = CompletableDeferred<Boolean>()
        radioController.remoteNodeDbAckFor = { kind -> if (kind == "addRemoteContact") contactAck.await() else true }
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        assertEquals(listOf("addRemoteContact"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.SENDING, vm.lastResult.value?.status)

        contactAck.complete(true)

        assertEquals(listOf("addRemoteContact", "setRemoteFavorite"), remoteWrites().map { it.kind })
        val favorite = remoteWrites().last()
        assertEquals(REMOTE, favorite.destNum)
        assertEquals(KEYED, favorite.payload)
        assertEquals(
            RemoteWriteResult(KEYED, RemoteContactAction.SEND_CONTACT, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
    }

    @Test
    fun aClientBaseContactThatIsNotAcknowledgedIsNotFavorited() = runTest {
        remoteIs(Config.DeviceConfig.Role.CLIENT_BASE)
        radioController.remoteNodeDbAck = false
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        assertEquals(listOf("addRemoteContact"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.NOT_ACKNOWLEDGED, vm.lastResult.value?.status)
    }

    @Test
    fun aClientBaseFavoriteThatIsNotAcknowledgedIsReportedAsFailed() = runTest {
        remoteIs(Config.DeviceConfig.Role.CLIENT_BASE)
        radioController.remoteNodeDbAckFor = { kind -> kind == "addRemoteContact" }
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        assertEquals(listOf("addRemoteContact", "setRemoteFavorite"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.NOT_ACKNOWLEDGED, vm.lastResult.value?.status)
    }

    // Every other role favorites inside addFromContact, so a second packet would only spend airtime.
    @Test
    fun sendContactOnANodeThatIsNotAClientBaseSendsOnlyTheContact() = runTest {
        remoteIs(Config.DeviceConfig.Role.ROUTER)
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        assertEquals(listOf("addRemoteContact"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.ACKNOWLEDGED, vm.lastResult.value?.status)
    }

    // design#149 point 2: our own radio's key is one we hold, so it goes out verified. Anyone else's is relayed as is.
    @Test
    fun onlyOurOwnNodeIsSentAsManuallyVerified() = runTest {
        nodeRepository.setMyNodeInfo(TestDataFactory.createMyNodeInfo(myNodeNum = OWN))
        val vm = viewModel()

        vm.perform(node(OWN, "Own"), RemoteContactAction.SEND_CONTACT)
        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

        val sent = remoteWrites().map { it.payload as SharedContact }
        assertEquals(listOf(OWN, KEYED), sent.map { it.node_num })
        assertEquals(listOf(true, false), sent.map { it.manually_verified })
    }

    @Test
    fun aCancelledSessionEnsureSettlesAsNotSentAndDoesNotBlockTheNextWrite() = runTest {
        everySuspend { ensureSession(any()) } calls { throw CancellationException("session ensure cancelled") }
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.UNFAVORITE)
        assertEquals(RemoteWriteStatus.NOT_SENT, vm.lastResult.value?.status)

        everySuspend { ensureSession(any()) } returns EnsureSessionResult.AlreadyActive
        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.REMOVE)

        assertEquals(listOf("removeRemoteNode"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.ACKNOWLEDGED, vm.lastResult.value?.status)
    }

    @Test
    fun noWriteIsSentWithoutAUsableAdminSession() = runTest {
        everySuspend { ensureSession(any()) } returns EnsureSessionResult.Timeout
        val vm = viewModel()

        vm.perform(node(KEYED, "Keyed"), RemoteContactAction.SEND_CONTACT)

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
    fun contactUrlIsSentToTheRemoteNodeAsOneContactNeverABlock() = runTest {
        remoteIs(Config.DeviceConfig.Role.ROUTER)
        val blocking =
            node(KEYED, "Keyed").toSharedContact().newBuilder().also { wb -> wb.should_ignore = true }.build()
        val vm = viewModel()

        vm.addContactFromUrl(blocking.getSharedContactUrl().toString())

        assertEquals(
            RemoteWriteResult(null, RemoteContactAction.SEND_CONTACT, RemoteWriteStatus.ACKNOWLEDGED),
            vm.lastResult.value,
        )
        val sent = remoteWrites().single().payload as SharedContact
        assertEquals(KEYED, sent.node_num)
        assertFalse(sent.should_ignore)
    }

    @Test
    fun aContactUrlSentToAClientBaseIsFavoritedOnlyAfterTheContactIsAcknowledged() = runTest {
        remoteIs(Config.DeviceConfig.Role.CLIENT_BASE)
        val contactAck = CompletableDeferred<Boolean>()
        radioController.remoteNodeDbAckFor = { kind -> if (kind == "addRemoteContact") contactAck.await() else true }
        val vm = viewModel()

        vm.addContactFromUrl(node(OWN, "Pasted").toSharedContact().getSharedContactUrl().toString())

        assertEquals(listOf("addRemoteContact"), remoteWrites().map { it.kind })

        contactAck.complete(true)

        assertEquals(listOf("addRemoteContact", "setRemoteFavorite"), remoteWrites().map { it.kind })
        assertEquals(OWN, remoteWrites().last().payload)
    }

    @Test
    fun aContactUrlAClientBaseDoesNotAcknowledgeIsNotFavorited() = runTest {
        remoteIs(Config.DeviceConfig.Role.CLIENT_BASE)
        radioController.remoteNodeDbAck = false
        val vm = viewModel()

        vm.addContactFromUrl(node(OWN, "Pasted").toSharedContact().getSharedContactUrl().toString())

        assertEquals(listOf("addRemoteContact"), remoteWrites().map { it.kind })
        assertEquals(RemoteWriteStatus.NOT_ACKNOWLEDGED, vm.lastResult.value?.status)
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
        const val OWN = 0x3F8B0E01
        const val KEYLESS = 0x1234ABCD
        const val DISPUTED = 0x0BADC0DE
        val KEY = ByteArray(32) { (it + 1).toByte() }.toByteString()
    }
}
