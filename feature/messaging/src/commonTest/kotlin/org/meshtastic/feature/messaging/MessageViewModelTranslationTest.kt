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
package org.meshtastic.feature.messaging

import androidx.compose.material3.SnackbarDuration
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NotificationManager
import org.meshtastic.core.repository.PacketRepository
import org.meshtastic.core.repository.QuickChatActionRepository
import org.meshtastic.core.repository.RadioConfigRepository
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.translation_failed
import org.meshtastic.core.resources.translation_model_download_failed
import org.meshtastic.core.resources.translation_not_required
import org.meshtastic.core.testing.FakeNodeRepository
import org.meshtastic.core.testing.TestDataFactory
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.feature.messaging.translation.DownloadResult
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.TranslationResult
import org.meshtastic.proto.ChannelSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val SNACKBAR_NOT_REQUIRED = "already-in-your-language"
private const val SNACKBAR_FAILED = "translation-failed"
private const val SNACKBAR_DOWNLOAD_FAILED = "model-download-failed"

@OptIn(ExperimentalCoroutinesApi::class)
class MessageViewModelTranslationTest {

    private class FakeMessageTranslationService : MessageTranslationService {
        var translateResult: TranslationResult = TranslationResult.Unavailable
        var languageAvailable = false
        var downloadResult: DownloadResult = DownloadResult.Success
        var translateCalls = 0
        var availabilityChecks = 0
        val downloadedTags = mutableListOf<List<String>>()

        override suspend fun translate(text: String, targetLocale: String): TranslationResult {
            translateCalls++
            return translateResult
        }

        override suspend fun isLanguageAvailable(locale: String): Boolean {
            availabilityChecks++
            return languageAvailable
        }

        override suspend fun downloadLanguageModels(languageTags: List<String>): DownloadResult {
            downloadedTags += languageTags
            return downloadResult
        }
    }

    private class RecordingSnackbarManager : SnackbarManager() {
        val messages = mutableListOf<String>()

        override fun showSnackbar(
            message: String,
            actionLabel: String?,
            withDismissAction: Boolean,
            duration: SnackbarDuration,
            onAction: (() -> Unit)?,
        ) {
            messages += message
        }
    }

    private lateinit var viewModel: MessageViewModel
    private val translationService = FakeMessageTranslationService()
    private val snackbarManager = RecordingSnackbarManager()

    private val radioConfigRepository: RadioConfigRepository = mock(MockMode.autofill)
    private val quickChatActionRepository: QuickChatActionRepository = mock(MockMode.autofill)
    private val packetRepository: PacketRepository = mock(MockMode.autofill)
    private val connectionStateProvider: ConnectionStateProvider = mock(MockMode.autofill)
    private val messagingController: MessagingController = mock(MockMode.autofill)
    private val sendMessageUseCase: SendMessageUseCase = mock(MockMode.autofill)
    private val customEmojiPrefs: CustomEmojiPrefs = mock(MockMode.autofill)
    private val homoglyphPrefs: HomoglyphPrefs = mock(MockMode.autofill)
    private val uiPrefs: UiPrefs = mock(MockMode.autofill)
    private val notificationManager: NotificationManager = mock(MockMode.autofill)

    private val testDispatcher = StandardTestDispatcher()

    private val message =
        Message(
            uuid = 7L,
            receivedTime = 1000L,
            node = TestDataFactory.createTestNodes(1).first(),
            text = "Hola, ¿cómo estás?",
            fromLocal = false,
            time = "10:00",
            read = true,
            status = MessageStatus.RECEIVED,
            routingError = 0,
            packetId = 123,
            emojis = emptyList(),
            snr = 2.5f,
            rssi = 90,
            hopsAway = 0,
            replyId = null,
        )

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        MessagingUiTextResolver.resolve = { text ->
            when (text) {
                is UiText.DynamicString -> text.value

                is UiText.Resource ->
                    when (text.res) {
                        Res.string.translation_not_required -> SNACKBAR_NOT_REQUIRED
                        Res.string.translation_failed -> SNACKBAR_FAILED
                        Res.string.translation_model_download_failed -> SNACKBAR_DOWNLOAD_FAILED
                        else -> error("Unexpected UiText resource in test: ${text.res}")
                    }
            }
        }

        every { radioConfigRepository.channelSetFlow } returns MutableStateFlow(ChannelSet())
        every { connectionStateProvider.connectionState } returns
            MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        every { customEmojiPrefs.customEmojiFrequency } returns MutableStateFlow<String?>(null)
        every { homoglyphPrefs.homoglyphEncodingEnabled } returns MutableStateFlow(false)
        every { uiPrefs.showQuickChat } returns MutableStateFlow(false)
        every { packetRepository.getContactSettings() } returns
            MutableStateFlow<Map<String, ContactSettings>>(emptyMap())
        every { quickChatActionRepository.getAllActions() } returns MutableStateFlow(emptyList())

