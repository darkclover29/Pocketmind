package com.pocketshadow.app.data.repository

import android.content.Context
import com.pocketshadow.app.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class FontScale { SMALL, NORMAL, LARGE }


/**
 * All user-configurable app preferences backed by SharedPreferences.
 * Each setting is exposed as a [StateFlow] so collectors react instantly.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Selected Model (file path) ────────────────────────────────────────────

    private val _selectedModelPath = MutableStateFlow(
        prefs.getString(KEY_SELECTED_MODEL_PATH, "") ?: ""
    )
    val selectedModelPath: StateFlow<String> = _selectedModelPath.asStateFlow()

    fun setSelectedModelPath(path: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL_PATH, path).apply()
        _selectedModelPath.value = path
    }


    // ── Theme ─────────────────────────────────────────────────────────────────

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.DARK.name)!!) }.getOrDefault(ThemeMode.DARK)
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
    }

    // ── Font scale ────────────────────────────────────────────────────────────

    private val _fontSize = MutableStateFlow(
        FontScale.valueOf(prefs.getString(KEY_FONT_SCALE, FontScale.NORMAL.name)!!)
    )
    val fontSize: StateFlow<FontScale> = _fontSize.asStateFlow()

    fun setFontSize(scale: FontScale) {
        prefs.edit().putString(KEY_FONT_SCALE, scale.name).apply()
        _fontSize.value = scale
    }

    // ── Haptic feedback ───────────────────────────────────────────────────────

    private val _hapticFeedback = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC, true))
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
        _hapticFeedback.value = enabled
    }

    // ── Sound effects ────────────────────────────────────────────────────────

    private val _soundEffects = MutableStateFlow(prefs.getBoolean(KEY_SOUND_EFFECTS, true))
    val soundEffects: StateFlow<Boolean> = _soundEffects.asStateFlow()

    fun setSoundEffects(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SOUND_EFFECTS, enabled).apply()
        _soundEffects.value = enabled
    }

    // ── Context window size (how many past messages the AI sees) ──────────────

    private val _contextWindowSize = MutableStateFlow(prefs.getInt(KEY_CONTEXT_SIZE, 10))
    val contextWindowSize: StateFlow<Int> = _contextWindowSize.asStateFlow()

    fun setContextWindowSize(size: Int) {
        prefs.edit().putInt(KEY_CONTEXT_SIZE, size).apply()
        _contextWindowSize.value = size
    }

    // ── Save history ──────────────────────────────────────────────────────────

    private val _saveHistory = MutableStateFlow(prefs.getBoolean(KEY_SAVE_HISTORY, true))
    val saveHistory: StateFlow<Boolean> = _saveHistory.asStateFlow()

    fun setSaveHistory(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_HISTORY, enabled).apply()
        _saveHistory.value = enabled
    }

    // ── Auto scroll ───────────────────────────────────────────────────────────

    private val _autoScroll = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SCROLL, true))
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    fun setAutoScroll(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SCROLL, enabled).apply()
        _autoScroll.value = enabled
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    // ── Onboarding (shown once on first launch) ───────────────────────────────

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDING_DONE, false)
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _hasCompletedOnboarding.value = true
    }

    // ── Custom system prompt ──────────────────────────────────────────────────
    // When non-blank, replaces the default PocketShadow persona in the LLM prompt.

    private val _customSystemPrompt = MutableStateFlow(
        prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
    )
    val customSystemPrompt: StateFlow<String> = _customSystemPrompt.asStateFlow()

    fun setCustomSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply()
        _customSystemPrompt.value = prompt
    }

    // ── TTS voice settings ────────────────────────────────────────────────────
    // Persisted so the user's accent / speed / pitch survive app restarts.

    private val _ttsVoiceName = MutableStateFlow(prefs.getString(KEY_TTS_VOICE, "") ?: "")
    val ttsVoiceName: StateFlow<String> = _ttsVoiceName.asStateFlow()
    fun setTtsVoiceName(name: String) {
        prefs.edit().putString(KEY_TTS_VOICE, name).apply()
        _ttsVoiceName.value = name
    }

    private val _ttsSpeechRate = MutableStateFlow(prefs.getFloat(KEY_TTS_RATE, 1.0f))
    val ttsSpeechRate: StateFlow<Float> = _ttsSpeechRate.asStateFlow()
    fun setTtsSpeechRate(rate: Float) {
        prefs.edit().putFloat(KEY_TTS_RATE, rate).apply()
        _ttsSpeechRate.value = rate
    }

    private val _ttsPitch = MutableStateFlow(prefs.getFloat(KEY_TTS_PITCH, 1.0f))
    val ttsPitch: StateFlow<Float> = _ttsPitch.asStateFlow()
    fun setTtsPitch(pitch: Float) {
        prefs.edit().putFloat(KEY_TTS_PITCH, pitch).apply()
        _ttsPitch.value = pitch
    }

    private val _ttsAccent = MutableStateFlow(prefs.getString(KEY_TTS_ACCENT, "") ?: "")
    val ttsAccent: StateFlow<String> = _ttsAccent.asStateFlow()
    fun setTtsAccent(accent: String) {
        prefs.edit().putString(KEY_TTS_ACCENT, accent).apply()
        _ttsAccent.value = accent
    }

    // ── Conversation count + In-App Review trigger ────────────────────────────
    // Emits a Unit after specific conversation milestones so MainActivity can
    // launch the Google Play review dialog at the right moment.

    private val _reviewTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewTrigger: SharedFlow<Unit> = _reviewTrigger.asSharedFlow()

    fun incrementConversationCount() {
        val count = prefs.getInt(KEY_CONV_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CONV_COUNT, count).apply()
        // Prompt at 5, 15, 30 — diminishing cadence; Play Store enforces its own rate limit
        if (count == 5 || count == 15 || count == 30) {
            _reviewTrigger.tryEmit(Unit)
        }
    }

    /** Requests the Play in-app review dialog from an explicit user action. */
    fun requestReview() {
        _reviewTrigger.tryEmit(Unit)
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME          = "pocketshadow_settings"
        private const val KEY_THEME           = "theme_mode"
        private const val KEY_FONT_SCALE      = "font_scale"
        private const val KEY_HAPTIC          = "haptic_feedback"
        private const val KEY_SOUND_EFFECTS   = "sound_effects"
        private const val KEY_CONTEXT_SIZE    = "context_window_size"
        private const val KEY_SAVE_HISTORY    = "save_history"
        private const val KEY_AUTO_SCROLL     = "auto_scroll"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_CUSTOM_PROMPT   = "custom_system_prompt"
        private const val KEY_CONV_COUNT      = "completed_conversation_count"
        private const val KEY_TTS_VOICE       = "tts_voice_name"
        private const val KEY_TTS_RATE        = "tts_speech_rate"
        private const val KEY_TTS_PITCH       = "tts_pitch"
        private const val KEY_TTS_ACCENT      = "tts_accent"
        private const val KEY_SELECTED_MODEL_PATH = "selected_model_path"
    }
}
