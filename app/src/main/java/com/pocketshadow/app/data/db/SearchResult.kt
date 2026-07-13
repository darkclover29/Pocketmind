package com.pocketshadow.app.data.db

/**
 * Lightweight row returned by full-text search across chat_messages.
 * Carries just enough to render a result row in the drawer; the full
 * message list is loaded only when the user taps a result.
 */
data class SearchResult(
    val messageId : String,
    val sessionId : String,
    val sessionTitle : String,
    val content   : String,
    val role      : String,   // "USER" or "AI"
    val timestamp : Long
)
