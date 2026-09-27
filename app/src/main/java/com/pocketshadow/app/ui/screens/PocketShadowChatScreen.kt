package com.pocketshadow.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import com.pocketshadow.app.BuildConfig
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketshadow.app.data.db.entity.ChatSessionEntity
import com.pocketshadow.app.data.repository.FontScale
import com.pocketshadow.app.data.repository.ModelFile
import com.pocketshadow.app.data.repository.ModelInfo
import com.pocketshadow.app.ui.components.*
import com.pocketshadow.app.ui.theme.*
import com.pocketshadow.app.ui.viewmodel.ChatViewModel
import com.pocketshadow.app.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ── Voice option (UI representation of a TTS Voice) ───────────────────────────

data class VoiceOption(
    val name       : String,  // TTS engine voice name — blank = system default
    val label      : String   // Human-readable label shown in the picker
)

/** Converts the engine's Voice set into picker-friendly options (English only). */
private fun Set<Voice>.toVoiceOptions(): List<VoiceOption> {
    val best = this
        .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
        .groupBy { it.locale.country }
        .mapValues { (_, voices) -> voices.maxByOrNull { it.quality }!! }
        .values
        .sortedWith(compareByDescending<Voice> { it.quality }
            .thenBy { it.locale.displayCountry })
    return listOf(VoiceOption("", "On-device default")) + best.map { v ->
        val country = v.locale.displayCountry.let { if (it.isNotBlank()) " ($it)" else "" }
        VoiceOption(name = v.name, label = "English$country")
    }
}

/**
 * Strips markdown syntax AND emoji/symbols so TTS reads clean prose.
 * Handles surrogate-pair emoji (😊), BMP symbols (✅ ⚠️ ❌), variation
 * selectors, and ZWJ sequences — nothing aloud that isn't actual words.
 */
