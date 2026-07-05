package com.pocketmind.data.repository

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG         = "LlmRepository"
private const val MAX_TOKENS  = 1024
private const val TEMPERATURE = 0.7f
private const val TOP_K       = 40
private const val TOP_P       = 0.95

// ── Discovered model file (for the model picker UI) ───────────────────────────

data class ModelFile(
    val path       : String,
    val filename   : String,
    val displayName: String,
    val sizeMb     : Float
)

// ── Model info (exposed to UI) ────────────────────────────────────────────────

data class ModelInfo(
    val statusText  : String  = "Not initialized",
    val isReady     : Boolean = false,
    val backend     : String  = "–",
    val path        : String  = "Not found",
    val displayName : String  = "–",
    val fileSizeMb  : Float   = 0f,
    val maxTokens   : Int     = MAX_TOKENS,
    val temperature : Float   = TEMPERATURE,
    val topK        : Int     = TOP_K
)

// ── Repository ────────────────────────────────────────────────────────────────

@Singleton
class LlmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepo: SettingsRepository
) {
    private val engineMutex = Mutex()
    private var engine: Engine? = null

    // Persistent conversation: reused across turns so the model does NOT
    // re-prefill the whole history on every message (prefill dominates latency).
    // Rebuilt only when the session / system prompt / document context changes.
    private var conversation: Conversation? = null
    private var conversationKey: String? = null

    private val _modelInfo = MutableStateFlow(ModelInfo())
    val modelInfo: StateFlow<ModelInfo> = _modelInfo.asStateFlow()

    private val _availableModels = MutableStateFlow<List<ModelFile>>(emptyList())
    val availableModels: StateFlow<List<ModelFile>> = _availableModels.asStateFlow()

    // ── Model discovery ───────────────────────────────────────────────────────

    /** Scans known directories for .litertlm and .bin model files (disk I/O → IO dispatcher). */
    suspend fun scanAvailableModels() = withContext(Dispatchers.IO) {
        val dirs = listOfNotNull(
            File("/data/local/tmp/llm/"),
            File(context.filesDir, "llm/"),
            context.getExternalFilesDir(null)?.let { File(it, "llm/") }
        )
        val extensions = setOf("bin", "litertlm", "tflite", "task")
        val found = mutableListOf<ModelFile>()
        for (dir in dirs) {
            if (!dir.exists()) continue
            dir.listFiles()
                ?.filter { it.isFile && it.extension in extensions && it.canRead() }
                ?.forEach { file ->
                    found.add(
                        ModelFile(
                            path        = file.absolutePath,
                            filename    = file.name,
                            displayName = resolveDisplayName(file.name),
                            sizeMb      = file.length().toFloat() / (1024 * 1024)
                        )
                    )
                }
        }
        _availableModels.value = found.distinctBy { it.filename }
    }

    private fun resolveDisplayName(filename: String): String = when {
        filename.contains("gemma-4-E4B", ignoreCase = true) ||
        filename.contains("gemma4-e4b",  ignoreCase = true)  -> "Gemma 4 E4B"
        filename.contains("gemma-4-E2B", ignoreCase = true)  -> "Gemma 4 E2B"
        filename.contains("gemma-3n",    ignoreCase = true) &&
        filename.contains("E4B",         ignoreCase = true)  -> "Gemma 3n E4B"
        filename.contains("gemma-3n",    ignoreCase = true) &&
        filename.contains("E2B",         ignoreCase = true)  -> "Gemma 3n E2B"
        filename.contains("gemma3-4b",   ignoreCase = true) ||
        filename.contains("gemma-3-4b",  ignoreCase = true)  -> "Gemma 3 4B"
        filename.contains("gemma3-1b",   ignoreCase = true) ||
        filename.contains("gemma-3-1b",  ignoreCase = true)  -> "Gemma 3 1B"
        filename.contains("Llama-3.2-3B",ignoreCase = true) ||
        filename.contains("llama-3.2-3b",ignoreCase = true)  -> "Llama 3.2 3B"
        filename.contains("Llama-3.2-1B",ignoreCase = true) ||
        filename.contains("llama-3.2-1b",ignoreCase = true)  -> "Llama 3.2 1B"
        filename.contains("phi-4",       ignoreCase = true)  -> "Phi-4 Mini"
        filename.contains("qwen",        ignoreCase = true)  -> "Qwen 2.5 0.5B"
        filename.contains("deepseek",    ignoreCase = true)  -> "DeepSeek R1"
        filename == "model.bin"                               -> "Gemma 2B (Default)"
        filename.contains("gemma-2b",    ignoreCase = true) ||
        filename.contains("gemma2b",     ignoreCase = true)  -> "Gemma 2B"
        else -> filename
            .removeSuffix(".litertlm")
            .removeSuffix(".bin")
            .removeSuffix(".tflite")
            .removeSuffix(".task")
            .replace("-", " ")
            .replace("_", " ")
            .trim()
    }

    // ── Model path resolution ─────────────────────────────────────────────────

    private fun resolveModelPath(): String? {
        // 1. User-selected model takes priority
        val selected = settingsRepo.selectedModelPath.value
        if (selected.isNotBlank() && File(selected).let { it.exists() && it.canRead() }) {
            return selected
        }
        // 2. Fall back to known default locations (Gemma 3 1B prioritized, .litertlm preferred over .bin)
        return listOfNotNull(
            "/data/local/tmp/llm/gemma3-1b-it-int4.litertlm",
            "/data/local/tmp/llm/gemma-3-1b-it-int4.litertlm",
            "${context.filesDir.absolutePath}/llm/gemma3-1b-it-int4.litertlm",
            "${context.filesDir.absolutePath}/llm/gemma-3-1b-it-int4.litertlm",
            context.getExternalFilesDir(null)?.let { "${it.absolutePath}/llm/gemma3-1b-it-int4.litertlm" },
            context.getExternalFilesDir(null)?.let { "${it.absolutePath}/llm/gemma-3-1b-it-int4.litertlm" },
            "/data/local/tmp/llm/gemma-4-E4B-it.litertlm",
            "/data/local/tmp/llm/model.litertlm",
            "/data/local/tmp/llm/model.bin",
            "${context.filesDir.absolutePath}/llm/model.litertlm",
            "${context.filesDir.absolutePath}/llm/model.bin",
            context.getExternalFilesDir(null)?.let { "${it.absolutePath}/llm/model.litertlm" },
            context.getExternalFilesDir(null)?.let { "${it.absolutePath}/llm/model.bin" }
        ).firstOrNull { path -> File(path).let { it.exists() && it.canRead() } }
    }

    // ── Engine loading ────────────────────────────────────────────────────────

    @OptIn(ExperimentalApi::class)
    private suspend fun getOrCreateEngine(): Engine? {
        engine?.let { return it }
        return engineMutex.withLock {
            engine?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                // Multi-Token Prediction (speculative decoding): up to ~3x faster
                // decode on GPU for supported models (e.g. Gemma 4); ignored otherwise.
                runCatching { ExperimentalFlags.enableSpeculativeDecoding = true }
                val path = resolveModelPath()
                if (path == null) {
                    _modelInfo.value = ModelInfo(
                        statusText = "Model not found. Push a .litertlm file to /data/local/tmp/llm/"
                    )
                    return@withContext null
                }

                val filename    = File(path).name
                val displayName = resolveDisplayName(filename)
                val fileMb      = File(path).length().toFloat() / (1024 * 1024)
                _modelInfo.value = ModelInfo(statusText = "Loading $displayName…", path = path, displayName = displayName)

                val backends = listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
                for ((backendName, backend) in backends) {
                    try {
                        val config = EngineConfig(
                            modelPath = path,
                            backend   = backend,
                            cacheDir  = context.cacheDir.path
                        )
                        val e = Engine(config)
                        e.initialize()
                        engine = e
                        _modelInfo.value = ModelInfo(
                            statusText  = "Ready",
                            isReady     = true,
                            backend     = backendName,
                            path        = path,
                            displayName = displayName,
                            fileSizeMb  = fileMb,
                            maxTokens   = MAX_TOKENS,
                            temperature = TEMPERATURE,
                            topK        = TOP_K
                        )
                        Log.i(TAG, "Engine ready on $backendName · $displayName ($path, ${fileMb.toInt()} MB)")
                        return@withContext e
                    } catch (e: Exception) {
                        Log.w(TAG, "$backendName failed: ${e.message}")
                    }
                }
                _modelInfo.value = ModelInfo(
                    statusText  = "Failed to load model — check Logcat",
                    path        = path,
                    displayName = displayName,
                    fileSizeMb  = fileMb
                )
                null
            }
        }
    }

    /**
     * Loads the engine ahead of time so the first message doesn't pay the
     * multi-second model-init cost. Safe to call from any coroutine; work
     * happens on Dispatchers.IO inside getOrCreateEngine().
     */
    suspend fun warmUp() {
        getOrCreateEngine()
    }

    private fun invalidateConversation() {
        runCatching { conversation?.close() }
        conversation = null
        conversationKey = null
    }

    /** Closes the engine so the next call reloads with the newly selected model. */
    fun resetModel() {
        invalidateConversation()
        runCatching { engine?.close() }
        engine = null
        _modelInfo.value = ModelInfo()
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    /**
     * @param sessionKey           Stable ID of the chat session (drives conversation reuse).
     * @param conversationHistory  (isUser, content) pairs for the current session.
     * @param documentContext      Optional pasted document text for Document Mode.
     */
    fun streamResponse(
        sessionKey         : String,
        conversationHistory: List<Pair<Boolean, String>>,
        documentContext    : String? = null
    ): Flow<String> = flow {
        val eng = getOrCreateEngine() ?: run {
            emit("⚠️ The AI model couldn't be loaded. Please restart the app — if the problem persists, free up storage space and try again.")
            return@flow
        }

        val customPrompt = settingsRepo.customSystemPrompt.value.trim()
        val systemPrompt = if (customPrompt.isNotBlank()) customPrompt else DEFAULT_SYSTEM_PROMPT

        val currentQuestion = conversationHistory.lastOrNull()?.second ?: return@flow

        // Conversation identity: same session + same system prompt + same doc →
        // reuse the live conversation (KV cache retained, no history re-prefill).
        val key = "$sessionKey|${systemPrompt.hashCode()}|${documentContext?.hashCode() ?: 0}"

        val conv = engineMutex.withLock {
            val existing = conversation
            if (existing != null && conversationKey == key) {
                existing
            } else {
                invalidateConversation()

                // Rebuild from history (only needed on session switch / config change)
                val initialMessages = mutableListOf<Message>()

                if (!documentContext.isNullOrBlank()) {
                    val truncated = documentContext.take(6000)
                    initialMessages += Message.user(
                        "I'm sharing a reference document. Use it to answer my questions accurately.\n\n" +
                        "=== DOCUMENT START ===\n$truncated" +
                        (if (documentContext.length > 6000) "\n[Document truncated for context limit]" else "") +
                        "\n=== DOCUMENT END ==="
                    )
                    initialMessages += Message.model("I've read the document. Ask me anything about it.")
                }

                val pastTurns = if (conversationHistory.size > 1) conversationHistory.dropLast(1) else emptyList()
                pastTurns.forEach { (isUser, content) ->
                    if (isUser) initialMessages += Message.user(content)
                    else        initialMessages += Message.model(content)
                }

                // LiteRT-LM handles all chat templating — no manual <start_of_turn> tags needed.
                val conversationConfig = ConversationConfig(
                    systemInstruction = Contents.of(systemPrompt),
                    initialMessages   = initialMessages,
                    samplerConfig     = SamplerConfig(
                        topK        = TOP_K,
                        topP        = TOP_P,
                        temperature = TEMPERATURE.toDouble()
                    )
                )

                eng.createConversation(conversationConfig).also {
                    conversation    = it
                    conversationKey = key
                }
            }
        }

        try {
            var lastText = ""
            conv.sendMessageAsync(currentQuestion)
                .collect { message ->
                    // Works whether the flow emits deltas or full accumulated text
                    val fullText = message.toString()
                    val delta    = fullText.removePrefix(lastText)
                    lastText     = fullText
                    if (delta.isNotEmpty()) emit(delta)
                }
        } catch (t: Throwable) {
            // Cancelled (Stop) or failed mid-generation: the native KV cache no
            // longer matches our saved history, so rebuild on the next turn.
            invalidateConversation()
            throw t
        }
    }

    // ── System prompt ─────────────────────────────────────────────────────────

    companion object {
        private val DEFAULT_SYSTEM_PROMPT = """
You are PocketMind, a helpful on-device AI assistant. Follow these rules strictly:

1. ANSWER FROM KNOWLEDGE: You have extensive training data covering history, science, geography, math, language, culture, and general knowledge. Use it. Never refuse to answer a factual question by claiming you need the internet.
2. INTERNET EXCUSE IS BANNED: Never say "I don't have internet access", "I can't browse the web", or "I lack real-time access" as a reason for not answering. If you know the answer, say it. If you genuinely don't know, say "I'm not sure" — not "I can't access the internet."
3. REAL-TIME EXCEPTION: Only say you lack real-time data for things that genuinely change by the minute — stock prices, live sports scores, breaking news. Capitals of countries, historical facts, scientific constants, definitions — these never need the internet.
4. HONESTY: If you are not sure of something, say "I'm not sure." Never invent facts.
5. FOCUS: Answer only what was just asked. Be direct and concise.
6. HINGLISH SUPPORT: You can understand Hindi, English, and Hinglish (Hindi written in the Latin script mixed with English). If the user asks or communicates in Hinglish, you MUST reply naturally in Hinglish.
        """.trimIndent()
    }
}
