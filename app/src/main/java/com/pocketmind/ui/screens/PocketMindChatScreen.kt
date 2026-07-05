package com.pocketmind.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import com.pocketmind.BuildConfig
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketmind.data.db.entity.ChatSessionEntity
import com.pocketmind.data.repository.FontScale
import com.pocketmind.data.repository.ModelFile
import com.pocketmind.data.repository.ModelInfo
import com.pocketmind.ui.components.*
import com.pocketmind.ui.theme.*
import com.pocketmind.ui.viewmodel.ChatViewModel
import com.pocketmind.ui.viewmodel.MainViewModel
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
    return listOf(VoiceOption("", "System Default")) + best.map { v ->
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
fun PocketMindChatScreen(
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
    val haptic      = LocalHapticFeedback.current
    var showModelPickerDialog by remember { mutableStateOf(false) }

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
            android.util.Log.e("PocketMind", "Error setting language/accent: ${e.message}")
        }

        if (uiState.ttsVoiceName.isBlank()) {
            engine.voice = engine.defaultVoice
        } else {
            engine.voices?.firstOrNull { it.name == uiState.ttsVoiceName }
                ?.let { engine.voice = it }
        }
    }

    // Strips markdown + emoji then speaks — no asterisks, no "fire emoji" aloud
    val readAloud: (String) -> Unit = { raw ->
        if (ttsReady.value) {
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
                previewPlayingAccent.value = code
                engine.speak(
                    "Hi, I'm PocketMind — your private, on-device assistant.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "preview_$code"
                )
                // Locale is restored when the dialog is dismissed via the existing onDismiss handler
            }
        }
    }

    val fontScale = when (uiState.fontSize) {
        FontScale.SMALL  -> 0.88f
        FontScale.NORMAL -> 1.00f
        FontScale.LARGE  -> 1.18f
    }

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

    PocketMindTheme(themeMode = uiState.themeMode, fontScale = fontScale) {
        ModalNavigationDrawer(
            drawerState   = drawerState,
            drawerContent = {
                HistoryDrawer(
                    sessions        = uiState.sessions,
                    hapticEnabled   = uiState.hapticFeedback,
                    onSessionClick  = { session ->
                        viewModel.loadSession(session)
                        scope.launch { drawerState.close() }
                    },
                    onSessionDelete = { viewModel.deleteSession(it.sessionId) },
                    onSessionRename = { session, newTitle ->
                        viewModel.renameSession(session.sessionId, newTitle)
                    },
                    onNewChat       = {
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
                        onDrawerClick = { scope.launch { drawerState.open() } },
                        onNewChat     = { viewModel.newChat() },
                        onSettings    = { viewModel.showSettings() },
                        onModelInfo   = { viewModel.showModelInfo() }
                    )
                },
                bottomBar = {
                    ChatInputArea(
                        value          = uiState.inputText,
                        onValueChange  = viewModel::onInputChanged,
                        isGenerating   = uiState.isGenerating,
                        onSend         = {
                            if (uiState.hapticFeedback) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            viewModel.sendMessage()
                        },
                        onStop         = viewModel::stopGeneration,
                        documentActive = uiState.documentContext != null,
                        documentWords  = remember(uiState.documentContext) {
                            uiState.documentContext?.trim()
                                ?.split(Regex("\\s+"))?.size ?: 0
                        },
                        onDocumentClear = viewModel::clearDocumentContext,
                        onDocumentClick = viewModel::showDocumentSheet,
                        isEmpty         = uiState.messages.isEmpty()
                    )
                }
            ) { padding ->
                if (uiState.messages.isEmpty() && !uiState.isGenerating) {
                    EmptyState(
                        models          = uiState.availableModels,
                        selectedPath    = uiState.selectedModelPath,
                        sessions        = uiState.sessions,
                        modifier        = Modifier.padding(padding),
                        onPrompt        = { prompt -> viewModel.onInputChanged(prompt) },
                        onModelClick    = { showModelPickerDialog = true },
                        onSessionClick  = { session -> viewModel.loadSession(session) }
                    )
                } else {
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        LazyColumn(
                            state          = listState,
                            modifier       = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(uiState.messages, key = { it.id }) { msg ->
                                ChatMessageBubble(
                                    message       = msg,
                                    hapticEnabled = uiState.hapticFeedback,
                                    onRetry       = if (msg.id == uiState.retryMessageId)
                                                      viewModel::retryGeneration else null,
                                    onReadAloud = if (msg.role == Role.AI)
                                                      ({ readAloud(msg.content) }) else null
                                )
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
            }
        }

        if (uiState.showSettings) {
            SettingsBottomSheet(
                uiState               = uiState,
                onThemeMode           = viewModel::setThemeMode,
                onFontSize            = viewModel::setFontSize,
                onHapticFeedback      = viewModel::setHapticFeedback,
                onAutoScroll          = viewModel::setAutoScroll,
                onSaveHistory         = viewModel::setSaveHistory,
                onContextWindowSize   = viewModel::setContextWindowSize,
                onCustomSystemPrompt  = viewModel::setCustomSystemPrompt,
                onModelInfo           = viewModel::showModelInfo,
                availableVoices       = availableVoices.value,
                onTtsVoice            = viewModel::setTtsVoiceName,
                ttsAccent             = uiState.ttsAccent,
                onTtsAccent           = viewModel::setTtsAccent,
                onTtsSpeechRate       = viewModel::setTtsSpeechRate,
                onTtsPitch            = viewModel::setTtsPitch,
                previewPlayingAccent  = previewPlayingAccent.value,
                onPlayPreview         = playPreview,
                onSelectModel         = viewModel::setSelectedModel,
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
            DocumentSheet(
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
    }
}

// ── PocketMind amber neural logo mark ─────────────────────────────────────────
//
// Canvas-drawn so it works at any size with no extra drawable files.
// Three nodes at 120° form an equilateral triangle; organic bezier dendrites
// radiate from a glowing center node; a pocket arc connects the lower two.

@Composable
fun PocketMindLogoMark(
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
    onSessionClick  : (ChatSessionEntity) -> Unit,
    onSessionDelete : (ChatSessionEntity) -> Unit,
    onSessionRename : (ChatSessionEntity, String) -> Unit,
    onNewChat       : () -> Unit
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier             = Modifier.width(300.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PocketMindLogoMark(size = 32f)
                Column {
                    Text("PocketMind", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Chat History",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
            FilledIconButton(
                onClick  = onNewChat,
                colors   = IconButtonDefaults.filledIconButtonColors(
                    containerColor = ElectricViolet, contentColor = White),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Rounded.Add, "New Chat", modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 0.5.dp)
        Spacer(Modifier.height(8.dp))

        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), Alignment.Center) {
                Text(
                    "No conversations yet",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                )
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
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                    .padding(end = 20.dp),
                Alignment.CenterEnd
            ) {
                Icon(Icons.Rounded.Delete, "Delete",
                    tint     = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp))
            }
        }
    ) {
        Surface(
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
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
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp)
                    )
                }
                // Rename hint icon — shows the user long-press is available
                Icon(Icons.Rounded.Edit, "Rename",
                    tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp))
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
                PocketMindLogoMark(size = 26f)
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

private val STARTER_PROMPTS = listOf(
    "✍️ Write"      to "Write a short, friendly email to ",
    "💡 Explain"    to "Explain in simple terms: ",
    "🧠 Brainstorm" to "Brainstorm 5 creative ideas for ",
    "🌐 Translate"  to "Translate this to English: "
)

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(
    models          : List<ModelFile>,
    selectedPath    : String,
    sessions        : List<ChatSessionEntity>,
    modifier        : Modifier = Modifier,
    onPrompt        : (String) -> Unit = {},
    onModelClick    : () -> Unit = {},
    onSessionClick  : (ChatSessionEntity) -> Unit = {}
) {
    val subtitle = "Your private, on-device AI. Fully local, fully secure."

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier            = Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Logo & Greeting Header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PocketMindLogoMark(size = 72f)
                Text(
                    text = "PocketMind",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Active Model Pill Badge
                val currentName = models.firstOrNull { it.path == selectedPath }?.displayName
                    ?: if (selectedPath.isBlank()) "Gemma 2B (Default)" else "Unknown Model"

                Surface(
                    onClick = onModelClick,
                    shape   = RoundedCornerShape(50),
                    color   = VioletGlow.copy(alpha = 0.5f),
                    border  = androidx.compose.foundation.BorderStroke(
                        0.5.dp, ElectricViolet.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier              = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = null,
                            tint = ElectricViolet,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = currentName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ElectricViolet,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Recent Chats section
            if (sessions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text  = "Recent Chats",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        sessions.take(4).forEach { session ->
                            Surface(
                                onClick = { onSessionClick(session) },
                                shape   = RoundedCornerShape(16.dp),
                                color   = MaterialTheme.colorScheme.surfaceContainer,
                                border  = androidx.compose.foundation.BorderStroke(
                                    1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.width(160.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(ElectricViolet.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Forum,
                                            contentDescription = null,
                                            tint = ElectricViolet,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text     = session.title,
                                            style    = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text  = formatSessionDate(session.createdAt),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Feature Prompt Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text  = "Explore & Create",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        title       = "Code Helper",
                        description = "Write code, debug errors, or explain syntax.",
                        icon        = Icons.Rounded.Code,
                        iconColor   = Color(0xFF4CAF50),
                        modifier    = Modifier.weight(1f),
                        onClick     = { onPrompt("Write a Kotlin function to ") }
                    )
                    FeatureCard(
                        title       = "Concept Explainer",
                        description = "Explain complex topics or snippets simply.",
                        icon        = Icons.Rounded.Lightbulb,
                        iconColor   = Color(0xFFFFB300),
                        modifier    = Modifier.weight(1f),
                        onClick     = { onPrompt("Explain in simple terms: ") }
                    )
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FeatureCard(
                        title       = "Creative Writer",
                        description = "Draft emails, essays, summaries, or captions.",
                        icon        = Icons.Rounded.Edit,
                        iconColor   = ElectricViolet,
                        modifier    = Modifier.weight(1f),
                        onClick     = { onPrompt("Write a short, friendly email to ") }
                    )
                    FeatureCard(
                        title       = "Brainstorm",
                        description = "Generate outlines, concepts, topics, or ideas.",
                        icon        = Icons.Rounded.AutoAwesome,
                        iconColor   = Color(0xFFE91E63),
                        modifier    = Modifier.weight(1f),
                        onClick     = { onPrompt("Brainstorm 5 creative ideas for ") }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
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
    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(16.dp),
        color   = MaterialTheme.colorScheme.surfaceContainer,
        border  = androidx.compose.foundation.BorderStroke(
            0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text  = description,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 14.sp
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
// Collecting streamingContent here — NOT at PocketMindChatScreen level — means
// only this composable rerenders per token. The LazyColumn, TopBar, and
// InputArea are completely unaffected during streaming.

@Composable
private fun StreamingBubble(
    id          : String,
    contentFlow : kotlinx.coroutines.flow.StateFlow<String>
) {
    val content by contentFlow.collectAsStateWithLifecycle()
    ChatMessageBubble(
        message = Message(
            id          = id,
            role        = Role.AI,
            content     = content,
            isStreaming = true
        )
    )
}

// ── Settings bottom sheet ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    uiState              : com.pocketmind.ui.viewmodel.ChatUiState,
    onThemeMode          : (ThemeMode) -> Unit,
    onFontSize           : (FontScale) -> Unit,
    onHapticFeedback     : (Boolean) -> Unit,
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
                Text("Settings",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, "Close",
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingsSectionHeader("Active Model")
            Spacer(Modifier.height(8.dp))
            ModelPickerRow(
                models        = uiState.availableModels,
                selectedPath  = uiState.selectedModelPath,
                onSelectModel = onSelectModel
            )
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("Appearance")
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

            SettingsSectionHeader("Behaviour")
            Spacer(Modifier.height(4.dp))
            SettingsSwitchRow("Haptic Feedback", "Vibrate on send and long-press",
                uiState.hapticFeedback, onHapticFeedback)
            SettingsSwitchRow("Auto-scroll", "Keep latest message in view",
                uiState.autoScroll, onAutoScroll)
            Spacer(Modifier.height(24.dp))

            SettingsSectionHeader("AI")
            Spacer(Modifier.height(4.dp))
            SettingsSwitchRow("Save History", "Store conversations locally",
                uiState.saveHistory, onSaveHistory)
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
            SettingsSectionHeader("Voice & Speech")
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
            SettingsSectionHeader("About")
            Spacer(Modifier.height(12.dp))
            AboutSection(onModelInfo = onModelInfo)
        }
    }
}

@Composable
private fun AboutSection(onModelInfo: () -> Unit = {}) {
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
                PocketMindLogoMark(size = 22f)
                Text("PocketMind",
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
                    "AI responses may be inaccurate. Do not rely on PocketMind for medical, legal, financial, or safety-critical decisions.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color      = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }

        // Privacy note
        Text(
            "🔒 All conversations are processed and stored locally on this device only. Nothing is ever sent to any server.",
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
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall.copy(
            color         = ElectricViolet,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
    )
    Spacer(Modifier.height(2.dp))
    HorizontalDivider(
        color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        thickness = 0.5.dp)
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

@Composable
private fun ThemePickerRow(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            ThemeSwatchChip(
                mode     = mode,
                selected = current == mode,
                onClick  = { onSelect(mode) },
                modifier = Modifier.weight(1f)
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
            .padding(vertical = 10.dp, horizontal = 6.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Context Window", style = MaterialTheme.typography.bodyLarge)
                Text("Past messages the AI can see",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium.copy(
                    color      = ElectricViolet,
                    fontWeight = FontWeight.Bold)
            )
        }
        Slider(
            value         = value.toFloat(),
            onValueChange = { onValue(it.roundToInt()) },
            valueRange    = 4f..20f,
            steps         = 3,
            colors        = SliderDefaults.colors(
                thumbColor         = ElectricViolet,
                activeTrackColor   = ElectricViolet,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer,
                activeTickColor    = White,
                inactiveTickColor  = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("4", "8", "12", "16", "20").forEach { label ->
                Text(label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
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
        shape   = RoundedCornerShape(12.dp),
        color   = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Active Model", style = MaterialTheme.typography.bodyLarge)
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
                                    modifier = Modifier.size(36.dp)
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
                Text("Replaces the default PocketMind instructions",
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
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context     = LocalContext.current
    var text        by remember { mutableStateOf("") }
    var editMode    by remember { mutableStateOf(false) }
    var pasteError  by remember { mutableStateOf(false) }

    fun pasteFromClipboard() {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val pasted = clip?.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
        if (pasted.isBlank()) {
            pasteError = true
        } else {
            text       = pasted
            pasteError = false
            editMode   = false
        }
    }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column {
                    Text("Load Document",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("Ask the AI questions about any text",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp))
                }
            }

            // Primary action — no keyboard needed
            Button(
                onClick  = { pasteFromClipboard() },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor   = White
                )
            ) {
                Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (text.isBlank()) "Paste from Clipboard" else "Re-paste from Clipboard",
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (pasteError) {
                Text(
                    "Clipboard is empty — copy your text first, then tap Paste.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.error)
                )
            }

            // Preview / edit area — shown once text is loaded
            if (text.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        val words = text.trim().split("\\s+".toRegex()).size
                        Text(
                            "$words words · ${text.length} chars",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        TextButton(
                            onClick = { editMode = !editMode },
                            colors  = ButtonDefaults.textButtonColors(contentColor = ElectricViolet)
                        ) {
                            Icon(
                                if (editMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                null, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (editMode) "Done" else "Edit",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    if (editMode) {
                        // Full editable field — keyboard appears only when user explicitly taps Edit
                        OutlinedTextField(
                            value         = text,
                            onValueChange = { text = it },
                            modifier      = Modifier.fillMaxWidth(),
                            minLines      = 6,
                            maxLines      = 12,
                            shape         = RoundedCornerShape(14.dp),
                            textStyle     = MaterialTheme.typography.bodySmall,
                            colors        = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor      = ElectricViolet,
                                unfocusedBorderColor    = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedContainerColor   = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                cursorColor             = ElectricViolet
                            )
                        )
                    } else {
                        // Read-only preview — no keyboard
                        Surface(
                            shape    = RoundedCornerShape(14.dp),
                            color    = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text     = text.take(600) + if (text.length > 600) "\n…" else "",
                                style    = MaterialTheme.typography.bodySmall.copy(
                                    color      = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                ),
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                // Load button
                Button(
                    onClick  = { onConfirm(text.ifBlank { null }) },
                    enabled  = text.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = ElectricViolet,
                        contentColor   = White
                    )
                ) {
                    Icon(Icons.Rounded.Article, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Load Document", fontWeight = FontWeight.SemiBold)
                }
            }

            // Cancel
            if (text.isBlank()) {
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(14.dp)
                ) { Text("Cancel") }
            }
        }
    }
}

// ── Model info sheet ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelInfoSheet(
    modelInfo        : com.pocketmind.data.repository.ModelInfo,
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
                Text("Model Info",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
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
                            modelInfo.statusText,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                color      = if (modelInfo.isReady) ElectricViolet
                                             else MaterialTheme.colorScheme.error)
                        )
                        if (modelInfo.isReady) {
                            Text("Running on ${modelInfo.backend}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Text("Switch Active Model",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(8.dp))
            ModelPickerRow(
                models        = models,
                selectedPath  = selectedPath,
                onSelectModel = onSelectModel
            )
            Spacer(Modifier.height(20.dp))

            // Info rows
            listOf(
                "Model path"     to modelInfo.path.substringAfterLast("/").ifBlank { modelInfo.path },
                "File size"      to if (modelInfo.fileSizeMb > 0) "${"%.1f".format(modelInfo.fileSizeMb)} MB" else "–",
                "Backend"        to modelInfo.backend,
                "Max tokens"     to "${modelInfo.maxTokens}",
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