private fun String.stripForTts(): String = this
    // ── markdown ──────────────────────────────────────────────────────────────
    .replace(Regex("```[\\w]*\\n?"), "")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
    .replace(Regex("^[\\-\\*\\+]\\s+", RegexOption.MULTILINE), "")
    .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
    // ── remove all emojis, modifiers, technical symbols, and dingbats ───────────
    .replace(Regex("[\\p{So}\\p{Sk}]"), "")
    // ── clean up whitespace left by removals ──────────────────────────────────
    .replace(Regex("[ \\t]{2,}"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketShadowChatScreen(
    viewModel     : ChatViewModel = viewModel(),
    mainViewModel : MainViewModel = viewModel()
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val newChatRequested by mainViewModel.newChatRequested.collectAsStateWithLifecycle()

    // Handle "New Chat" launcher shortcut — triggers newChat() then clears the flag
    LaunchedEffect(newChatRequested) {
        if (newChatRequested) {
            viewModel.newChat()
            mainViewModel.consumeNewChatRequest()
        }
    }

    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context     = LocalContext.current
    val wideLayout  = LocalConfiguration.current.screenWidthDp >= 840
    val haptic      = LocalHapticFeedback.current
    var showModelPickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var pendingExportPassphrase by remember { mutableStateOf("") }
    var pendingImportUri by remember { mutableStateOf<String?>(null) }
    var selectedTemplate by remember { mutableStateOf<PromptTemplate?>(null) }
    val soundEffects = remember { PocketShadowSoundEffects() }
    DisposableEffect(Unit) {
        onDispose { soundEffects.close() }
    }

    val exportArchiveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val passphrase = pendingExportPassphrase
            pendingExportPassphrase = ""
            scope.launch {
                viewModel.exportEncryptedArchive(passphrase).onSuccess { bytes ->
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                            ?: error("Could not open the selected file.")
                    }.onSuccess {
                        viewModel.setArchiveStatus("Encrypted chat archive saved.")
                    }.onFailure {
                        viewModel.setArchiveStatus(it.message ?: "Export failed while writing the file.")
                    }
                }
            }
        } else {
            pendingExportPassphrase = ""
        }
    }

    val importArchiveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            showImportDialog = true
            pendingImportUri = uri.toString()
        }
    }

    // Let Android choose vibration strength and respect the device's haptic setting.
    val view = androidx.compose.ui.platform.LocalView.current
    fun performPocketHaptic(constant: Int, previewWhenEnabling: Boolean = false) {
        if (uiState.hapticFeedback || previewWhenEnabling) view.performHapticFeedback(constant)
    }
    val confirmHaptic: () -> Unit = {
        if (uiState.soundEffects) soundEffects.send()
        performPocketHaptic(
            if (android.os.Build.VERSION.SDK_INT >= 30) android.view.HapticFeedbackConstants.CONFIRM
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    var generationWasActive by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isGenerating) {
        if (generationWasActive && !uiState.isGenerating && uiState.messages.lastOrNull()?.role == Role.AI) {
            if (uiState.soundEffects) soundEffects.complete()
        }
        generationWasActive = uiState.isGenerating
    }
    val tickHaptic: () -> Unit = {
        performPocketHaptic(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
    }

    // Composer focus — prompt cards and "Edit message" put text in the input;
    // focusing + showing the IME saves the extra tap on the field.
    val inputFocusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboardController  = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusComposer: () -> Unit = {
        runCatching { inputFocusRequester.requestFocus() }
        keyboardController?.show()
    }

    // ── Voice input (dictation) ───────────────────────────────────────────────
    // Partial results overwrite from a baseline snapshot of the composer, so
    // dictation appends to whatever was already typed instead of erasing it.
    var voiceBaseText by remember { mutableStateOf("") }
    val voiceInput = rememberVoiceInputState(
        onPartialResult = { partial ->
            viewModel.onInputChanged("$voiceBaseText $partial".trim())
        },
        onFinalResult = { final ->
            viewModel.onInputChanged("$voiceBaseText $final".trim())
            focusComposer()   // let the user review/edit, then send
        }
    )
    val startDictation: () -> Unit = {
        voiceBaseText = viewModel.inputText.value
        voiceInput.start()
    }
    val micPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startDictation() }
    val onMicClick: () -> Unit = {
        if (!voiceInput.isAvailable) {
            Toast.makeText(context, "On-device dictation is unavailable. Please type your message; cloud dictation is disabled.", Toast.LENGTH_LONG).show()
        } else if (voiceInput.isListening) {
            tickHaptic()
            voiceInput.stop()
        } else {
            tickHaptic()
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.RECORD_AUDIO
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (granted) startDictation()
            else micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(voiceInput.errorMessage) {
        voiceInput.errorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // ── Text-to-Speech ────────────────────────────────────────────────────────
    // Initialised once; voices populated after SUCCESS callback.
    val ttsReady             = remember { mutableStateOf(false) }
    val tts                  = remember { mutableStateOf<TextToSpeech?>(null) }
    val availableVoices      = remember { mutableStateOf<List<VoiceOption>>(emptyList()) }
    val isTtsSpeaking        = remember { mutableStateOf(false) }
    val previewPlayingAccent = remember { mutableStateOf<String?>(null) }
 
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady.value = true
                availableVoices.value = tts.value?.voices?.toVoiceOptions() ?: emptyList()
            }
        }
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isTtsSpeaking.value = true
            }
            override fun onDone(utteranceId: String?) {
                isTtsSpeaking.value = false
                if (utteranceId?.startsWith("preview_") == true) {
                    previewPlayingAccent.value = null
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                isTtsSpeaking.value = false
                if (utteranceId?.startsWith("preview_") == true) {
                    previewPlayingAccent.value = null
                }
            }
        })
        tts.value = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    // Re-apply voice/rate/pitch/accent whenever settings change (e.g. user picks new voice)
    LaunchedEffect(uiState.ttsVoiceName, uiState.ttsAccent, uiState.ttsSpeechRate, uiState.ttsPitch) {
        val engine = tts.value ?: return@LaunchedEffect
        engine.setSpeechRate(uiState.ttsSpeechRate)
        engine.setPitch(uiState.ttsPitch)
        
        // Apply Accent / Locale
        val locale = when (uiState.ttsAccent) {
            "US" -> Locale.US
            "UK" -> Locale.UK
            "IN" -> Locale("en", "IN")
            "AU" -> Locale("en", "AU")
            "CA" -> Locale.CANADA
            else -> Locale.getDefault()
        }
        try {
            engine.language = locale
        } catch (e: Exception) {
            android.util.Log.e("PocketShadow", "Error setting language/accent: ${e.message}")
        }

        if (uiState.ttsVoiceName.isBlank()) {
            engine.voice = engine.defaultVoice
        } else {
            engine.voices?.firstOrNull { it.name == uiState.ttsVoiceName }
                ?.let { engine.voice = it }
        }
    }

    // Even a system-default voice may require a network. Gate every speech call.
    fun selectOfflineVoice(engine: TextToSpeech): Boolean {
        val current = engine.voice
        val offline = current?.takeUnless { it.isNetworkConnectionRequired }
            ?: engine.voices?.filter { !it.isNetworkConnectionRequired &&
                it.locale.language == (current?.locale?.language ?: Locale.getDefault().language) }
                ?.maxByOrNull { it.quality }
        if (offline == null || engine.setVoice(offline) == TextToSpeech.ERROR) {
            Toast.makeText(context, "No on-device voice is available. Install an offline voice in device settings to read aloud.", Toast.LENGTH_LONG).show()
            return false
        }
        return true
    }

    // Strips markdown + emoji then speaks — no asterisks, no "fire emoji" aloud
    val readAloud: (String) -> Unit = { raw ->
        if (ttsReady.value && tts.value?.let { selectOfflineVoice(it) } == true) {
            isTtsSpeaking.value = true
            tts.value?.speak(
                raw.stripForTts(),
                TextToSpeech.QUEUE_FLUSH,
                null,
                "pm_${System.currentTimeMillis()}"
            )
        }
    }

    val stopSpeaking: () -> Unit = {
        if (ttsReady.value) {
            tts.value?.stop()
            isTtsSpeaking.value = false
        }
    }

    // Accent preview — speaks a short sample sentence in the chosen locale.
    // Passing an empty code (or the same code that's already playing) stops playback.
    // The correct locale is re-applied by the onDismiss handler when the dialog closes.
    val playPreview: (String) -> Unit = playPreview@{ code ->
        val engine = tts.value ?: return@playPreview
        if (previewPlayingAccent.value == code && code.isNotEmpty()) {
            // Tap again → stop
            engine.stop()
            previewPlayingAccent.value = null
        } else {
            engine.stop()
            previewPlayingAccent.value = null
            if (code.isNotEmpty()) {
                val locale = when (code) {
                    "US" -> java.util.Locale.US
                    "UK" -> java.util.Locale.UK
                    "IN" -> java.util.Locale("en", "IN")
                    "AU" -> java.util.Locale("en", "AU")
                    "CA" -> java.util.Locale.CANADA
                    else -> java.util.Locale.getDefault()
                }
                try { engine.language = locale } catch (_: Exception) {}
                if (!selectOfflineVoice(engine)) return@playPreview
                previewPlayingAccent.value = code
                engine.speak(
                    "Hi, I'm PocketShadow — your private, on-device assistant.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "preview_$code"
                )
                // Locale is restored when the dialog is dismissed via the existing onDismiss handler
            }
        }
    }

    val targetFontScale = when (uiState.fontSize) {
        FontScale.SMALL  -> 0.88f
        FontScale.NORMAL -> 1.00f
        FontScale.LARGE  -> 1.18f
    }
    val fontScale by animateFloatAsState(
        targetValue = targetFontScale,
        animationSpec = tween(durationMillis = 220),
        label = "app_text_size"
    )

    // Scroll when a new committed message arrives
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty() && uiState.autoScroll) {
            val target = if (uiState.isGenerating) uiState.messages.size
                         else                      uiState.messages.lastIndex
            listState.animateScrollToItem(target)
        }
    }

    // Per-token auto-scroll, throttled to once per 150 ms and ONLY while the
    // user is already at the bottom. If they scroll up to read, we stop
    // following entirely (no fighting their finger); the jump-to-bottom pill
    // brings them back. scrollBy (clamped) is used instead of scrollToItem,
    // which pins the item TOP and misbehaves on messages taller than the screen.
    LaunchedEffect(uiState.isGenerating, uiState.autoScroll) {
        if (!uiState.isGenerating || !uiState.autoScroll) return@LaunchedEffect
        var lastScrollMs = 0L
        viewModel.streamingContent.collect {
            val now = System.currentTimeMillis()
            if (now - lastScrollMs < 150L || listState.isScrollInProgress) return@collect

            val info        = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@collect
            val nearBottom  = lastVisible.index == info.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= info.viewportEndOffset + 160

            if (nearBottom) {
                lastScrollMs = now
                listState.scrollBy(100_000f)   // clamped — lands exactly at the end
            }
        }
    }

    PocketShadowTheme(themeMode = uiState.themeMode, fontScale = fontScale) {
        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                HistoryDrawer(
                    sessions        = uiState.sessions,
                    hapticEnabled   = uiState.hapticFeedback,
                    searchQuery     = uiState.searchQuery,
                    searchResults   = uiState.searchResults,
                    isSearching     = uiState.isSearching,
                    onSearchQuery   = viewModel::onSearchQueryChanged,
                    onSearchResult  = { result ->
                        // Load the session that contains the matched message
                        val session = uiState.sessions.firstOrNull { it.sessionId == result.sessionId }
                        if (session != null) {
                            viewModel.loadSession(session)
                            scope.launch { drawerState.close() }
                        }
                    },
                    onClearSearch   = viewModel::clearSearch,
                    onSessionClick  = { session ->
                        tickHaptic()
                        viewModel.loadSession(session)
                        scope.launch { drawerState.close() }
                    },
                    onSessionDelete = { viewModel.deleteSession(it.sessionId) },
                    onSessionRename = { session, newTitle ->
                        viewModel.renameSession(session.sessionId, newTitle)
                    },
                    onNewChat       = {
                        tickHaptic()
                        viewModel.newChat()
                        scope.launch { drawerState.close() }
                    }
                )
            },
            scrimColor = MaterialTheme.colorScheme.background.copy(alpha = 0.75f)
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    ChatTopBar(
                        sessionTitle  = uiState.sessionTitle,
                        isGenerating  = uiState.isGenerating,
                        isTtsSpeaking = isTtsSpeaking.value,
                        onStopTts     = stopSpeaking,
                        onDrawerClick = { tickHaptic(); scope.launch { drawerState.open() } },
                        onNewChat     = { tickHaptic(); viewModel.newChat() },
                        onSettings    = { tickHaptic(); viewModel.showSettings() },
                        onModelInfo   = { viewModel.showModelInfo() }
                    )
                },
                bottomBar = {
                    ChatInputArea(
                        textFlow       = viewModel.inputText,
                        onValueChange  = viewModel::onInputChanged,
                        isGenerating   = uiState.isGenerating,
                        onSend         = {
                            if (voiceInput.isListening) voiceInput.stop()
                            confirmHaptic()
                            viewModel.sendMessage()
                        },
                        onStop         = { tickHaptic(); viewModel.stopGeneration() },
                        documentActive = uiState.documentContext != null,
                        documentName   = uiState.documentName,
                        documentWords  = remember(uiState.documentContext) {
                            uiState.documentContext?.trim()
                                ?.split(Regex("\\s+"))?.size ?: 0
                        },
                        onDocumentClear = { tickHaptic(); viewModel.clearDocumentContext() },
                        onDocumentClick = { tickHaptic(); viewModel.showDocumentSheet() },
                        isEmpty         = uiState.messages.isEmpty(),
                        inputHistory    = uiState.inputHistory,
                        focusRequester  = inputFocusRequester,
                        voiceAvailable  = true,
                        isListening     = voiceInput.isListening,
                        onMicClick      = onMicClick
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.TopCenter
                ) {
                Column(
                    modifier = (if (wideLayout) Modifier.widthIn(max = 1080.dp)
                                else Modifier.fillMaxWidth()).fillMaxHeight()
                ) {
                // Slim status strip: engine load takes seconds — without this the
                // first send just feels frozen.
                ModelStatusBanner(
                    modelInfo = uiState.modelInfo,
                    onRetry = viewModel::retryModelLoading,
                    onChooseModel = { showModelPickerDialog = true },
                    onShowGuidance = viewModel::showModelInfo
                )

                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyState(
                        modelInfo       = uiState.modelInfo,
                        sessions        = uiState.sessions,
                        modifier        = Modifier.fillMaxWidth().weight(1f),
                        onPrompt        = { prompt ->
                            viewModel.onInputChanged(prompt)
                            focusComposer()
                        },
                        onTemplate      = { tickHaptic(); selectedTemplate = it },
                        onModelClick    = { showModelPickerDialog = true },
                        onDocumentClick = { tickHaptic(); viewModel.showDocumentSheet() },
                        onSessionClick  = { session -> tickHaptic(); viewModel.loadSession(session) }
                    )
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state          = listState,
                            modifier       = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            // contentType lets LazyColumn recycle user/AI bubble
                            // slots separately — less re-layout when scrolling.
                            val lastAiId = uiState.messages.lastOrNull { it.role == Role.AI }?.id
                            items(uiState.messages, key = { it.id }, contentType = { it.role }) { msg ->
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = true,
                                    enter = androidx.compose.animation.fadeIn(
                                        animationSpec = androidx.compose.animation.core.tween(PocketShadowMotion.enterMs)
                                    ) + androidx.compose.animation.slideInVertically(
                                        animationSpec = androidx.compose.animation.core.tween(PocketShadowMotion.enterMs),
                                        initialOffsetY = { it / 8 }
                                    ),
                                    exit = androidx.compose.animation.fadeOut(
                                        animationSpec = androidx.compose.animation.core.tween(PocketShadowMotion.exitMs)
                                    )
                                ) {
                                ChatMessageBubble(
                                    message       = msg,
                                    modifier      = Modifier.animateItem(
                                        // No fade specs: the committed AI message REPLACES the
                                        // streaming bubble (different key), so a fade-in makes the
                                        // finished reply flash transparent for a frame. Placement
                                        // animation (reordering) still applies.
                                        fadeInSpec  = null,
                                        fadeOutSpec = null
                                    ),
                                    hapticEnabled = uiState.hapticFeedback,
                                    onRetry       = if (msg.id == uiState.retryMessageId)
                                                      viewModel::retryGeneration else null,
                                    onRegenerate  = if (msg.role == Role.AI && msg.id != uiState.retryMessageId)
                                                      ({ viewModel.regenerateMessage(msg.id) }) else null,
                                    onReadAloud = if (msg.role == Role.AI)
                                                      ({ readAloud(msg.content) }) else null,
                                    onQuickAction = if (msg.role == Role.AI)
                                                      ({ prompt -> viewModel.sendQuickAction(prompt) }) else null,
                                    // Visible actions on the newest AI reply only —
                                    // older ones keep the long-press menu.
                                    showActions   = msg.id == lastAiId &&
                                                    msg.id != uiState.retryMessageId &&
                                                    !uiState.isGenerating,
                                    onEdit        = if (msg.role == Role.USER)
                                                      ({ content ->
                                                          viewModel.onInputChanged(content)
                                                          focusComposer()
                                                      }) else null
                                )
                                }
                            }
                            if (uiState.streamingId != null) {
                                item(key = "streaming_bubble") {
                                    // StreamingBubble owns the collectAsStateWithLifecycle call
                                    // so recomposition stays scoped to this single item.
                                    StreamingBubble(
                                        id          = uiState.streamingId!!,
                                        contentFlow = viewModel.streamingContent
                                    )
                                }
                            }
                        }

                        // ── Jump-to-bottom pill (shows when scrolled up) ──────
                        val showJumpToBottom by remember {
                            derivedStateOf { listState.canScrollForward }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible  = showJumpToBottom,
                            enter    = androidx.compose.animation.fadeIn(tween(150)) +
                                       androidx.compose.animation.scaleIn(tween(150), initialScale = 0.8f),
                            exit     = androidx.compose.animation.fadeOut(tween(150)) +
                                       androidx.compose.animation.scaleOut(tween(150), targetScale = 0.8f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                        ) {
                            FilledIconButton(
                                onClick = {
                                    tickHaptic()
                                    scope.launch {
                                        val total = listState.layoutInfo.totalItemsCount
                                        if (total > 0) {
                                            listState.animateScrollToItem(total - 1)
                                            listState.scrollBy(100_000f)  // clamp to true end
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor   = ElectricViolet
                                ),
                                modifier = Modifier
                                    .size(38.dp)
                                    .border(0.5.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                        CircleShape)
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, "Scroll to bottom",
                                    modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
                } // content Column
                } // centered tablet/foldable content Box
            }
        }

        selectedTemplate?.let { template ->
            PromptTemplateSheet(
                template = template,
                onDismiss = { selectedTemplate = null },
                onUse = { prompt ->
                    viewModel.onInputChanged(prompt)
                    selectedTemplate = null
                    focusComposer()
                }
            )
        }

        if (uiState.showSettings) {
            SettingsBottomSheet(
                        uiState               = uiState,
                        onThemeMode           = {
                            if (it != uiState.themeMode) { tickHaptic(); viewModel.setThemeMode(it) }
                        },
                        onFontSize            = {
                            if (it != uiState.fontSize) { tickHaptic(); viewModel.setFontSize(it) }
                        },
                        onHapticFeedback      = {
                            if (it) performPocketHaptic(
                                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                                previewWhenEnabling = true
                            )
                            viewModel.setHapticFeedback(it)
                        },
                        onSoundEffects        = { tickHaptic(); viewModel.setSoundEffects(it) },
                onAutoScroll          = { tickHaptic(); viewModel.setAutoScroll(it) },
                onSaveHistory         = { tickHaptic(); viewModel.setSaveHistory(it) },
                onContextWindowSize   = viewModel::setContextWindowSize,
                onCustomSystemPrompt  = viewModel::setCustomSystemPrompt,
                onModelInfo           = viewModel::showModelInfo,
                availableVoices       = availableVoices.value,
                onTtsVoice            = { tickHaptic(); viewModel.setTtsVoiceName(it) },
                ttsAccent             = uiState.ttsAccent,
                onTtsAccent           = { tickHaptic(); viewModel.setTtsAccent(it) },
                onTtsSpeechRate       = viewModel::setTtsSpeechRate,
                onTtsPitch            = viewModel::setTtsPitch,
                previewPlayingAccent  = previewPlayingAccent.value,
                onPlayPreview         = playPreview,
                onSelectModel         = { tickHaptic(); viewModel.setSelectedModel(it) },
                onExportChats         = { showExportDialog = true },
                onImportChats         = { importArchiveLauncher.launch(arrayOf("application/json", "text/*", "*/*")) },
                onRateApp              = mainViewModel::requestReview,
                onSendFeedback         = { sendFeedback(context) },
                onDismiss             = {
                    val locale = when (uiState.ttsAccent) {
                        "US" -> Locale.US
                        "UK" -> Locale.UK
                        "IN" -> Locale("en", "IN")
                        "AU" -> Locale("en", "AU")
                        "CA" -> Locale.CANADA
                        else -> Locale.getDefault()
                    }
                    try {
                        tts.value?.language = locale
                    } catch (e: Exception) {}
                    previewPlayingAccent.value = null
                    viewModel.dismissSettings()
                }
            )
        }

        if (uiState.showDocumentSheet) {
            val excerpts by viewModel.documentExcerpts.collectAsStateWithLifecycle()
            DocumentSheet(
                initialText = uiState.documentContext.orEmpty(),
                initialName = uiState.documentName,
                excerpts = excerpts,
                onConfirm = viewModel::setDocumentContext,
                onDismiss = viewModel::dismissDocumentSheet
            )
        }

        if (uiState.showModelInfo) {
            ModelInfoSheet(
                modelInfo         = uiState.modelInfo,
                contextWindowSize = uiState.contextWindowSize,
                models            = uiState.availableModels,
                selectedPath      = uiState.selectedModelPath,
                onSelectModel     = viewModel::setSelectedModel,
                onDismiss         = viewModel::dismissModelInfo
            )
        }

        if (showModelPickerDialog) {
            ChooseModelDialog(
                models        = uiState.availableModels,
                selectedPath  = uiState.selectedModelPath,
                onSelectModel = viewModel::setSelectedModel,
                onDismiss     = { showModelPickerDialog = false }
            )
        }

        if (showExportDialog) {
            ArchivePasswordDialog(
                title       = "Export Chats",
                description = "Create a password-protected local archive. You will need this password to import it later.",
                actionLabel = "Export",
                onConfirm   = { passphrase ->
                    pendingExportPassphrase = passphrase
                    showExportDialog = false
                    exportArchiveLauncher.launch("pocketshadow-chats.json")
                },
                onDismiss   = { showExportDialog = false }
            )
        }

        if (showImportDialog) {
            ArchivePasswordDialog(
                title       = "Import Chats",
                description = "Enter the password used when this PocketShadow archive was exported.",
                actionLabel = "Import",
                onConfirm   = { passphrase ->
                    val uriString = pendingImportUri
                    showImportDialog = false
                    pendingImportUri = null
                    if (uriString != null) {
                        scope.launch {
                            runCatching {
                                context.contentResolver.openInputStream(android.net.Uri.parse(uriString))
                                    ?.use { it.readBytes() }
                                    ?: error("Could not open the selected archive.")
                            }.onSuccess { bytes ->
                                viewModel.importEncryptedArchive(bytes, passphrase)
                            }.onFailure {
                                viewModel.setArchiveStatus(it.message ?: "Import failed while reading the file.")
                            }
                        }
                    }
                },
                onDismiss   = {
                    showImportDialog = false
                    pendingImportUri = null
                }
            )
        }
    }
}

