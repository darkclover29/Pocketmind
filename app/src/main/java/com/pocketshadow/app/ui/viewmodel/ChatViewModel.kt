package com.pocketshadow.app.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketshadow.app.data.db.entity.ChatSessionEntity
import com.pocketshadow.app.data.db.entity.MessageRole
import com.pocketshadow.app.data.repository.ChatHistoryRepository
import com.pocketshadow.app.data.repository.FontScale
import com.pocketshadow.app.data.repository.LlmRepository
import com.pocketshadow.app.data.repository.ModelFile
import com.pocketshadow.app.data.repository.ModelInfo
import com.pocketshadow.app.data.repository.SettingsRepository
import com.pocketshadow.app.ui.components.Message
import com.pocketshadow.app.ui.components.Role
import com.pocketshadow.app.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.yield
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

// ── UI state ──────────────────────────────────────────────────────────────────

/**
 * All stable state — does NOT contain the live streaming buffer.
 * The buffer lives in [ChatViewModel.streamingContent] so that per-token
 * updates only recompose the streaming bubble, not the entire screen.
 */
@Stable
data class ChatUiState(
    val messages         : List<Message>           = emptyList(),
    /** ID of the in-flight message; null when idle. */
    val streamingId      : String?                 = null,
    val isGenerating     : Boolean                 = false,
    /** ID of the message that should show the Retry button. */
    val retryMessageId   : String?                 = null,
    val sessionTitle     : String                  = "New Chat",
    val sessions         : List<ChatSessionEntity> = emptyList(),
    val showSettings     : Boolean                 = false,
    // ── Settings (mirrored for the UI) ────────────────────────────────────────
    val themeMode        : ThemeMode               = ThemeMode.DARK,
    val fontSize         : FontScale               = FontScale.NORMAL,
    val hapticFeedback   : Boolean                 = true,
    val soundEffects     : Boolean                 = true,
    val autoScroll       : Boolean                 = true,
    val saveHistory        : Boolean   = true,
    val contextWindowSize  : Int      = 20,
    // ── New features ──────────────────────────────────────────────────────────
    val documentContext    : String?  = null,   // null = Document Mode inactive
    val documentName       : String? = null,
    val showDocumentSheet  : Boolean  = false,
    val showModelInfo      : Boolean  = false,
    val customSystemPrompt : String   = "",
    val modelInfo          : ModelInfo = ModelInfo(),
    // ── TTS voice settings ────────────────────────────────────────────────────
    val ttsVoiceName  : String = "",    // blank = system default
    val ttsAccent     : String = "",    // blank = system default
    val ttsSpeechRate : Float  = 1.0f,
    val ttsPitch      : Float  = 1.0f,
    // ── Model selection ───────────────────────────────────────────────────────
    val availableModels    : List<ModelFile> = emptyList(),
    val selectedModelPath  : String          = "",
    // ── Input history (MRU) for swipe-up recall ────────────────────────────────
    val inputHistory       : List<String>    = emptyList(),
    // ── Search results ────────────────────────────────────────────────────────
    val searchQuery        : String          = "",
    val searchResults      : List<com.pocketshadow.app.data.db.SearchResult> = emptyList(),
    val isSearching        : Boolean         = false,
    // ── Local encrypted archive ──────────────────────────────────────────────
    val archiveStatus      : String?         = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmRepo     : LlmRepository,
    private val historyRepo : ChatHistoryRepository,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    val documentExcerpts: StateFlow<String> = llmRepo.documentExcerpts

    /**
     * Live streaming buffer — intentionally a separate StateFlow so that
     * per-token emissions only recompose the streaming bubble composable,
     * leaving all historical message items untouched.
     */
    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

    /**
     * Composer text — separate from ChatUiState for the same reason as the
     * streaming buffer: putting it in the big state object made EVERY
     * keystroke copy the whole UiState and restart the whole screen's
     * recomposition scope. Collected inside ChatInputArea only.
     */
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private var sessionId   : String? = null
    private var streamingJob: Job?    = null
    private var titleJob    : Job?    = null

    init {
        viewModelScope.launch {
            historyRepo.getAllSessions().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
        // All 12 settings flows combined into ONE collector (was 14 separate
        // coroutines, each triggering its own _uiState.update on startup).
        viewModelScope.launch {
            combine(
                listOf(
                    settingsRepo.themeMode, settingsRepo.fontSize, settingsRepo.hapticFeedback, settingsRepo.soundEffects,
                    settingsRepo.autoScroll, settingsRepo.saveHistory, settingsRepo.contextWindowSize,
                    settingsRepo.customSystemPrompt, settingsRepo.ttsVoiceName, settingsRepo.ttsAccent,
                    settingsRepo.ttsSpeechRate, settingsRepo.ttsPitch, settingsRepo.selectedModelPath
                )
            ) { v -> v.copyOf() }.collect { v ->
                _uiState.update {
                    it.copy(
                        themeMode          = v[0]  as ThemeMode,
                        fontSize           = v[1]  as FontScale,
                        hapticFeedback     = v[2]  as Boolean,
                        soundEffects       = v[3]  as Boolean,
                        autoScroll         = v[4]  as Boolean,
                        saveHistory        = v[5]  as Boolean,
                        contextWindowSize  = v[6]  as Int,
                        customSystemPrompt = v[7]  as String,
                        ttsVoiceName       = v[8]  as String,
                        ttsAccent          = v[9]  as String,
                        ttsSpeechRate      = v[10] as Float,
                        ttsPitch           = v[11] as Float,
                        selectedModelPath  = v[12] as String
                    )
                }
            }
        }
        viewModelScope.launch {
            combine(llmRepo.modelInfo, llmRepo.availableModels) { info, models -> info to models }
                .collect { (info, models) ->
                    _uiState.update { it.copy(modelInfo = info, availableModels = models) }
                }
        }
        // Scan for model files + warm up the engine so the first message
        // doesn't pay the multi-second model-load cost.
        viewModelScope.launch {
            llmRepo.scanAvailableModels()
            // Let the first Compose frame and input pipeline settle before the
            // native model allocates its large mmap/KV cache.
            yield()
            llmRepo.warmUp()
        }
        // Input history (MRU) for swipe-up recall in ChatInputArea
        viewModelScope.launch {
            historyRepo.getInputHistory().collect { entities ->
                _uiState.update { it.copy(inputHistory = entities.map { e -> e.text }) }
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onInputChanged(text: String) { _inputText.value = text }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun showSettings()    = _uiState.update { it.copy(showSettings = true) }
    fun dismissSettings() = _uiState.update { it.copy(showSettings = false) }

    fun setThemeMode(mode: ThemeMode)   { settingsRepo.setThemeMode(mode) }
    fun setFontSize(scale: FontScale)   { settingsRepo.setFontSize(scale) }
    fun setHapticFeedback(on: Boolean)  { settingsRepo.setHapticFeedback(on) }
    fun setSoundEffects(on: Boolean)    { settingsRepo.setSoundEffects(on) }
    fun setAutoScroll(on: Boolean)      { settingsRepo.setAutoScroll(on) }
    fun setSaveHistory(on: Boolean)     { settingsRepo.setSaveHistory(on) }
    fun setContextWindowSize(n: Int)       { settingsRepo.setContextWindowSize(n) }
    fun setCustomSystemPrompt(p: String)   { settingsRepo.setCustomSystemPrompt(p) }

    // ── Model selection ───────────────────────────────────────────────────────
    fun setSelectedModel(path: String) {
        settingsRepo.setSelectedModelPath(path)
        // Reset the loaded model so next message reloads with new file
        llmRepo.resetModel()
    }

    fun refreshAvailableModels() { viewModelScope.launch { llmRepo.scanAvailableModels() } }

    fun retryModelLoading() {
        viewModelScope.launch {
            llmRepo.resetModel()
            llmRepo.scanAvailableModels()
            llmRepo.warmUp()
        }
    }

    // ── Local encrypted archive ──────────────────────────────────────────────
    suspend fun exportEncryptedArchive(passphrase: String): Result<ByteArray> =
        runCatching { historyRepo.exportEncryptedArchive(passphrase) }
            .onSuccess {
                _uiState.update { state ->
                    state.copy(archiveStatus = "Encrypted chat archive ready.")
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(archiveStatus = error.message ?: "Export failed.")
                }
            }

    suspend fun importEncryptedArchive(bytes: ByteArray, passphrase: String): Result<Int> =
        runCatching { historyRepo.importEncryptedArchive(bytes, passphrase) }
            .onSuccess { count ->
                _uiState.update { state ->
                    state.copy(archiveStatus = "Imported $count chat${if (count == 1) "" else "s"}.")
                }
            }
            .onFailure { error ->
                _uiState.update { state ->
                    state.copy(archiveStatus = error.message ?: "Import failed. Check the password and file.")
                }
            }

    fun clearArchiveStatus() {
        _uiState.update { it.copy(archiveStatus = null) }
    }

    fun setArchiveStatus(message: String?) {
        _uiState.update { it.copy(archiveStatus = message) }
    }

    // ── TTS settings ──────────────────────────────────────────────────────────
    fun setTtsVoiceName(name: String)  { settingsRepo.setTtsVoiceName(name) }
    fun setTtsAccent(accent: String)   { settingsRepo.setTtsAccent(accent) }
    fun setTtsSpeechRate(rate: Float)  { settingsRepo.setTtsSpeechRate(rate) }
    fun setTtsPitch(pitch: Float)      { settingsRepo.setTtsPitch(pitch) }

    // ── Document mode ─────────────────────────────────────────────────────────
    fun showDocumentSheet()                { _uiState.update { it.copy(showDocumentSheet = true) } }
    fun dismissDocumentSheet()             { _uiState.update { it.copy(showDocumentSheet = false) } }
    fun setDocumentContext(text: String?, name: String? = null) {
        if (text != _uiState.value.documentContext) llmRepo.clearDocumentExcerpts()
        _uiState.update { it.copy(documentContext = text?.ifBlank { null }, documentName = name, showDocumentSheet = false) }
    }
    fun clearDocumentContext() {
        llmRepo.clearDocumentExcerpts()
        _uiState.update { it.copy(documentContext = null, documentName = null) }
    }

    // ── Model info ────────────────────────────────────────────────────────────
    fun showModelInfo()                    { _uiState.update { it.copy(showModelInfo = true) } }
    fun dismissModelInfo()                 { _uiState.update { it.copy(showModelInfo = false) } }

    // ── Session management ────────────────────────────────────────────────────

    fun newChat() {
        // Cancel in-flight generation first — the per-request Channel in
        // LlmRepository will be closed when streamResponse's flow is cancelled,
        // which triggers cleanup in generateResponseAsync's listener.
        streamingJob?.cancel()
        _streamingContent.value = ""
        _inputText.value = ""

        sessionId = null
        _uiState.update {
            it.copy(
                messages       = emptyList(),
                streamingId    = null,
                sessionTitle   = "New Chat",
                isGenerating   = false,
                retryMessageId = null
            )
        }
    }

    fun loadSession(session: ChatSessionEntity) {
        streamingJob?.cancel()
        _streamingContent.value = ""

        viewModelScope.launch {
            val messages = historyRepo.loadSessionMessages(session.sessionId).map { e ->
                Message(
                    id        = e.messageId,
                    role      = if (e.role == MessageRole.USER) Role.USER else Role.AI,
                    content   = e.content,
                    timestamp = e.timestamp
                )
            }
            sessionId = session.sessionId
            _inputText.value = ""
            _uiState.update {
                it.copy(
                    messages       = messages,
                    streamingId    = null,
                    sessionTitle   = session.title,
                    isGenerating   = false,
                    retryMessageId = null
                )
            }
        }
    }

    fun deleteSession(targetId: String) {
        viewModelScope.launch {
            historyRepo.deleteSession(targetId)
            if (sessionId == targetId) newChat()
        }
    }

    fun renameSession(targetId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        val trimmed = newTitle.trim()
        viewModelScope.launch {
            historyRepo.updateTitle(targetId, trimmed)
            // Reflect immediately in the top bar if this is the active session
            if (sessionId == targetId) {
                _uiState.update { it.copy(sessionTitle = trimmed) }
            }
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        val isFirst     = _uiState.value.messages.isEmpty()
        val now         = System.currentTimeMillis()
        val userMsg     = Message(role = Role.USER, content = text, timestamp = now)
        val assistantId = UUID.randomUUID().toString()

        _inputText.value = ""
        _uiState.update {
            it.copy(
                messages       = it.messages + userMsg,
                isGenerating   = true,
                streamingId    = assistantId,
                retryMessageId = null
            )
        }

        // Record input history (MRU, deduped, capped at 10)
        viewModelScope.launch { historyRepo.recordInput(text) }

        val contextSnapshot = _uiState.value.messages
        streamingJob = viewModelScope.launch {
            streamAndPersist(
                sessionId   = getOrCreateSession(),
                userText    = text,
                isFirst     = isFirst,
                context     = contextSnapshot,
                assistantId = assistantId
            )
        }
    }

    fun sendQuickAction(actionPrompt: String) {
        if (_uiState.value.isGenerating) return
        _inputText.value = actionPrompt
        sendMessage()
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    fun stopGeneration() {
        // Cancelling the job runs streamAndPersist's `finally`, which commits
        // the partial text exactly once — no duplicate bubble, no double save.
        streamingJob?.cancel()
    }

    // ── Retry ─────────────────────────────────────────────────────────────────

    fun retryGeneration() {
        val messages = _uiState.value.messages
        val lastMsg  = messages.lastOrNull() ?: return
        if (lastMsg.role != Role.AI || !lastMsg.content.trimStart().startsWith(ERROR_PREFIX)) return

        val cleaned      = messages.dropLast(1)
        val lastUserText = cleaned.lastOrNull { it.role == Role.USER }?.content ?: return
        val sid          = sessionId ?: return
        val assistantId  = UUID.randomUUID().toString()

        _uiState.update {
            it.copy(
                messages       = cleaned,
                isGenerating   = true,
                streamingId    = assistantId,
                retryMessageId = null
            )
        }
        streamingJob = viewModelScope.launch {
            streamAndPersist(
                sessionId   = sid,
                userText    = lastUserText,
                isFirst     = false,
                context     = cleaned,
                assistantId = assistantId
            )
        }
    }

    // ── Regenerate ──────────────────────────────────────────────────────────────
    //
    // Removes the AI message at [messageId] and re-streams from the last user
    // message before it. Persists the deletion to history. Does NOT run if
    // already generating.

    fun regenerateMessage(messageId: String) {
        if (_uiState.value.isGenerating) return
        val messages = _uiState.value.messages
        val idx      = messages.indexOfFirst { it.id == messageId }
        if (idx < 0) return
        val target = messages[idx]
        if (target.role != Role.AI) return

        // Find the user message preceding this AI message
        var userIdx = idx - 1
        while (userIdx >= 0 && messages[userIdx].role != Role.USER) userIdx--
        if (userIdx < 0) return
        val lastUserText = messages[userIdx].content

        // Drop the AI message and everything after it
        val cleaned = messages.subList(0, idx).toList()
        val sid     = sessionId ?: return
        val assistantId = UUID.randomUUID().toString()

        // Persist deletion of the old AI message
        viewModelScope.launch(NonCancellable) {
            historyRepo.deleteMessage(messageId)
        }

        _uiState.update {
            it.copy(
                messages       = cleaned,
                isGenerating   = true,
                streamingId    = assistantId,
                retryMessageId = null
            )
        }
        streamingJob = viewModelScope.launch {
            streamAndPersist(
                sessionId   = sid,
                userText    = lastUserText,
                isFirst     = false,
                context     = cleaned,
                assistantId = assistantId
            )
        }
    }

    // ── Core streaming ────────────────────────────────────────────────────────

    private suspend fun streamAndPersist(
        sessionId  : String,
        userText   : String,
        isFirst    : Boolean,
        context    : List<Message>,
        assistantId: String
    ) {
        _streamingContent.value = ""

        val buffer = StringBuilder()
        try {
            val windowSize = _uiState.value.contextWindowSize
            val history    = buildTokenBoundedHistory(context, windowSize)
            val docCtx     = _uiState.value.documentContext

            // Throttle UI pushes to ~1 per 50 ms: `buffer.toString()` allocates a
            // full copy of the (growing) response, so doing it per token causes
            // GC churn and needless recompositions that jank scrolling.
            var lastPushMs = 0L
            llmRepo.streamResponse(sessionId, history, docCtx).collect { token ->
                buffer.append(token)
                val now = System.currentTimeMillis()
                if (now - lastPushMs >= 50L) {
                    lastPushMs = now
                    _streamingContent.value = buffer.toString()
                }
            }
            _streamingContent.value = buffer.toString()  // final flush
        } catch (e: CancellationException) {
            throw e   // user tapped Stop — finally below commits the partial text
        } catch (e: Exception) {
            // Engine/inference failure must never crash the app — surface an
            // error bubble instead. ERROR_PREFIX makes the Retry button appear.
            if (buffer.isNotEmpty()) buffer.append("\n\n")
            buffer.append("$ERROR_PREFIX Something went wrong while generating a response. Tap Retry to try again.")
        } finally {
            val finalText = buffer.toString()
            val isError   = finalText.trimStart().startsWith(ERROR_PREFIX)

            // Native perf counters for this turn (tok/s · tokens · TTFT).
            // Read-and-clear so a failed turn never shows the previous turn's stats.
            val statsLine = llmRepo.consumeGenerationStats()
                ?.takeIf { !isError && finalText.isNotBlank() && it.decodeTokens > 0 }
                ?.let {
                    "%.1f tok/s · %d tokens · first token %.1fs"
                        .format(it.decodeTokensPerSecond, it.decodeTokens, it.timeToFirstTokenSec)
                }

            if (finalText.isNotBlank() && !isError && _uiState.value.saveHistory) {
                // NonCancellable: this finally also runs when Stop cancels the job —
                // a plain suspend call here would throw CancellationException.
                withContext(NonCancellable) {
                    historyRepo.saveCompleteInteraction(sessionId, userText, finalText)
                }
            }
            // Track completed conversations – triggers in-app review at milestones
            if (finalText.isNotBlank() && !isError) {
                settingsRepo.incrementConversationCount()
            }

            val finalMsg = Message(
                id         = assistantId,
                role       = Role.AI,
                content    = finalText,
                timestamp  = System.currentTimeMillis(),
                statsLine  = statsLine
            )
            _streamingContent.value = ""
            _uiState.update { s ->
                s.copy(
                    messages       = if (finalText.isNotBlank()) s.messages + finalMsg else s.messages,
                    streamingId    = null,
                    isGenerating   = false,
                    retryMessageId = if (isError && finalText.isNotBlank()) assistantId else null
                )
            }

            if (isFirst && !isError) launchTitleGeneration(sessionId, userText, finalText)
        }
    }

    // ── Title generation ──────────────────────────────────────────────────────
    //
    // First attempts AI-based 3-5 word title generation. Falls back to the
    // heuristic truncation if the AI call fails or returns empty/garbage.

    private fun launchTitleGeneration(sessionId: String, userText: String, aiText: String) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            var title: String? = null
            try {
                // Try AI-based title generation (3-5 words, 32 token cap)
                val aiTitle = llmRepo.generateTitle(userText, aiText)
                if (!aiTitle.isNullOrBlank() && aiTitle.length <= 80) {
                    title = aiTitle.trim()
                }
            } catch (_: Exception) {
                // Fall through to heuristic
            }
            if (title == null) {
                title = userText.trim().let {
                    if (it.length <= 36) it else it.take(33) + "..."
                }
            }
            historyRepo.updateTitle(sessionId, title)
            _uiState.update { it.copy(sessionTitle = title) }
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = query.isNotBlank()) }
        searchJob?.cancel()   // drop in-flight query — prevents stale results landing late
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(250)   // debounce: don't hit the DB per keystroke
            val results = historyRepo.searchMessages(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
    }

    private suspend fun getOrCreateSession() =
        sessionId ?: historyRepo.createSession().sessionId.also { sessionId = it }

    /**
     * Trims history by estimated tokens, not just message count: one giant
     * pasted message would otherwise blow up prefill time (and can overflow
     * the model context). Walks backwards from the newest message; the
     * current question is always included.
     *
     * If [windowSize] is 0, the message count limit is disabled (unlimited
     * context) — the char budget still applies to prevent prefill overflow.
     */
    private fun buildTokenBoundedHistory(
        context   : List<Message>,
        windowSize: Int
    ): List<Pair<Boolean, String>> {
        val result = ArrayDeque<Pair<Boolean, String>>()
        var usedChars = 0
        for (msg in context.asReversed()) {
            if (windowSize > 0 && result.size >= windowSize) break
            val content =
                if (msg.content.length > MAX_MESSAGE_CHARS)
                    msg.content.take(MAX_MESSAGE_CHARS) + "\n[truncated]"
                else msg.content
            if (result.isNotEmpty() && usedChars + content.length > HISTORY_CHAR_BUDGET) break
            usedChars += content.length
            result.addFirst((msg.role == Role.USER) to content)
        }
        return result.toList()
    }

    companion object {
        /** ~3k tokens at ≈4 chars/token — keeps prefill bounded and predictable. */
        private const val HISTORY_CHAR_BUDGET = 12_000
        /** Cap for any single message included in history. */
        private const val MAX_MESSAGE_CHARS   = 4_000
        /** LlmRepository prefixes error messages with this — used to show Retry. */
        private const val ERROR_PREFIX = "⚠️"
    }
}