        viewModel =
            MessageViewModel(
                savedStateHandle = SavedStateHandle(mapOf("contactKey" to "0!12345678")),
                nodeRepository = FakeNodeRepository(),
                radioConfigRepository = radioConfigRepository,
                quickChatActionRepository = quickChatActionRepository,
                connectionStateProvider = connectionStateProvider,
                messagingController = messagingController,
                packetRepository = packetRepository,
                sendMessageUseCase = sendMessageUseCase,
                customEmojiPrefs = customEmojiPrefs,
                homoglyphEncodingPrefs = homoglyphPrefs,
                uiPrefs = uiPrefs,
                notificationManager = notificationManager,
                messageTranslationService = translationService,
                snackbarManager = snackbarManager,
            )
    }

    @AfterTest
    fun tearDown() {
        MessagingUiTextResolver.resolve = { it.resolve() }
        Dispatchers.resetMain()
    }

    @Test
    fun translationAvailableIsFalseWhenServiceUnavailable() = runTest {
        translationService.languageAvailable = false
        viewModel.translationAvailable.test {
            assertEquals(false, awaitItem())
            // Drain the upstream availability check so this asserts the queried result, not just the initial value.
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(translationService.availabilityChecks >= 1)
    }

    @Test
    fun translationAvailableIsTrueWhenServiceSupportsLocale() = runTest {
        translationService.languageAvailable = true
        viewModel.translationAvailable.test {
            assertEquals(false, awaitItem())
            assertEquals(true, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun translateSuccessPersistsTranslation() = runTest {
        translationService.translateResult = TranslationResult.Success("Hello, how are you?")

        viewModel.translateMessage(message)
        advanceUntilIdle()

        assertEquals(1, translationService.translateCalls)
        verifySuspend { packetRepository.setMessageTranslation(7L, "Hello, how are you?") }
        assertTrue(snackbarManager.messages.isEmpty())
    }

    @Test
    fun translateWithCachedTranslationOnlyTogglesDisplay() = runTest {
        viewModel.translateMessage(message.copy(translatedText = "Hello", showTranslated = false))
        advanceUntilIdle()

        assertEquals(0, translationService.translateCalls)
        verifySuspend { packetRepository.setShowTranslated(7L, true) }
        verifySuspend(VerifyMode.exactly(0)) { packetRepository.setMessageTranslation(7L, "Hello") }
    }

    @Test
    fun translateNotRequiredShowsSnackbarAndPersistsNothing() = runTest {
        translationService.translateResult = TranslationResult.NotRequired

        viewModel.translateMessage(message)
        advanceUntilIdle()

        assertEquals(listOf(SNACKBAR_NOT_REQUIRED), snackbarManager.messages)
        verifySuspend(VerifyMode.exactly(0)) { packetRepository.setMessageTranslation(7L, "Hello, how are you?") }
    }

    @Test
    fun translateUnavailableShowsSnackbar() = runTest {
        translationService.translateResult = TranslationResult.Unavailable

        viewModel.translateMessage(message)
        advanceUntilIdle()

        assertEquals(listOf(SNACKBAR_FAILED), snackbarManager.messages)
    }

    @Test
    fun modelDownloadRequiredShowsPrompt() = runTest {
        translationService.translateResult =
            TranslationResult.ModelDownloadRequired(languageTags = listOf("es", "en"), estimatedSizeMb = 60)

        viewModel.translateMessage(message)
        advanceUntilIdle()

        val state = assertIs<TranslationDialogState.DownloadPrompt>(viewModel.translationDialogState.value)
        assertEquals(listOf("es", "en"), state.languageTags)
        assertEquals(60, state.estimatedSizeMb)
        assertEquals(message.uuid, state.message.uuid)
    }

    @Test
    fun confirmDownloadSuccessAutoContinuesToTranslation() = runTest {
        translationService.translateResult =
            TranslationResult.ModelDownloadRequired(languageTags = listOf("es"), estimatedSizeMb = 30)
        viewModel.translateMessage(message)
        advanceUntilIdle()

        translationService.translateResult = TranslationResult.Success("Hello, how are you?")
        viewModel.confirmTranslationModelDownload()
        advanceUntilIdle()

        assertEquals(listOf(listOf("es")), translationService.downloadedTags)
        assertEquals(TranslationDialogState.Hidden, viewModel.translationDialogState.value)
        verifySuspend { packetRepository.setMessageTranslation(7L, "Hello, how are you?") }
    }

    @Test
    fun confirmDownloadFailureShowsSnackbarAndHidesDialog() = runTest {
        translationService.translateResult =
            TranslationResult.ModelDownloadRequired(languageTags = listOf("es"), estimatedSizeMb = 30)
        viewModel.translateMessage(message)
        advanceUntilIdle()

        translationService.downloadResult = DownloadResult.Failed("no network")
        viewModel.confirmTranslationModelDownload()
        advanceUntilIdle()

        assertEquals(TranslationDialogState.Hidden, viewModel.translationDialogState.value)
        assertEquals(listOf(SNACKBAR_DOWNLOAD_FAILED), snackbarManager.messages)
        assertEquals(1, translationService.translateCalls)
    }

    @Test
    fun dismissTranslationDialogHidesPrompt() = runTest {
        translationService.translateResult =
            TranslationResult.ModelDownloadRequired(languageTags = listOf("es"), estimatedSizeMb = 30)
        viewModel.translateMessage(message)
        advanceUntilIdle()

        viewModel.dismissTranslationDialog()

        assertEquals(TranslationDialogState.Hidden, viewModel.translationDialogState.value)
    }

    @Test
    fun toggleShowTranslatedFlipsPersistedFlag() = runTest {
        viewModel.toggleShowTranslated(message.copy(translatedText = "Hello", showTranslated = true))
        advanceUntilIdle()
        verifySuspend { packetRepository.setShowTranslated(7L, false) }

        viewModel.toggleShowTranslated(message.copy(translatedText = "Hello", showTranslated = false))
        advanceUntilIdle()
        verifySuspend { packetRepository.setShowTranslated(7L, true) }
    }
}
