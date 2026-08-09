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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.common.util.currentLocaleCode
import org.meshtastic.core.common.util.ioDispatcher
import org.meshtastic.core.model.ContactSettings
import org.meshtastic.core.model.Message
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.repository.ConnectionStateProvider
import org.meshtastic.core.repository.CustomEmojiPrefs
import org.meshtastic.core.repository.HomoglyphPrefs
import org.meshtastic.core.repository.MessagingController
import org.meshtastic.core.repository.NodeRepository
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
import org.meshtastic.core.ui.util.SnackbarManager
import org.meshtastic.core.ui.viewmodel.safeLaunch
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import org.meshtastic.feature.messaging.translation.DownloadResult
import org.meshtastic.feature.messaging.translation.MessageTranslationService
import org.meshtastic.feature.messaging.translation.TranslationResult
import org.meshtastic.proto.ChannelSet

/**
 * Test seam for resolving [UiText] to a plain string. Unit tests replace [resolve] so ViewModel snackbar paths don't
 * depend on the compose-resources runtime (same pattern as NodeDetailUiTextResolver).
 */
internal object MessagingUiTextResolver {
    var resolve: suspend (UiText) -> String = { it.resolve() }
}

/** State of the translation-model download dialog on the message screen. */
sealed interface TranslationDialogState {
    data object Hidden : TranslationDialogState

    /** Ask the user to confirm downloading the missing [languageTags] models (~[estimatedSizeMb] MB). */
    data class DownloadPrompt(val message: Message, val languageTags: List<String>, val estimatedSizeMb: Int) :
        TranslationDialogState

    data class Downloading(val message: Message) : TranslationDialogState
}