// ── PocketShadow amber neural logo mark ─────────────────────────────────────────
//
// Canvas-drawn so it works at any size with no extra drawable files.
// Three nodes at 120° form an equilateral triangle; organic bezier dendrites
// radiate from a glowing center node; a pocket arc connects the lower two.

@Composable
fun PocketShadowLogoMark(
    modifier: Modifier = Modifier,
    size    : Float    = 54f
) {
    Canvas(modifier = modifier.size(size.dp)) {
        val w  = this.size.width
        val h  = this.size.height
        val cx = w / 2f
        val cy = h / 2f

        val amber    = ElectricViolet
        val amberDim = ElectricVioletDim

        // Node positions (equilateral triangle, tip at top)
        val topNode   = Offset(cx,            cy - h * 0.38f)
        val leftNode  = Offset(cx - w * 0.29f, cy + h * 0.20f)
        val rightNode = Offset(cx + w * 0.29f, cy + h * 0.20f)

        val dendrite  = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val arcStroke = Stroke(width = w * 0.038f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val glowStroke = Stroke(width = w * 0.10f,  cap = StrokeCap.Round)

        fun bezier(from: Offset, to: Offset, cp1: Offset, cp2: Offset) = Path().apply {
            moveTo(from.x, from.y); cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, to.x, to.y)
        }

        val center = Offset(cx, cy)

        // ── Glow halos under dendrites ────────────────────────────────────────
        val glowColor = Color(amber.red, amber.green, amber.blue, 0.10f)
        drawPath(bezier(center, topNode,
            Offset(cx, cy - h * 0.10f), Offset(cx, topNode.y + h * 0.10f)),
            color = glowColor, style = glowStroke)
        drawPath(bezier(center, leftNode,
            Offset(cx - w * 0.10f, cy), Offset(leftNode.x + w * 0.10f, leftNode.y)),
            color = glowColor, style = glowStroke)
        drawPath(bezier(center, rightNode,
            Offset(cx + w * 0.10f, cy), Offset(rightNode.x - w * 0.10f, rightNode.y)),
            color = glowColor, style = glowStroke)

        // ── Dendrites ─────────────────────────────────────────────────────────
        val dendColor = Color(amber.red, amber.green, amber.blue, 0.75f)
        drawPath(bezier(center, topNode,
            Offset(cx, cy - h * 0.10f), Offset(cx, topNode.y + h * 0.10f)),
            color = dendColor, style = dendrite)
        drawPath(bezier(center, leftNode,
            Offset(cx - w * 0.10f, cy), Offset(leftNode.x + w * 0.10f, leftNode.y)),
            color = dendColor, style = dendrite)
        drawPath(bezier(center, rightNode,
            Offset(cx + w * 0.10f, cy), Offset(rightNode.x - w * 0.10f, rightNode.y)),
            color = dendColor, style = dendrite)

        // ── Pocket arc (bottom curve) ─────────────────────────────────────────
        val arcPath = Path().apply {
            moveTo(leftNode.x, leftNode.y)
            cubicTo(leftNode.x,  leftNode.y  + h * 0.18f,
                    rightNode.x, rightNode.y + h * 0.18f,
                    rightNode.x, rightNode.y)
        }
        drawPath(arcPath,
            color = Color(amber.red, amber.green, amber.blue, 0.55f),
            style = arcStroke)

        // ── Outer node glows ──────────────────────────────────────────────────
        val nodeGlowR = w * 0.13f
        drawCircle(Color(amber.red, amber.green, amber.blue, 0.18f), nodeGlowR, topNode)
        drawCircle(Color(amber.red, amber.green, amber.blue, 0.18f), nodeGlowR, leftNode)
        drawCircle(Color(amber.red, amber.green, amber.blue, 0.18f), nodeGlowR, rightNode)

        // ── Outer node fills ──────────────────────────────────────────────────
        val nodeFillR = w * 0.075f
        drawCircle(amber, nodeFillR, topNode)
        drawCircle(amber, nodeFillR, leftNode)
        drawCircle(amber, nodeFillR, rightNode)

        // ── Center node glow layers ───────────────────────────────────────────
        drawCircle(Color(amber.red, amber.green, amber.blue, 0.18f), w * 0.26f, center)
        drawCircle(Color(amber.red, amber.green, amber.blue, 0.30f), w * 0.18f, center)

        // ── Center node fill ──────────────────────────────────────────────────
        drawCircle(amberDim, w * 0.155f, center)
        drawCircle(amber,    w * 0.145f, center)

        // ── Specular highlight ────────────────────────────────────────────────
        drawCircle(Color.White.copy(alpha = 0.45f), w * 0.055f,
            Offset(cx - w * 0.045f, cy - h * 0.045f))
    }
}

