package com.pocketmind.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.repository.ChatHistoryRepository
import com.pocketmind.data.repository.LlmRepository
import com.pocketmind.ui.components.Message
import com.pocketmind.ui.components.Role
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.pocketmind.data.repository.DownloadState
import java.util.UUID
import javax.inject.Inject

// ── Engine type ───────────────────────────────────────────────────────────────

enum class InferenceEngine(val label: String) {
    AICORE("⚡ AICore Active"),
    LOCAL_MEDIAPIPE("🔷 Local Model Active")
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class ChatUiState(
    val messages    : List<Message>     = emptyList(),
    val inputText   : String            = "",
    val isGenerating: Boolean           = false,
    val activeEngine: InferenceEngine   = InferenceEngine.AICORE,
    val sessionTitle: String            = "New Chat",
    val downloadState: DownloadState    = DownloadState.Idle
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val llmRepo    : LlmRepository,
    private val historyRepo: ChatHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            llmRepo.downloadState.collect { dState ->
                _uiState.update { it.copy(downloadState = dState) }
            }
        }
    }

    // Current session — created lazily on first send
    private var sessionId: String? = null

    // Primary streaming job — cancel this on Stop
    private var streamingJob: Job? = null

    // Title generation job — completely independent of streamingJob
    private var titleJob: Job? = null

    // ── Input ─────────────────────────────────────────────────────────────────

    fun onInputChanged(text: String) = _uiState.update { it.copy(inputText = text) }

    fun toggleEngine() = _uiState.update {
        it.copy(
            activeEngine = when (it.activeEngine) {
                InferenceEngine.AICORE         -> InferenceEngine.LOCAL_MEDIAPIPE
                InferenceEngine.LOCAL_MEDIAPIPE -> InferenceEngine.AICORE
            }
        )
    }

    fun selectEngine(engine: InferenceEngine) = _uiState.update {
        it.copy(activeEngine = engine)
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isBlank() || _uiState.value.isGenerating) return

        val isFirstMessage = _uiState.value.messages.isEmpty()

        // Append the user bubble immediately (in-memory only)
        val userMsg = Message(role = Role.USER, content = text)
        _uiState.update {
            it.copy(
                messages     = it.messages + userMsg,
                inputText    = "",
                isGenerating = true
            )
        }

        // ── ① Primary: stream AI response ────────────────────────────────────
        streamingJob = viewModelScope.launch {
            streamAndPersist(
                sessionId   = getOrCreateSession(),
                userText    = text,
                isFirstMsg  = isFirstMessage
            )
        }
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    fun stopGeneration() {
        streamingJob?.cancel()
        // Persist whatever was accumulated before the user hit Stop
        val partial = _uiState.value.messages
            .lastOrNull { it.role == Role.AI && it.isStreaming }
        if (partial != null) {
            viewModelScope.launch {
                val sid   = sessionId ?: return@launch
                val prior = _uiState.value.messages
                    .lastOrNull { it.role == Role.USER }?.content ?: ""
                historyRepo.saveCompleteInteraction(sid, prior, partial.content)
            }
        }
        finaliseStreamingBubble()
    }

    // ── Core streaming logic ──────────────────────────────────────────────────

    private suspend fun streamAndPersist(
        sessionId  : String,
        userText   : String,
        isFirstMsg : Boolean
    ) {
        val assistantId  = UUID.randomUUID().toString()
        val placeholder  = Message(id = assistantId, role = Role.AI, content = "", isStreaming = true)
        _uiState.update { it.copy(messages = it.messages + placeholder) }

        val buffer = StringBuilder()

        try {
            val engine = _uiState.value.activeEngine
            llmRepo.streamResponse(userText, engine).collect { token ->
                buffer.append(token)
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == assistantId) msg.copy(content = buffer.toString()) else msg
                        }
                    )
                }
            }
        } finally {
            // ✅ Streaming complete — write ONCE to Room (avoids thrash)
            val finalText = buffer.toString()
            if (finalText.isNotBlank()) {
                historyRepo.saveCompleteInteraction(sessionId, userText, finalText)
            }
            finaliseStreamingBubble()

            // ── ② Independent: generate title (only for brand-new sessions) ──
            if (isFirstMsg) {
                launchTitleGeneration(sessionId = sessionId, firstMessage = userText)
            }
        }
    }

    // ── Title generation (isolated coroutine) ─────────────────────────────────
    //
    // ✅ Concurrency separation: titleJob is completely independent of streamingJob.
    // ✅ Error isolation:        any exception is caught silently with a fallback.

    private fun launchTitleGeneration(sessionId: String, firstMessage: String) {
        titleJob?.cancel()
        titleJob = viewModelScope.launch(Dispatchers.Default) {
            val title = try {
                llmRepo.generateTitle(firstMessage)
                    ?: firstMessage.take(25) + "…"
            } catch (e: Exception) {
                firstMessage.take(25) + "…"   // silent fallback — never crashes chat
            }

            historyRepo.updateTitle(sessionId, title)
            _uiState.update { it.copy(sessionTitle = title) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private suspend fun getOrCreateSession(): String {
        return sessionId ?: historyRepo.createSession().sessionId.also { sessionId = it }
    }

    private fun finaliseStreamingBubble() {
        _uiState.update { state ->
            state.copy(
                messages     = state.messages.map { if (it.isStreaming) it.copy(isStreaming = false) else it },
                isGenerating = false
            )
        }
    }
}
