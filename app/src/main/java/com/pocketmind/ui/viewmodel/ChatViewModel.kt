package com.pocketmind.ui.viewmodel

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.db.entity.ChatSessionEntity
import com.pocketmind.data.db.entity.MessageRole
import com.pocketmind.data.repository.ChatHistoryRepository
import com.pocketmind.data.repository.FontScale
import com.pocketmind.data.repository.LlmRepository
import com.pocketmind.data.repository.ModelFile
import com.pocketmind.data.repository.ModelInfo
import com.pocketmind.data.repository.SettingsRepository
import com.pocketmind.ui.components.Message
import com.pocketmind.ui.components.Role
import com.pocketmind.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.*
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
    val inputText        : String                  = "",
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
    val autoScroll       : Boolean                 = true,
    val saveHistory        : Boolean   = true,
    val contextWindowSize  : Int      = 20,
    // ── New features ──────────────────────────────────────────────────────────
    val documentContext    : String?  = null,   // null = Document Mode inactive
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

    /**
     * Live streaming buffer — intentionally a separate StateFlow so that
     * per-token emissions only recompose the streaming bubble composable,
     * leaving all historical message items untouched.
     */
    private val _streamingContent = MutableStateFlow("")
    val streamingContent: StateFlow<String> = _streamingContent.asStateFlow()

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
                    settingsRepo.themeMode, settingsRepo.fontSize, settingsRepo.hapticFeedback,
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
                        autoScroll         = v[3]  as Boolean,
                        saveHistory        = v[4]  as Boolean,
                        contextWindowSize  = v[5]  as Int,
                        customSystemPrompt = v[6]  as String,
                        ttsVoiceName       = v[7]  as String,
                        ttsAccent          = v[8]  as String,
                        ttsSpeechRate      = v[9]  as Float,
                        ttsPitch           = v[10] as Float,
                        selectedModelPath  = v[11] as String
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
            llmRepo.warmUp()
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onInputChanged(text: String) = _uiState.update { it.copy(inputText = text) }

    // ── Settings ──────────────────────────────────────────────────────────────

    fun showSettings()    = _uiState.update { it.copy(showSettings = true) }
    fun dismissSettings() = _uiState.update { it.copy(showSettings = false) }

    fun setThemeMode(mode: ThemeMode)   { settingsRepo.setThemeMode(mode) }
    fun setFontSize(scale: FontScale)   { settingsRepo.setFontSize(scale) }
    fun setHapticFeedback(on: Boolean)  { settingsRepo.setHapticFeedback(on) }
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

    // ── TTS settings ──────────────────────────────────────────────────────────
    fun setTtsVoiceName(name: String)  { settingsRepo.setTtsVoiceName(name) }
    fun setTtsAccent(accent: String)   { settingsRepo.setTtsAccent(accent) }
    fun setTtsSpeechRate(rate: Float)  { settingsRepo.setTtsSpeechRate(rate) }
    fun setTtsPitch(pitch: Float)      { settingsRepo.setTtsPitch(pitch) }

    // ── Document mode ─────────────────────────────────────────────────────────
    fun showDocumentSheet()                { _uiState.update { it.copy(showDocumentSheet = true) } }
    fun dismissDocumentSheet()             { _uiState.update { it.copy(showDocumentSheet = false) } }
    fun setDocumentContext(text: String?)  { _uiState.update { it.copy(documentContext = text?.ifBlank { null }, showDocumentSheet = false) } }
    fun clearDocumentContext()             { _uiState.update { it.copy(documentContext = null) } }

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

        sessionId = null
        _uiState.update {
            it.copy(
                messages       = emptyList(),
                streamingId    = null,
                inputText      = "",
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
                    id      = e.messageId,
                    role    = if (e.role == MessageRole.USER) Role.USER else Role.AI,
                    content = e.content
                )
            }
            sessionId = session.sessionId
            _uiState.update {
                it.copy(
                    messages       = messages,
                    streamingId    = null,
                    inputText      = "",
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
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        val isFirst = _uiState.value.messages.isEmpty()
        val userMsg = Message(role = Role.USER, content = text)

        _uiState.update {
            it.copy(
                messages       = it.messages + userMsg,
                inputText      = "",
                isGenerating   = true,
                retryMessageId = null
            )
        }

        val contextSnapshot = _uiState.value.messages
        streamingJob = viewModelScope.launch {
            streamAndPersist(
                sessionId = getOrCreateSession(),
                userText  = text,
                isFirst   = isFirst,
                context   = contextSnapshot
            )
        }
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

        _uiState.update { it.copy(messages = cleaned, isGenerating = true, retryMessageId = null) }
        streamingJob = viewModelScope.launch {
            streamAndPersist(sessionId = sid, userText = lastUserText, isFirst = false, context = cleaned)
        }
    }

    // ── Core streaming ────────────────────────────────────────────────────────

    private suspend fun streamAndPersist(
        sessionId: String,
        userText : String,
        isFirst  : Boolean,
        context  : List<Message>
    ) {
        val assistantId = UUID.randomUUID().toString()
        _streamingContent.value = ""
        _uiState.update { it.copy(streamingId = assistantId) }

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

            val finalMsg = Message(id = assistantId, role = Role.AI, content = finalText)
            _streamingContent.value = ""
            _uiState.update { s ->
                s.copy(
                    messages       = if (finalText.isNotBlank()) s.messages + finalMsg else s.messages,
                    streamingId    = null,
                    isGenerating   = false,
                    retryMessageId = if (isError && finalText.isNotBlank()) assistantId else null
                )
            }

            if (isFirst && !isError) launchTitleGeneration(sessionId, userText)
        }
    }

    // ── Title generation (heuristic - instant, no AI cost) ───────────────────

    private fun launchTitleGeneration(sessionId: String, firstMessage: String) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch {
            val title = firstMessage.trim().let {
                if (it.length <= 36) it else it.take(33) + "..."
            }
            historyRepo.updateTitle(sessionId, title)
            _uiState.update { it.copy(sessionTitle = title) }
        }
    }

    private suspend fun getOrCreateSession() =
        sessionId ?: historyRepo.createSession().sessionId.also { sessionId = it }

    /**
     * Trims history by estimated tokens, not just message count: one giant
     * pasted message would otherwise blow up prefill time (and can overflow
     * the model context). Walks backwards from the newest message; the
     * current question is always included.
     */
    private fun buildTokenBoundedHistory(
        context   : List<Message>,
        windowSize: Int
    ): List<Pair<Boolean, String>> {
        val result = ArrayDeque<Pair<Boolean, String>>()
        var usedChars = 0
        for (msg in context.asReversed()) {
            if (result.size >= windowSize) break
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