@Suppress("LongParameterList", "TooManyFunctions")
@KoinViewModel
class MessageViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val nodeRepository: NodeRepository,
    radioConfigRepository: RadioConfigRepository,
    quickChatActionRepository: QuickChatActionRepository,
    private val connectionStateProvider: ConnectionStateProvider,
    private val messagingController: MessagingController,
    private val packetRepository: PacketRepository,
    private val uiPrefs: UiPrefs,
    private val customEmojiPrefs: CustomEmojiPrefs,
    private val homoglyphEncodingPrefs: HomoglyphPrefs,
    private val notificationManager: NotificationManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val messageTranslationService: MessageTranslationService,
    private val snackbarManager: SnackbarManager,
) : ViewModel() {
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _draftMessage = MutableStateFlow(savedStateHandle.get<String>("draftMessage") ?: "")
    val draftMessage: StateFlow<String> = _draftMessage.asStateFlow()

    private var pendingDraftPersistence: Job? = null

    /**
     * Updates the in-memory draft immediately. The durable [SavedStateHandle] write is debounced by
     * [DRAFT_PERSISTENCE_DELAY_MS] — rapid edits cancel the prior pending flush, so only the trailing value persists.
     * On process death within the debounce window the last ≤[DRAFT_PERSISTENCE_DELAY_MS] of typing is not durably
     * saved; this is a conscious trade-off to avoid per-keystroke jank.
     */
    fun setDraftMessage(text: String) {
        _draftMessage.value = text
        pendingDraftPersistence?.cancel()
        pendingDraftPersistence =
            viewModelScope.launch {
                delay(DRAFT_PERSISTENCE_DELAY_MS)
                savedStateHandle["draftMessage"] = text
            }
    }

    fun clearDraftMessage() {
        _draftMessage.value = ""
        pendingDraftPersistence?.cancel()
        pendingDraftPersistence = null
        savedStateHandle["draftMessage"] = ""
    }

    val ourNodeInfo = nodeRepository.ourNodeInfo

    val connectionState = connectionStateProvider.connectionState

    val nodeList: StateFlow<List<Node>> = nodeRepository.getNodes().stateInWhileSubscribed(initialValue = emptyList())

    val channels = radioConfigRepository.channelSetFlow.stateInWhileSubscribed(ChannelSet())

    val showQuickChat = uiPrefs.showQuickChat

    private val _showFiltered = MutableStateFlow(false)
    val showFiltered: StateFlow<Boolean> = _showFiltered.asStateFlow()

    val quickChatActions = quickChatActionRepository.getAllActions().stateInWhileSubscribed(initialValue = emptyList())

    val contactSettings: StateFlow<Map<String, ContactSettings>> =
        packetRepository.getContactSettings().stateInWhileSubscribed(initialValue = emptyMap())

    private val contactKeyForPagedMessages: MutableStateFlow<String?> = MutableStateFlow(null)
    private val pagedMessagesForContactKey: Flow<PagingData<Message>> =
        combine(contactKeyForPagedMessages.filterNotNull(), _showFiltered, contactSettings) {
                contactKey,
                showFiltered,
                settings,
            ->
            // If filtering is disabled for this contact, always include filtered messages
            val filteringDisabled = settings[contactKey]?.filteringDisabled ?: false
            val includeFiltered = showFiltered || filteringDisabled
            contactKey to includeFiltered
        }
            .flatMapLatest { (contactKey, includeFiltered) ->
                packetRepository.getMessagesFromPaged(contactKey, includeFiltered, ::getNode)
            }
            .cachedIn(viewModelScope)

    val frequentEmojis: List<String>
        get() =
            customEmojiPrefs.customEmojiFrequency.value
                ?.split(",")
                ?.mapNotNull { entry ->
                    val parts = entry.split("=", limit = 2)
                    val count = parts.getOrNull(1)?.toIntOrNull()
                    if (parts.size == 2 && parts[0].isNotEmpty() && count != null) parts[0] to count else null
                }
                ?.sortedByDescending { it.second }
                ?.map { it.first }
                ?.take(6) ?: listOf("👍", "👎", "😂", "🔥", "❤️", "😮")

    val homoglyphEncodingEnabled = homoglyphEncodingPrefs.homoglyphEncodingEnabled

    val firstUnreadMessageUuid: StateFlow<Long?> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getFirstUnreadMessageUuid(it) }
            .stateInWhileSubscribed(null)

    val hasUnreadMessages: StateFlow<Boolean?> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.hasUnreadMessages(it) }
            .stateInWhileSubscribed(null)

    val unreadCount: StateFlow<Int> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getUnreadCountFlow(it) }
            .stateInWhileSubscribed(0)

    val filteredCount: StateFlow<Int> =
        contactKeyForPagedMessages
            .filterNotNull()
            .flatMapLatest { packetRepository.getFilteredCountFlow(it) }
            .stateInWhileSubscribed(0)

    // region ── Search ──

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive.asStateFlow()

    private val _searchResultIndex = MutableStateFlow(0)
    val searchResultIndex: StateFlow<Int> = _searchResultIndex.asStateFlow()

    @OptIn(FlowPreview::class)
    val searchResults: StateFlow<List<Message>> =
        combine(_searchQuery, contactKeyForPagedMessages) { query, contactKey -> query to contactKey }
            .debounce(SEARCH_DEBOUNCE_MS)
            .flatMapLatest { (query, contactKey) ->
                if (query.length < MIN_SEARCH_LENGTH) {
                    flowOf(emptyList())
                } else {
                    packetRepository.searchMessages(query, contactKey, ::getNode)
                }
            }
            .stateInWhileSubscribed(emptyList())

    /** The currently focused search result message (for scroll-to-match). */
    val currentSearchResult: StateFlow<Message?> =
        combine(searchResults, _searchResultIndex) { results, index -> results.getOrNull(index) }
            .stateInWhileSubscribed(null)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _searchResultIndex.value = 0
    }

    fun navigateToNextResult() {
        val max = searchResults.value.size
        if (max == 0) return
        _searchResultIndex.update { (it + 1) % max }
    }

    fun navigateToPreviousResult() {
        val max = searchResults.value.size
        if (max == 0) return
        _searchResultIndex.update { if (it == 0) max - 1 else it - 1 }
    }

    fun toggleSearch() {
        _isSearchActive.value = !_isSearchActive.value
        if (!_isSearchActive.value) {
            _searchQuery.value = ""
            _searchResultIndex.value = 0
        }
    }

    fun closeSearch() {
        _isSearchActive.value = false
        _searchQuery.value = ""
        _searchResultIndex.value = 0
    }

    // endregion

    init {
        val contactKey = savedStateHandle.get<String>("contactKey")
        if (contactKey != null) {
            contactKeyForPagedMessages.value = contactKey
        }
    }

    fun setContactKey(contactKey: String) {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    fun getMessagesFromPaged(contactKey: String): Flow<PagingData<Message>> {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
        return pagedMessagesForContactKey
    }

    /**
     * Returns a non-paged reactive [Flow] of messages for a conversation. Used by desktop targets that don't use
     * paging-compose.
     *
     * @param contactKey The unique contact key identifying the conversation.
     * @param limit Optional maximum number of messages to return (null = all).
     */
    fun getMessagesFlow(contactKey: String, limit: Int? = null): Flow<List<Message>> {
        if (contactKeyForPagedMessages.value != contactKey) {
            contactKeyForPagedMessages.value = contactKey
        }
        return flow { emitAll(packetRepository.getMessagesFrom(contactKey, limit = limit, getNode = ::getNode)) }
    }

    fun toggleShowQuickChat() {
        uiPrefs.setShowQuickChat(!uiPrefs.showQuickChat.value)
    }

    fun toggleShowFiltered() {
        _showFiltered.update { !it }
    }

    fun setContactFilteringDisabled(contactKey: String, disabled: Boolean) {
        safeLaunch(context = ioDispatcher, tag = "setContactFilteringDisabled") {
            packetRepository.setContactFilteringDisabled(contactKey, disabled)
        }
    }

    fun getNode(userId: String?) = nodeRepository.getNode(userId ?: NodeAddress.ID_BROADCAST)

    fun getUser(userId: String?) = nodeRepository.getUser(userId ?: NodeAddress.ID_BROADCAST)

    /**
     * Sends a message to a contact or channel.
     *
     * If the message is a direct message (no channel specified), this function will:
     * - If the device firmware version is older than 2.7.12, it will mark the destination node as a favorite to prevent
     *   it from being removed from the on-device node database.
     * - If the device firmware version is 2.7.12 or newer, it will send a shared contact to the destination node.
     *
     * @param str The message content.
     * @param contactKey The unique contact key, which is a combination of channel (optional) and node ID. Defaults to
     *   broadcasting on channel 0.
     * @param replyId The ID of the message this is a reply to, if any.
     */
    fun sendMessage(str: String, contactKey: String = "0${NodeAddress.ID_BROADCAST}", replyId: Int? = null) {
        safeLaunch(tag = "sendMessage") { sendMessageUseCase.invoke(str, contactKey, replyId) }
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) =
        safeLaunch(tag = "sendReaction") { messagingController.sendReaction(emoji, replyId, contactKey) }

    fun deleteMessages(uuidList: List<Long>) =
        safeLaunch(context = ioDispatcher, tag = "deleteMessages") { packetRepository.deleteMessages(uuidList) }

    // region ── Translation ──

    /** Whether on-device translation into the current locale is possible (always false on F-Droid/desktop). */
    val translationAvailable: StateFlow<Boolean> =
        flow { emit(messageTranslationService.isLanguageAvailable(currentLocaleCode())) }
            .stateInWhileSubscribed(initialValue = false)

    private val _translationDialogState = MutableStateFlow<TranslationDialogState>(TranslationDialogState.Hidden)
    val translationDialogState: StateFlow<TranslationDialogState> = _translationDialogState.asStateFlow()

    /** Translates [message] into the device language, or just re-shows a previously persisted translation. */
    // Translation methods use plain safeLaunch (like sendMessage): every call inside is a main-safe suspend
    // function (the repository hops to IO internally), and tests stay on the deterministic test scheduler.
    fun translateMessage(message: Message) = safeLaunch(tag = "translateMessage") {
        if (message.translatedText != null) {
            packetRepository.setShowTranslated(message.uuid, true)
            return@safeLaunch
        }
        when (val result = messageTranslationService.translate(message.text, currentLocaleCode())) {
            is TranslationResult.Success ->
                packetRepository.setMessageTranslation(message.uuid, result.translatedText)

            is TranslationResult.NotRequired ->
                snackbarManager.showSnackbar(
                    MessagingUiTextResolver.resolve(UiText.Resource(Res.string.translation_not_required)),
                )

            is TranslationResult.ModelDownloadRequired ->
                _translationDialogState.value =
                    TranslationDialogState.DownloadPrompt(message, result.languageTags, result.estimatedSizeMb)

            is TranslationResult.Unavailable ->
                snackbarManager.showSnackbar(
                    MessagingUiTextResolver.resolve(UiText.Resource(Res.string.translation_failed)),
                )
        }
    }

    fun toggleShowTranslated(message: Message) = safeLaunch(tag = "toggleShowTranslated") {
        packetRepository.setShowTranslated(message.uuid, !message.showTranslated)
    }

    fun confirmTranslationModelDownload() {
        val prompt = _translationDialogState.value as? TranslationDialogState.DownloadPrompt ?: return
        safeLaunch(tag = "downloadTranslationModel") {
            _translationDialogState.value = TranslationDialogState.Downloading(prompt.message)
            val result = messageTranslationService.downloadLanguageModels(prompt.languageTags)
            _translationDialogState.value = TranslationDialogState.Hidden
            when (result) {
                is DownloadResult.Success -> translateMessage(prompt.message)

                is DownloadResult.Failed ->
                    snackbarManager.showSnackbar(
                        MessagingUiTextResolver.resolve(UiText.Resource(Res.string.translation_model_download_failed)),
                    )
            }
        }
    }

    fun dismissTranslationDialog() {
        _translationDialogState.value = TranslationDialogState.Hidden
    }

    // endregion

    fun clearUnreadCount(contact: String, messageUuid: Long, lastReadTimestamp: Long) =
        safeLaunch(context = ioDispatcher, tag = "clearUnreadCount") {
            val existingTimestamp = contactSettings.value[contact]?.lastReadMessageTimestamp ?: Long.MIN_VALUE
            if (lastReadTimestamp <= existingTimestamp) {
                return@safeLaunch
            }
            packetRepository.clearUnreadCount(contact, lastReadTimestamp)
            packetRepository.updateLastReadMessage(contact, messageUuid, lastReadTimestamp)
            val unreadCount = packetRepository.getUnreadCount(contact)
            if (unreadCount == 0) notificationManager.cancel(contact.hashCode())
        }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val MIN_SEARCH_LENGTH = 2
        private const val DRAFT_PERSISTENCE_DELAY_MS = 300L
    }
}