// ── History drawer ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDrawer(
    sessions        : List<ChatSessionEntity>,
    hapticEnabled   : Boolean,
    searchQuery     : String,
    searchResults   : List<com.pocketshadow.app.data.db.SearchResult>,
    isSearching     : Boolean,
    onSearchQuery   : (String) -> Unit,
    onSearchResult  : (com.pocketshadow.app.data.db.SearchResult) -> Unit,
    onClearSearch   : () -> Unit,
    onSessionClick  : (ChatSessionEntity) -> Unit,
    onSessionDelete : (ChatSessionEntity) -> Unit,
    onSessionRename : (ChatSessionEntity, String) -> Unit,
    onNewChat       : () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier             = Modifier.width(320.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text("Chat History", style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold))
            IconButton(
                onClick  = onNewChat,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Rounded.Add, "New Chat", tint = ElectricViolet,
                    modifier = Modifier.size(24.dp))
            }
        }

        // ── Search bar ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value     = searchQuery,
            onValueChange = onSearchQuery,
            placeholder  = { Text("Search messages…", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon  = {
                Icon(Icons.Rounded.Search, "Search",
                    modifier = Modifier.size(18.dp),
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = onClearSearch, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Close, "Clear",
                            modifier = Modifier.size(16.dp),
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine   = true,
            shape        = RoundedCornerShape(14.dp),
            modifier     = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            textStyle    = MaterialTheme.typography.bodyMedium,
            colors       = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = ElectricViolet,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        // ── Search results or session list ──────────────────────────────────────
        if (searchQuery.isNotBlank()) {
            if (isSearching) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color       = ElectricViolet
                    )
                }
            } else if (searchResults.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), Alignment.Center) {
                    Text(
                        "No results",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(searchResults, key = { it.messageId }) { result ->
                        SearchResultItem(
                            result    = result,
                            onClick   = { onSearchResult(result) }
                        )
                    }
                }
            }
        } else if (sessions.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("No conversations yet", style = MaterialTheme.typography.bodyMedium)
                Text("Your chats will appear here.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                items(sessions, key = { it.sessionId }) { session ->
                    SessionItem(
                        session       = session,
                        hapticEnabled = hapticEnabled,
                        onClick       = { onSessionClick(session) },
                        onDelete      = { onSessionDelete(session) },
                        onRename = { newTitle -> onSessionRename(session, newTitle) }
                    )
                }
            }
        }
    }
}

// ── Search result item ────────────────────────────────────────────────────────

