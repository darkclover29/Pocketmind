package com.pocketmind.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LlmRepository"

@Singleton
class LlmRepository @Inject constructor(
    // Inject your actual engine here:
    // private val aiCoreEngine   : AiCoreSession,
    // private val mediaPipeEngine: LlmInference,
) {

    // ── Streaming chat (token-by-token) ───────────────────────────────────────
    //
    // Replace the simulated loop with your real engine callback.
    // Pattern is identical for both AICore and MediaPipe:
    //   - AICore: session.generateResponseAsync(prompt) { token, done -> emit(token) }
    //   - MediaPipe: llmInference.generateResponseAsync(prompt, resultListener)

    fun streamResponse(prompt: String): Flow<String> = flow {
        // ── REPLACE THIS BLOCK ───────────────────────────────────────────────
        val mockTokens = "[MOCK] This is a *streaming* response for: **$prompt**"
            .split(" ")
        for (token in mockTokens) {
            kotlinx.coroutines.delay(40)
            emit("$token ")
        }
        // ── END REPLACE ───────────────────────────────────────────────────────
    }

    // ── Single-shot title generation ──────────────────────────────────────────
    //
    // Intentionally NOT a Flow — we want one complete string back, not a stream.
    // Called on Dispatchers.Default from the ViewModel so it never blocks the UI
    // or the primary streaming coroutine.

    suspend fun generateTitle(firstMessage: String): String? =
        withContext(Dispatchers.Default) {
            try {
                val systemPrompt = buildTitlePrompt(firstMessage)

                // ── REPLACE THIS BLOCK ────────────────────────────────────────
                // val rawTitle = aiCoreEngine.generateResponse(systemPrompt)  // AICore
                // val rawTitle = mediaPipeEngine.generateResponse(systemPrompt) // MediaPipe
                val rawTitle = "[MOCK] ${firstMessage.take(20)} Overview"  // remove in prod
                // ── END REPLACE ───────────────────────────────────────────────

                sanitizeTitle(rawTitle)
            } catch (e: Exception) {
                // ✅ Error isolation: title failure must NEVER crash the chat
                Log.w(TAG, "Title generation failed: ${e.message}")
                null
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildTitlePrompt(userFirstMessage: String) = """
You are a precise text-summarization utility.
Task: Analyze the user's input and generate a short, concise conversation title based on it.

Rules:
1. The title must be between 2 to 5 words maximum.
2. Do NOT use quotation marks, punctuation, or markdown formatting.
3. Do NOT include filler words like "Title:", "Here is your title:", or greetings.
4. Output ONLY the raw title text.

User Input: "$userFirstMessage"
Output:
""".trimIndent()

    /**
     * Sanitize the raw LLM output before writing to the database.
     * Smaller models (Llama 3.2 1B etc.) often add stray quotes/periods
     * even when instructed not to.
     */
    internal fun sanitizeTitle(raw: String): String =
        raw
            .trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trimEnd('.', ':', ',', ';')
            .take(30)           // hard cap so it always fits the sidebar
            .ifBlank { null }
            ?: raw.take(25) + "…"
}