@Composable
private fun SearchResultItem(
    result : com.pocketshadow.app.data.db.SearchResult,
    onClick: () -> Unit
) {
    val isUser = result.role == "USER"
    val dateFmt = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text  = if (isUser) "You" else "AI",
                style = MaterialTheme.typography.labelSmall.copy(
                    color    = if (isUser) ElectricViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Text(
                text  = dateFmt.format(java.util.Date(result.timestamp)),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text  = result.content.take(120).replace("\n", " "),
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (result.sessionTitle.isNotBlank()) {
            Text(
                text  = result.sessionTitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    color  = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontStyle = FontStyle.Italic
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Session item with swipe-to-delete confirmation ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SessionItem(
    session       : ChatSessionEntity,
    hapticEnabled : Boolean,
    onClick       : () -> Unit,
    onDelete      : () -> Unit,
    onRename      : (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText       by remember(session.title) { mutableStateOf(session.title) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange  = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDeleteDialog = true
                false  // Don't dismiss yet — wait for dialog confirmation
            } else false
        },
        positionalThreshold = { total -> total * 0.40f }
    )

    // Confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                scope.launch { dismissState.reset() }
            },
            icon  = {
                Icon(Icons.Rounded.Delete, null,
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp))
            },
            title = { Text("Delete conversation?") },
            text  = {
                Text(
                    "\"${session.title}\" will be permanently deleted.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDelete() },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    scope.launch { dismissState.reset() }
                }) { Text("Cancel") }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape          = RoundedCornerShape(20.dp)
        )
    }

    // Rename dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false; renameText = session.title },
            icon  = {
                Icon(Icons.Rounded.Edit, null,
                    tint     = ElectricViolet,
                    modifier = Modifier.size(24.dp))
            },
            title = { Text("Rename conversation") },
            text  = {
                OutlinedTextField(
                    value         = renameText,
                    onValueChange = { renameText = it },
                    singleLine    = true,
                    shape         = RoundedCornerShape(12.dp),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = ElectricViolet,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor          = ElectricViolet
                    )
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(renameText.trim())
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false; renameText = session.title }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape          = RoundedCornerShape(20.dp)
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.16f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(end = 16.dp),
                Alignment.CenterEnd
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(Icons.Rounded.Delete, "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    ) {
        Surface(
            shape    = RoundedCornerShape(12.dp),
            color    = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = {
                        if (hapticEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        showRenameDialog = true
                        renameText = session.title
                    }
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.title,
                        style    = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatSessionDate(session.createdAt),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
                IconButton(
                    onClick = {
                        if (hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        renameText = session.title
                        showRenameDialog = true
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Rounded.Edit, "Rename conversation",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun formatSessionDate(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "Just now"
        diff < 3_600_000L   -> "${diff / 60_000}m ago"
        diff < 86_400_000L  -> "${diff / 3_600_000}h ago"
        diff < 604_800_000L -> "${diff / 86_400_000}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    sessionTitle : String,
    isGenerating : Boolean,
    isTtsSpeaking: Boolean,
    onStopTts    : () -> Unit,
    onDrawerClick: () -> Unit,
    onNewChat    : () -> Unit,
    onSettings   : () -> Unit,
    onModelInfo  : () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(isGenerating) {
        elapsedSeconds = 0
        if (isGenerating) {
            while (true) {
                kotlinx.coroutines.delay(1_000)
                elapsedSeconds++
            }
        }
    }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onDrawerClick) {
                Icon(Icons.Rounded.Menu, "History",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PocketShadowLogoMark(size = 26f)
                Text(sessionTitle,
                    style    = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        },
        actions = {
            // Pulsing amber dot while generating
            if (isGenerating) {
                val infiniteTransition = rememberInfiniteTransition(label = "gen")
                val alpha by infiniteTransition.animateFloat(
                    initialValue  = 0.3f,
                    targetValue   = 1f,
                    animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                    label         = "dot"
                )
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = alpha))
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Generating · ${elapsedSeconds}s",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ElectricViolet,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            if (isTtsSpeaking) {
                IconButton(onClick = onStopTts) {
                    val infiniteTransition = rememberInfiniteTransition(label = "tts_pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue  = 0.95f,
                        targetValue   = 1.15f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label         = "scale"
                    )
                    Icon(
                        Icons.Rounded.VolumeOff,
                        "Stop Reading",
                        tint     = ElectricViolet,
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scale)
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, "Menu",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded         = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape            = RoundedCornerShape(14.dp),
                    containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation   = 0.dp,
                    modifier         = Modifier
                        .widthIn(min = 180.dp)
                        .border(0.5.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp))
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Rounded.Add, null, tint = ElectricViolet,
                                modifier = Modifier.size(20.dp))
                        },
                        text    = {
                            Text("New Chat",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold))
                        },
                        onClick = { showMenu = false; onNewChat() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(
                        color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        thickness = 0.5.dp,
                        modifier  = Modifier.padding(horizontal = 8.dp)
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Rounded.Settings, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                        },
                        text    = {
                            Text("Settings",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium))
                        },
                        onClick = { showMenu = false; onSettings() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    )
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(Icons.Rounded.Memory, null,
                                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp))
                        },
                        text    = {
                            Text("Model Info",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium))
                        },
                        onClick = { showMenu = false; onModelInfo() },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun ModelStatusBanner(
    modelInfo: ModelInfo,
    onRetry: () -> Unit = {},
    onChooseModel: () -> Unit = {},
    onShowGuidance: () -> Unit = {}
) {
    androidx.compose.animation.AnimatedVisibility(visible = !modelInfo.isReady) {
        val status    = modelInfo.statusText
        val isWorking = status == "Not initialized" ||
                        status.contains("Loading",   ignoreCase = true) ||
                        status.contains("Preparing", ignoreCase = true)
        val title = if (isWorking) "Preparing private AI model" else "AI model needs attention"
        val message = when {
            status == "Not initialized" ->
                "Setting up private AI on this device. You can explore while it gets ready."
            isWorking -> status
            else -> status
        }
        Surface(
            color    = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isWorking) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color       = ElectricViolet
                        )
                    } else {
                        Icon(
                            imageVector        = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.error,
                            modifier           = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (isWorking) ElectricViolet else MaterialTheme.colorScheme.error
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text     = message,
                            style    = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isWorking) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(CircleShape),
                        color = ElectricViolet,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Retry", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = onChooseModel,
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Choose model", maxLines = 1)
                        }
                    }
                    TextButton(
                        onClick = onShowGuidance,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Storage & RAM help")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    modelInfo: ModelInfo,
    sessions: List<ChatSessionEntity>,
    modifier: Modifier = Modifier,
    onPrompt: (String) -> Unit = {},
    onTemplate: (PromptTemplate) -> Unit = {},
    onModelClick: () -> Unit = {},
    onDocumentClick: () -> Unit = {},
    onSessionClick: (ChatSessionEntity) -> Unit = {}
) {
    var showAllTemplates by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val featured = promptTemplates.take(4)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.34f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box {
                Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(ElectricViolet.copy(alpha = 0.26f),
                        ElectricViolet.copy(alpha = 0.09f),
                        MaterialTheme.colorScheme.surfaceContainer)
                )))
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(18.dp),
                            color = ElectricViolet.copy(alpha = 0.17f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, ElectricViolet.copy(alpha = 0.25f))) {
                            PocketShadowLogoMark(Modifier.padding(9.dp), size = 43f)
                        }
                        Column {
                            Text("POCKETSHADOW", style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricViolet, fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.5.sp))
                            Text(if (modelInfo.isReady) "Private AI · Ready" else "Private AI · On your phone",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50.dp),
                            color = if (modelInfo.isReady) Color(0xFF173D2B)
                                else ElectricViolet.copy(alpha = 0.13f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(
                                    if (modelInfo.isReady) Color(0xFF61D99A) else ElectricViolet))
                                Text(if (modelInfo.isReady) "READY" else "LOCAL",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = if (modelInfo.isReady) Color(0xFF8EE6B5) else ElectricViolet))
                            }
                        }
                    }
                    Text("Big ideas.\nRight in your pocket.",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold, lineHeight = 35.sp,
                            letterSpacing = (-0.6).sp),
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("Think it through, make something, or just ask. Your conversations stay on this device.",
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 21.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(Icons.Rounded.WifiOff to "Works offline",
                            Icons.Rounded.Lock to "Stays private").forEach { (icon, label) ->
                            Surface(shape = RoundedCornerShape(50.dp),
                                color = ElectricViolet.copy(alpha = 0.13f)) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(icon, null, tint = ElectricViolet, modifier = Modifier.size(15.dp))
                                    Text(label, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    Surface(onClick = onModelClick, shape = RoundedCornerShape(15.dp),
                        color = ElectricViolet, contentColor = OnAmber) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            Icon(Icons.Rounded.Memory, null, modifier = Modifier.size(19.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (modelInfo.isReady) modelInfo.displayName else "Your on-device model",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (modelInfo.isReady) "Tap to manage models" else "Tap to check model setup",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Icon(Icons.Rounded.ArrowForward, null)
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pick a starting point", style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold))
            val promptColumns = if (LocalConfiguration.current.screenWidthDp < 360 ||
                LocalDensity.current.fontScale > 1.4f) 1 else 2
            featured.chunked(promptColumns).forEach { rowTemplates ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                    rowTemplates.forEach { template ->
                        FeatureCard(template.title, template.description, template.icon,
                            template.iconColor, Modifier.weight(1f)) { onTemplate(template) }
                    }
                    if (rowTemplates.size == 1 && promptColumns == 2) Spacer(Modifier.weight(1f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onDocumentClick, modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Rounded.AttachFile, null, tint = ElectricViolet)
                    Spacer(Modifier.width(7.dp))
                    Text("Ask about a file")
                }
                OutlinedButton(onClick = { showAllTemplates = !showAllTemplates },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = ElectricViolet)
                    Spacer(Modifier.width(7.dp))
                    Text(if (showAllTemplates) "Show less" else "Explore prompts")
                }
            }
        }

        if (showAllTemplates) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("More prompts", style = MaterialTheme.typography.titleMedium)
                promptTemplates.drop(4).forEach { template ->
                    FeatureCard(template.title, template.description, template.icon,
                        template.iconColor, Modifier.fillMaxWidth()) { onTemplate(template) }
                }
            }
        }
        if (sessions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.History, null, tint = ElectricViolet,
                    modifier = Modifier.size(21.dp))
                Text("Your recent chats", style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                }
                sessions.take(4).forEachIndexed { index, session ->
                    Surface(onClick = { onSessionClick(session) },
                        shape = RoundedCornerShape(17.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = ElectricViolet.copy(alpha = if (index == 0) 0.19f else 0.09f)) {
                                Icon(Icons.Rounded.ChatBubbleOutline, null, tint = ElectricViolet,
                                    modifier = Modifier.padding(9.dp).size(19.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(session.title, style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold), maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                                Text(formatSessionDate(session.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DiscoveryChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = PocketShadowMotion.pressSpring,
        label = "discovery_press"
    )
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
        }
    }
}

@Composable
private fun FeatureCard(
    title      : String,
    description: String,
    icon       : androidx.compose.ui.graphics.vector.ImageVector,
    iconColor  : Color,
    modifier   : Modifier = Modifier,
    onClick    : () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = PocketShadowMotion.pressSpring,
        label = "feature_press"
    )
    Surface(
        shape   = RoundedCornerShape(14.dp),
        color   = MaterialTheme.colorScheme.surfaceContainer,
        border  = androidx.compose.foundation.BorderStroke(
            0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        ),
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .heightIn(min = 108.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Streaming bubble (isolated recomposition scope) ───────────────────────────
//
// Collecting streamingContent here — NOT at PocketShadowChatScreen level — means
// only this composable rerenders per token. The LazyColumn, TopBar, and
// InputArea are completely unaffected during streaming.

@Composable
private fun StreamingBubble(
    id          : String,
    contentFlow : kotlinx.coroutines.flow.StateFlow<String>
) {
    val content by contentFlow.collectAsStateWithLifecycle()

    if (content.isEmpty()) {
        // ── Thinking indicator (shimmer before first token) ──────────────────
        val infiniteTransition = rememberInfiniteTransition(label = "thinking")
        val alpha by infiniteTransition.animateFloat(
            initialValue  = 0.3f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                animation  = tween(800),
                repeatMode = RepeatMode.Reverse
            ),
            label = "thinking_alpha"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment     = Alignment.Bottom
        ) {
            // AI avatar
            GlowingAiAvatar()
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Thinking",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) { i ->
                        val delayAlpha by infiniteTransition.animateFloat(
                            initialValue  = 0.3f,
                            targetValue   = 1f,
                            animationSpec = infiniteRepeatable(
                                animation  = tween(800, delayMillis = i * 200),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dot_$i"
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricViolet.copy(alpha = delayAlpha))
                        )
                    }
                }
            }
        }
        }
    } else {
        // remember(id): a fresh currentTimeMillis() per token tick made the
        // Message unequal every 50 ms and re-rendered the footer needlessly.
        val startedAt = remember(id) { System.currentTimeMillis() }
        ChatMessageBubble(
            message = Message(
                id          = id,
                role        = Role.AI,
                content     = content,
                isStreaming  = true,
                timestamp   = startedAt
            )
        )
    }
}

// ── Settings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    uiState              : com.pocketshadow.app.ui.viewmodel.ChatUiState,
    onThemeMode          : (ThemeMode) -> Unit,
    onFontSize           : (FontScale) -> Unit,
    onHapticFeedback     : (Boolean) -> Unit,
    onSoundEffects       : (Boolean) -> Unit,
    onAutoScroll         : (Boolean) -> Unit,
    onSaveHistory        : (Boolean) -> Unit,
    onContextWindowSize  : (Int) -> Unit,
    onCustomSystemPrompt : (String) -> Unit,
    onModelInfo          : () -> Unit,
    availableVoices      : List<VoiceOption>,
    onTtsVoice           : (String) -> Unit,
    ttsAccent            : String,
    onTtsAccent          : (String) -> Unit,
    onTtsSpeechRate      : (Float) -> Unit,
    onTtsPitch           : (Float) -> Unit,
    previewPlayingAccent : String?,
    onPlayPreview        : (String) -> Unit,
    onSelectModel        : (String) -> Unit,
    onExportChats        : () -> Unit,
    onImportChats        : () -> Unit,
    onRateApp            : () -> Unit,
    onSendFeedback       : () -> Unit,
    onDismiss            : () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle       = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize(
                    animationSpec = tween(durationMillis = 220)
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your PocketShadow",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Private controls, tailored to this device",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, "Close",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = VioletGlow.copy(alpha = 0.38f),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, ElectricViolet.copy(alpha = 0.28f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Private by default", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Chats and model processing stay on this device.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("MODEL ON THIS DEVICE")
            Spacer(Modifier.height(8.dp))
            ModelPickerRow(
                models        = uiState.availableModels,
                selectedPath  = uiState.selectedModelPath,
                onSelectModel = onSelectModel
            )
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("APPEARANCE")
            Spacer(Modifier.height(12.dp))
            Text("Theme",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(10.dp))
            ThemePickerRow(current = uiState.themeMode, onSelect = onThemeMode)
            Spacer(Modifier.height(16.dp))
            Text("Text Size",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(10.dp))
            FontSizeRow(current = uiState.fontSize, onSelect = onFontSize)
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("CHAT EXPERIENCE")
            Spacer(Modifier.height(4.dp))
            SettingsSwitchRow("Haptic Feedback", "Vibrate on send and long-press",
                uiState.hapticFeedback, onHapticFeedback)
            SettingsSwitchRow("Sound Effects", "Soft sounds for send and completion",
                uiState.soundEffects, onSoundEffects)
            SettingsSwitchRow("Auto-scroll", "Keep latest message in view",
                uiState.autoScroll, onAutoScroll)
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("PRIVACY & AI")
            Spacer(Modifier.height(4.dp))
            SettingsSwitchRow("Save History", "Store conversations locally",
                uiState.saveHistory, onSaveHistory)
            Spacer(Modifier.height(12.dp))
            ArchiveActionsSection(
                status   = uiState.archiveStatus,
                onExport = onExportChats,
                onImport = onImportChats
            )
            Spacer(Modifier.height(12.dp))
            ContextWindowRow(value = uiState.contextWindowSize, onValue = onContextWindowSize)
            Spacer(Modifier.height(20.dp))

            // ── Custom AI Persona ──────────────────────────────────────────────
            CustomPromptField(
                value    = uiState.customSystemPrompt,
                onChange = onCustomSystemPrompt
            )

            Spacer(Modifier.height(24.dp))

            // ── Voice & Speech ─────────────────────────────────────────────────
            SettingsSectionHeader("VOICE & SPEECH")
            Spacer(Modifier.height(12.dp))
            VoiceSpeechSection(
                currentVoice         = uiState.ttsVoiceName,
                currentAccent        = ttsAccent,
                currentRate          = uiState.ttsSpeechRate,
                currentPitch         = uiState.ttsPitch,
                availableVoices      = availableVoices,
                onVoice              = onTtsVoice,
                onAccent             = onTtsAccent,
                onRate               = onTtsSpeechRate,
                onPitch              = onTtsPitch,
                previewPlayingAccent = previewPlayingAccent,
                onPlayPreview        = onPlayPreview
            )

            Spacer(Modifier.height(24.dp))

            // ── About ──────────────────────────────────────────────────────────
            SettingsSectionHeader("ABOUT")
            Spacer(Modifier.height(12.dp))
            AboutSection(
                onModelInfo = onModelInfo,
                onRateApp = onRateApp,
                onSendFeedback = onSendFeedback
            )
        }
    }
}

@Composable
private fun ArchiveActionsSection(
    status  : String?,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export")
            }
            OutlinedButton(
                onClick = onImport,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.FileDownload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Import")
            }
        }
        Text(
            text = status ?: "Encrypted local archives help you move chats without cloud backup.",
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (status == null) MaterialTheme.colorScheme.onSurfaceVariant else ElectricViolet,
                lineHeight = 16.sp
            )
        )
    }
}

@Composable
private fun ArchivePasswordDialog(
    title      : String,
    description: String,
    actionLabel: String,
    onConfirm  : (String) -> Unit,
    onDismiss  : () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    val valid = passphrase.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Archive password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = passphrase.isNotEmpty() && !valid,
                    supportingText = {
                        Text("Use at least 8 characters. PocketShadow cannot recover this password.")
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        cursorColor = ElectricViolet
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = valid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor = White
                )
            ) { Text(actionLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AboutSection(
    onModelInfo: () -> Unit = {},
    onRateApp: () -> Unit = {},
    onSendFeedback: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Version badge
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PocketShadowLogoMark(size = 22f)
                Text("PocketShadow",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = VioletGlow
            ) {
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color      = ElectricViolet,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        // AI Disclaimer — required by Play Store AI policy
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top
            ) {
                Icon(
                    Icons.Rounded.Info, null,
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                )
                Text(
                    "AI responses may be inaccurate. Do not rely on PocketShadow for medical, legal, financial, or safety-critical decisions.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // Privacy note
        Text(
            "All conversations are processed and stored locally on this device only. Nothing is ever sent to any server.",
            style = MaterialTheme.typography.bodySmall.copy(
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        )

        // Model Info button
        OutlinedButton(
            onClick  = onModelInfo,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(12.dp),
            border   = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Rounded.Memory, null,
                modifier = Modifier.size(16.dp),
                tint     = ElectricViolet)
            Spacer(Modifier.width(8.dp))
            Text("View Model Info",
                style = MaterialTheme.typography.bodySmall.copy(color = ElectricViolet))
        }

        OutlinedButton(
            onClick = onRateApp,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Rounded.Star, null, modifier = Modifier.size(16.dp), tint = ElectricViolet)
            Spacer(Modifier.width(8.dp))
            Text("Rate PocketShadow", style = MaterialTheme.typography.bodySmall.copy(color = ElectricViolet))
        }

        OutlinedButton(
            onClick = onSendFeedback,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Rounded.Feedback, null, modifier = Modifier.size(16.dp), tint = ElectricViolet)
            Spacer(Modifier.width(8.dp))
            Text("Send Feedback", style = MaterialTheme.typography.bodySmall.copy(color = ElectricViolet))
        }
    }
}

private fun sendFeedback(context: Context) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "PocketShadow feedback")
        putExtra(Intent.EXTRA_TEXT, "Tell us what worked well and what we can improve:\n\n")
    }
    try {
        ContextCompat.startActivity(context, Intent.createChooser(intent, "Send feedback"), null)
    } catch (_: Exception) {
        Toast.makeText(context, "No app is available to send feedback", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(AmberBright, ElectricVioletDim))))
        Text(title, style = MaterialTheme.typography.labelSmall.copy(
            color = ElectricViolet, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.35.sp))
        Spacer(Modifier.weight(1f))
        HorizontalDivider(Modifier.width(44.dp), color = ElectricViolet.copy(alpha = 0.38f),
            thickness = 1.dp)
    }
}

@Composable
private fun SettingsSwitchRow(
    label      : String,
    description: String,
    checked    : Boolean,
    onChecked  : (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(description,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
        Switch(
            checked = checked, onCheckedChange = onChecked,
            colors  = SwitchDefaults.colors(
                checkedThumbColor   = White,
                checkedTrackColor   = ElectricViolet,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
    }
}

// ── Theme picker ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePickerRow(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier             = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            ThemeSwatchChip(
                mode     = mode,
                selected = current == mode,
                onClick  = { onSelect(mode) },
                modifier = Modifier.widthIn(min = 62.dp)
            )
        }
    }
}

@Composable
private fun ThemeSwatchChip(
    mode    : ThemeMode,
    selected: Boolean,
    onClick : () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchColor = Color(mode.swatchColor)
    val borderColor = if (selected) ElectricViolet
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val borderWidth = if (selected) 2.dp else 0.5.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(swatchColor)
                .then(
                    if (swatchColor == Color.White || swatchColor.red > 0.9f)
                        Modifier.border(1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
                    else Modifier
                )
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, null,
                    tint     = if (swatchColor.red > 0.85f) NearBlack else White,
                    modifier = Modifier.size(14.dp).align(Alignment.Center))
            }
        }
        Text(
            mode.label,
            style    = MaterialTheme.typography.labelSmall.copy(
                color      = if (selected) ElectricViolet
                             else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1
        )
    }
}

// ── Font size chips ───────────────────────────────────────────────────────────

@Composable
private fun FontSizeRow(current: FontScale, onSelect: (FontScale) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(FontScale.SMALL to "Small", FontScale.NORMAL to "Normal", FontScale.LARGE to "Large")
            .forEach { (scale, label) ->
                val selected = current == scale
                FilterChip(
                    selected = selected,
                    onClick  = { onSelect(scale) },
                    label    = { Text(label, style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f),
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricViolet,
                        selectedLabelColor     = White,
                        containerColor         = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border   = FilterChipDefaults.filterChipBorder(
                        enabled             = true,
                        selected            = selected,
                        selectedBorderColor = ElectricViolet,
                        selectedBorderWidth = 0.dp,
                        borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        borderWidth         = 0.5.dp
                    )
                )
            }
    }
}

// ── Context window slider ─────────────────────────────────────────────────────

@Composable
private fun ContextWindowRow(value: Int, onValue: (Int) -> Unit) {
    val isUnlimited = value == 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Context Window", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (isUnlimited) "All messages (char-bounded)"
                    else "Past messages the AI can see",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Text(
                if (isUnlimited) "∞" else "$value",
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = ElectricViolet,
                    fontWeight = FontWeight.Bold)
            )
        }
        Slider(
            value         = if (isUnlimited) 20f else value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange    = 0f..20f,
            steps         = 3,
            enabled       = !isUnlimited,
            colors        = SliderDefaults.colors(
                thumbColor         = ElectricViolet,
                activeTrackColor   = ElectricViolet,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer,
                activeTickColor    = White,
                inactiveTickColor  = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledThumbColor         = MaterialTheme.colorScheme.outline,
                disabledActiveTrackColor   = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("4",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant))
                Spacer(Modifier.width(0.dp))
            }
            // Unlimited toggle chip
            SuggestionChip(
                onClick = { onValue(if (isUnlimited) 10 else 0) },
                label   = {
                    Text(
                        if (isUnlimited) "Limited" else "Unlimited",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                icon    = {
                    Icon(
                        if (isUnlimited) Icons.Rounded.AllInclusive else Icons.Rounded.Lock,
                        null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                colors  = SuggestionChipDefaults.suggestionChipColors(
                    containerColor   = if (isUnlimited) ElectricViolet.copy(alpha = 0.15f)
                                       else MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor       = if (isUnlimited) ElectricViolet
                                       else MaterialTheme.colorScheme.onSurfaceVariant,
                    iconContentColor = if (isUnlimited) ElectricViolet
                                       else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border  = SuggestionChipDefaults.suggestionChipBorder(
                    enabled     = true,
                    borderColor = if (isUnlimited) ElectricViolet.copy(alpha = 0.4f)
                                  else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    borderWidth = if (isUnlimited) 1.dp else 0.5.dp
                )
            )
            Text("20",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}

// ── Model picker row + dialog ─────────────────────────────────────────────────

@Composable
private fun ModelPickerRow(
    models       : List<ModelFile>,
    selectedPath : String,
    onSelectModel: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val currentName = models.firstOrNull { it.path == selectedPath }?.displayName
        ?: if (selectedPath.isBlank()) "Default" else "Unknown"

    Surface(
        onClick = { showDialog = true },
        shape   = RoundedCornerShape(17.dp),
        color   = MaterialTheme.colorScheme.surfaceContainer,
        border  = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.19f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = ElectricViolet.copy(alpha = 0.12f)) {
                Icon(Icons.Rounded.Memory, null, tint = ElectricViolet,
                    modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Active Model", style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text(
                    currentName,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = ElectricViolet,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
            Icon(
                Icons.Rounded.ChevronRight, null,
                tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showDialog) {
        ChooseModelDialog(
            models        = models,
            selectedPath  = selectedPath,
            onSelectModel = onSelectModel,
            onDismiss     = { showDialog = false }
        )
    }
}

@Composable
private fun ChooseModelDialog(
    models       : List<ModelFile>,
    selectedPath : String,
    onSelectModel: (String) -> Unit,
    onDismiss    : () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = {
            Icon(Icons.Rounded.Memory, null,
                tint = ElectricViolet, modifier = Modifier.size(24.dp))
        },
        title = { Text("Choose Model") },
        text  = {
            if (models.isEmpty()) {
                Text(
                    "No additional models found on this device.\nThe bundled default model will be used.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth(),
                    contentPadding      = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(models, key = { it.path }) { model ->
                        val selected = model.path == selectedPath
                        Surface(
                            onClick = {
                                onSelectModel(model.path)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) VioletGlow
                                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier              = Modifier.weight(1f),
                                    verticalArrangement   = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        model.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color      = if (selected) ElectricViolet
                                                         else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (selected) FontWeight.SemiBold
                                                         else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        "%.1f GB  •  %s".format(
                                            model.sizeMb / 1024f,
                                            model.filename
                                        ),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                }
                                if (selected) {
                                    Icon(Icons.Rounded.Check, null,
                                        tint     = ElectricViolet,
                                        modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors  = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
            ) { Text("Done", fontWeight = FontWeight.SemiBold) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape          = RoundedCornerShape(20.dp)
    )
}

// ── Voice & Speech section ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSpeechSection(
    currentVoice         : String,
    currentAccent        : String,
    currentRate          : Float,
    currentPitch         : Float,
    availableVoices      : List<VoiceOption>,
    onVoice              : (String) -> Unit,
    onAccent             : (String) -> Unit,
    onRate               : (Float) -> Unit,
    onPitch              : (Float) -> Unit,
    previewPlayingAccent : String?,
    onPlayPreview        : (String) -> Unit
) {
    var showVoicePicker  by remember { mutableStateOf(false) }
    var showAccentPicker by remember { mutableStateOf(false) }

    val currentLabel = availableVoices.firstOrNull { it.name == currentVoice }?.label
        ?: if (availableVoices.isEmpty()) "Loading voices…" else "System Default"

    val accentLabel = when (currentAccent) {
        "US" -> "English (United States)"
        "UK" -> "English (United Kingdom)"
        "IN" -> "English (India)"
        "AU" -> "English (Australia)"
        "CA" -> "English (Canada)"
        else -> "System Default"
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ── Voice / Accent picker row ──────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Voice Profile", style = MaterialTheme.typography.bodyLarge)
                Text(
                    currentLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ElectricViolet, fontWeight = FontWeight.Medium)
                )
            }
            OutlinedButton(
                onClick  = { showVoicePicker = true },
                enabled  = availableVoices.isNotEmpty(),
                shape    = RoundedCornerShape(10.dp),
                border   = androidx.compose.foundation.BorderStroke(
                    0.5.dp, ElectricViolet.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Rounded.RecordVoiceOver, null,
                    modifier = Modifier.size(15.dp), tint = ElectricViolet)
                Spacer(Modifier.width(6.dp))
                Text("Change", style = MaterialTheme.typography.labelMedium.copy(color = ElectricViolet))
            }
        }

        // ── Read Accent row ───────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Read Accent", style = MaterialTheme.typography.bodyLarge)
                Text(
                    accentLabel,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ElectricViolet, fontWeight = FontWeight.Medium)
                )
            }
            OutlinedButton(
                onClick  = { showAccentPicker = true },
                shape    = RoundedCornerShape(10.dp),
                border   = androidx.compose.foundation.BorderStroke(
                    0.5.dp, ElectricViolet.copy(alpha = 0.6f))
            ) {
                Icon(Icons.Rounded.Public, null,
                    modifier = Modifier.size(15.dp), tint = ElectricViolet)
                Spacer(Modifier.width(6.dp))
                Text("Change", style = MaterialTheme.typography.labelMedium.copy(color = ElectricViolet))
            }
        }

        // ── Speed chips ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Speed", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0.7f to "Slow",
                    1.0f to "Normal",
                    1.4f to "Fast",
                    1.8f to "Very Fast"
                ).forEach { (rate, label) ->
                    val selected = currentRate == rate
                    FilterChip(
                        selected = selected,
                        onClick  = { onRate(rate) },
                        label    = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet,
                            selectedLabelColor     = White,
                            containerColor         = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border   = FilterChipDefaults.filterChipBorder(
                            enabled             = true,
                            selected            = selected,
                            selectedBorderColor = ElectricViolet,
                            selectedBorderWidth = 0.dp,
                            borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            borderWidth         = 0.5.dp
                        )
                    )
                }
            }
        }

        // ── Pitch chips ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pitch", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    0.7f to "Low",
                    1.0f to "Normal",
                    1.3f to "High"
                ).forEach { (pitch, label) ->
                    val selected = currentPitch == pitch
                    FilterChip(
                        selected = selected,
                        onClick  = { onPitch(pitch) },
                        label    = { Text(label, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.weight(1f),
                        colors   = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricViolet,
                            selectedLabelColor     = White,
                            containerColor         = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor             = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border   = FilterChipDefaults.filterChipBorder(
                            enabled             = true,
                            selected            = selected,
                            selectedBorderColor = ElectricViolet,
                            selectedBorderWidth = 0.dp,
                            borderColor         = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            borderWidth         = 0.5.dp
                        )
                    )
                }
            }
        }
    }

    // ── Choose Accent dialog ──────────────────────────────────────────────────
    if (showAccentPicker) {
        val accents = listOf(
            "" to "System Default",
            "US" to "English (United States)",
            "UK" to "English (United Kingdom)",
            "IN" to "English (India)",
            "AU" to "English (Australia)",
            "CA" to "English (Canada)"
        )
        AlertDialog(
            onDismissRequest = {
                onPlayPreview("")
                showAccentPicker = false
            },
            icon  = {
                Icon(Icons.Rounded.Public, null,
                    tint = ElectricViolet, modifier = Modifier.size(24.dp))
            },
            title = { Text("Choose Accent") },
            text  = {
                LazyColumn(
                    modifier            = Modifier.fillMaxWidth(),
                    contentPadding      = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(accents) { (code, label) ->
                        val selected = code == currentAccent
                        Surface(
                            onClick = {
                                onPlayPreview("")
                                onAccent(code)
                                showAccentPicker = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) VioletGlow
                                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color      = if (selected) ElectricViolet
                                                     else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.SemiBold
                                                     else FontWeight.Normal
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                
                                val isCurrentPreview = previewPlayingAccent == code
                                IconButton(
                                    onClick  = { onPlayPreview(code) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector        = if (isCurrentPreview) Icons.Rounded.Stop
                                                             else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isCurrentPreview) "Stop Preview" else "Play Preview",
                                        tint               = if (isCurrentPreview) ElectricViolet else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier           = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(Modifier.width(8.dp))

                                if (selected) {
                                    Icon(Icons.Rounded.Check, null,
                                        tint     = ElectricViolet,
                                        modifier = Modifier.size(16.dp))
                                } else {
                                    Spacer(Modifier.width(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onPlayPreview("")
                    showAccentPicker = false
                },
                    colors = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
                ) { Text("Done", fontWeight = FontWeight.SemiBold) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape          = RoundedCornerShape(20.dp)
        )
    }

    // ── Voice picker dialog ───────────────────────────────────────────────────
    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            icon  = {
                Icon(Icons.Rounded.RecordVoiceOver, null,
                    tint = ElectricViolet, modifier = Modifier.size(24.dp))
            },
            title = { Text("Choose Voice") },
            text  = {
                LazyColumn(
                    modifier       = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(availableVoices) { voice ->
                        val selected = voice.name == currentVoice
                            || (voice.name.isEmpty() && currentVoice.isEmpty())
                        Surface(
                            onClick = {
                                onVoice(voice.name)
                                showVoicePicker = false
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) VioletGlow
                                    else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0f)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically
                            ) {
                                Text(
                                    voice.label,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color      = if (selected) ElectricViolet
                                                     else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (selected) FontWeight.SemiBold
                                                     else FontWeight.Normal
                                    )
                                )
                                if (selected) {
                                    Icon(Icons.Rounded.Check, null,
                                        tint     = ElectricViolet,
                                        modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showVoicePicker = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
                ) { Text("Done", fontWeight = FontWeight.SemiBold) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape          = RoundedCornerShape(20.dp)
        )
    }
}

// ── Custom AI Persona field ───────────────────────────────────────────────────

@Composable
private fun CustomPromptField(value: String, onChange: (String) -> Unit) {
    val maxChars = 500
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Custom AI Persona",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text("Replaces the default PocketShadow instructions",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            if (value.isNotBlank()) {
                TextButton(
                    onClick = { onChange("") },
                    colors  = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
                ) { Text("Reset", style = MaterialTheme.typography.labelSmall) }
            }
        }
        OutlinedTextField(
            value         = value,
            onValueChange = { if (it.length <= maxChars) onChange(it) },
            placeholder   = {
                Text(
                    "e.g. You are a coding assistant who only answers in Python and explains every line.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
            },
            modifier  = Modifier.fillMaxWidth(),
            minLines  = 3,
            maxLines  = 6,
            shape     = RoundedCornerShape(14.dp),
            textStyle = MaterialTheme.typography.bodySmall,
            colors    = OutlinedTextFieldDefaults.colors(
                focusedBorderColor      = ElectricViolet,
                unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                cursorColor             = ElectricViolet
            ),
            supportingText = {
                Text(
                    "${value.length} / $maxChars",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (value.length > maxChars * 0.9) ElectricViolet
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        )
    }
}

// ── Document paste sheet ──────────────────────────────────────────────────────
//
// Primary action: "Paste from Clipboard" button — reads clipboard directly,
// no keyboard required. The preview field is read-only until the user taps
// "Edit" if they want to trim/fix the pasted text.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentSheet(
    initialText: String,
    initialName: String?,
    excerpts: String,
    onConfirm: (String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(initialText) }
    var filename by remember { mutableStateOf(initialName) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editMode by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            busy = true
            error = null
            scope.launch {
                try {
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val name = context.contentResolver.query(uri,
                            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        } ?: "Text document"
                        val content = context.contentResolver.openInputStream(uri)?.use {
                            com.pocketshadow.app.data.repository.TextDocumentReader.read(it)
                        } ?: throw IllegalArgumentException("Could not open this document. Try another file.")
                        name to content
                    }
                    filename = result.first
                    text = result.second
                    editMode = false
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: "Could not read this file. Try a UTF-8 TXT or Markdown document."
                } finally {
                    busy = false
                }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, ElectricViolet.copy(alpha = 0.22f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(
                        ElectricViolet.copy(alpha = 0.17f),
                        MaterialTheme.colorScheme.surfaceContainer
                    ))).padding(start = 14.dp, end = 8.dp, top = 13.dp, bottom = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(shape = RoundedCornerShape(13.dp),
                        color = ElectricViolet.copy(alpha = 0.15f)) {
                        Icon(Icons.Rounded.Article, null, tint = ElectricViolet,
                            modifier = Modifier.padding(11.dp).size(23.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Add a document", style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold))
                        Text("Give your next chat something to work from",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close document") }
                }
            }
            Text("TXT or Markdown · UTF-8 · up to 1 MB",
                style = MaterialTheme.typography.bodyMedium)
            Text("Files are read on your phone. PDFs and Word files are not supported yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.AttachFile, null)
                Spacer(Modifier.width(8.dp))
                Text(if (text.isBlank()) "Choose file" else "Choose another file")
            }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val pasted = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    when {
                        pasted.isBlank() -> error = "Clipboard is empty. Copy some text first."
                        pasted.toByteArray(Charsets.UTF_8).size > com.pocketshadow.app.data.repository.TextDocumentReader.MAX_BYTES ->
                            error = "Paste less than 1 MB of text."
                        else -> { text = pasted; filename = "Pasted text"; error = null; editMode = false }
                    }
                }, modifier = Modifier.fillMaxWidth()
            ) { Text("Paste text instead") }
            if (busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Reading document…", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (text.isNotBlank() || editMode) {
                Text(filename ?: "Pasted text", style = MaterialTheme.typography.titleMedium)
                Text("${text.trim().split(Regex("\\s+")).size} words",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { editMode = !editMode }, enabled = !busy) {
                    Text(if (editMode) "Done editing" else "Edit text")
                }
                if (editMode) {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("Document text") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 8
                    )
                } else {
                Surface(shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()) {
                    Text(text.take(600) + if (text.length > 600) "\n…" else "",
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                        modifier = Modifier.padding(15.dp))
                }
                }
                Text("Answers use relevant excerpts selected for each question and cite [Excerpt N]. Selection may not cover the entire document.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (excerpts.isNotBlank() && text == initialText && initialText.isNotBlank()) {
                TextButton(onClick = { showSources = !showSources }) {
                    Text(if (showSources) "Hide sources" else "View excerpts from latest question")
                }
                if (showSources) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(excerpts, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Button(
                onClick = { onConfirm(text, filename ?: "Pasted text") },
                enabled = !busy && text.isNotBlank() &&
                    text.toByteArray(Charsets.UTF_8).size <= com.pocketshadow.app.data.repository.TextDocumentReader.MAX_BYTES,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Use document") }
        }
    }
}

// ── Model info sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelInfoSheet(
    modelInfo        : com.pocketshadow.app.data.repository.ModelInfo,
    contextWindowSize: Int,
    models           : List<ModelFile>,
    selectedPath     : String,
    onSelectModel    : (String) -> Unit,
    onDismiss        : () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle       = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("AI on this device",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Private, local, and ready when you are",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }

            // Status badge
            Surface(
                shape    = RoundedCornerShape(14.dp),
                color    = if (modelInfo.isReady) VioletGlow
                           else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        if (modelInfo.isReady) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        null,
                        tint     = if (modelInfo.isReady) ElectricViolet
                                   else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            if (modelInfo.isReady) "Ready for private chats" else "Getting your private AI ready",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color      = if (modelInfo.isReady) ElectricViolet
                                             else MaterialTheme.colorScheme.error)
                        )
                        if (modelInfo.isReady) {
                            Text("Running locally on ${modelInfo.backend}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Active model",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
            ModelPickerRow(
                models        = models,
                selectedPath  = selectedPath,
                onSelectModel = onSelectModel
            )
            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ElectricViolet.copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, ElectricViolet.copy(alpha = 0.28f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = ElectricViolet,
                            modifier = Modifier.size(22.dp))
                        Text("Private by design",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Text(
                        "Your prompts and replies are processed by the model on this phone. Chat content is not uploaded or shared with a cloud service.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrivacyFact("Offline")
                        PrivacyFact("No account")
                        PrivacyFact("No tracking")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Clear, friendly facts first; detailed engine values remain below.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = ElectricViolet, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Works without internet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text("PocketShadow runs this model on your phone; chat content does not leave it.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.TipsAndUpdates, null, tint = ElectricViolet, modifier = Modifier.size(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Storage & RAM guidance", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        Text(
                            "Keep at least 1 GB of free storage for model extraction. If loading fails, close other apps or choose the smaller model; large models need substantially more available RAM.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Technical details", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant))
            Spacer(Modifier.height(4.dp))
            // Info rows
            listOf(
                "Model path"     to modelInfo.path.substringAfterLast("/").ifBlank { modelInfo.path },
                "File size"      to if (modelInfo.fileSizeMb > 0) "${"%.1f".format(modelInfo.fileSizeMb)} MB" else "–",
                "Backend"        to modelInfo.backend,
                "Context limit"  to "${modelInfo.maxTokens} tokens",
                "Temperature"    to "${modelInfo.temperature}",
                "Top K"          to "${modelInfo.topK}",
                "Context window" to "$contextWindowSize messages"
            ).forEachIndexed { index, (label, value) ->
                if (index > 0) HorizontalDivider(
                    color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    thickness = 0.5.dp
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Text(label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text(value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold),
                        maxLines = 1)
                }
            }

            if (!modelInfo.isReady) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        "The AI model couldn't be loaded. Try restarting the app, or free up storage space and try again.",
                        style    = MaterialTheme.typography.bodySmall.copy(
                            color      = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp
                        ),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun PrivacyFact(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = ElectricViolet.copy(alpha = 0.14f)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = ElectricViolet,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}
