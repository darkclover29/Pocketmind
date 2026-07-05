package com.pocketmind.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pocketmind.ui.components.*
import com.pocketmind.ui.theme.*
import com.pocketmind.ui.viewmodel.ChatViewModel
import com.pocketmind.ui.viewmodel.InferenceEngine
import kotlinx.coroutines.launch

// ── Chat screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketMindChatScreen(
    viewModel: ChatViewModel = viewModel()
) {
    val uiState   by viewModel.uiState.collectAsStateWithLifecycle()
    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()
    var showEngineSheet by remember { mutableStateOf(false) }

    // ✅ Auto-scroll check #1: fires when a new message is appended
    // ✅ Auto-scroll check #2: fires as content grows token-by-token
    val lastContent = uiState.messages.lastOrNull()?.content
    LaunchedEffect(uiState.messages.size, lastContent) {
        if (uiState.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(uiState.messages.lastIndex)
            }
        }
    }

    PocketMindTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = TrueBlack,
                topBar = {
                    ChatTopBar(
                        engine        = uiState.activeEngine,
                        sessionTitle  = uiState.sessionTitle,
                        isGenerating  = uiState.isGenerating,
                        onEngineClick = { showEngineSheet = true }
                    )
                },
                bottomBar = {
                    ChatInputArea(
                        value         = uiState.inputText,
                        onValueChange = viewModel::onInputChanged,
                        isGenerating  = uiState.isGenerating,
                        onSend        = viewModel::sendMessage,
                        onStop        = viewModel::stopGeneration
                    )
                }
            ) { padding ->
                if (uiState.messages.isEmpty()) {
                    EmptyState(modifier = Modifier.padding(padding))
                } else {
                    LazyColumn(
                        state          = listState,
                        modifier       = Modifier
                            .fillMaxSize()
                            .background(TrueBlack)
                            .padding(padding),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(
                            items = uiState.messages,
                            key   = { it.id }   // stable keys prevent full-list recomposition
                        ) { msg ->
                            ChatMessageBubble(message = msg)
                        }
                    }
                }
            }

            // Floating Side Download Badge
            if (uiState.downloadState is com.pocketmind.data.repository.DownloadState.Started || 
                uiState.downloadState is com.pocketmind.data.repository.DownloadState.Progress) {
                val progressText = when (val state = uiState.downloadState) {
                    is com.pocketmind.data.repository.DownloadState.Progress -> {
                        val mb = state.bytesDownloaded / (1024f * 1024f)
                        "%.1f MB".format(mb)
                    }
                    else -> "Prep..."
                }

                Surface(
                    onClick = { showEngineSheet = true },
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    color = AiCoreGreenGlow,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AiCoreGreen.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(72.dp)
                        .height(64.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Pulsing loading dot
                        val infiniteTransition = rememberInfiniteTransition(label = "side_pulsing_dot")
                        val pulseScale by infiniteTransition.animateFloat(
                            initialValue = 0.8f,
                            targetValue = 1.2f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse"
                        )

                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .drawBehind {
                                    drawCircle(
                                        color = AiCoreGreen,
                                        radius = size.minDimension / 2 * pulseScale,
                                        alpha = 0.8f
                                    )
                                }
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "DL NANO",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp),
                                color = White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                color = AiCoreGreen,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (showEngineSheet) {
            EngineSelectionBottomSheet(
                activeEngine = uiState.activeEngine,
                downloadState = uiState.downloadState,
                onEngineSelected = { engine ->
                    viewModel.selectEngine(engine)
                    showEngineSheet = false
                },
                onDismissRequest = { showEngineSheet = false }
            )
        }
    }
}

// ── Top app bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    engine       : InferenceEngine,
    sessionTitle : String,
    isGenerating : Boolean,
    onEngineClick: () -> Unit
) {
    TopAppBar(
        // Session title on the left
        title = {
            Text(
                text  = sessionTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
        },
        // ✅ Pill-shaped engine status indicator centred via actions slot + weight trick
        actions = {
            EngineStatusPill(
                engine       = engine,
                isGenerating = isGenerating,
                onClick      = onEngineClick
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Menu", tint = HintGray)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor    = TrueBlack,
            titleContentColor = White
        )
    )
}

// ── Engine pill ───────────────────────────────────────────────────────────────

@Composable
private fun EngineStatusPill(
    engine       : InferenceEngine,
    isGenerating : Boolean,
    onClick      : () -> Unit
) {
    val dotColor    = if (engine == InferenceEngine.AICORE) AiCoreGreen else LocalViolet
    val pillBg      = if (engine == InferenceEngine.AICORE) AiCoreGreenGlow else VioletGlow

    // Pulse the dot while generating
    val infiniteTransition = rememberInfiniteTransition(label = "pill_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue  = if (isGenerating) 0.3f else 1f,
        targetValue   = 1f,
        animationSpec = if (isGenerating)
            infiniteRepeatable(tween(700), RepeatMode.Reverse)
        else
            snap(),
        label = "dot_alpha"
    )

    Surface(
        onClick = onClick,
        shape   = RoundedCornerShape(50),
        color   = pillBg,
        modifier = Modifier.height(32.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Glowing dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(dotColor.copy(alpha = dotAlpha))
            )
            Text(
                text  = engine.label,
                style = MaterialTheme.typography.bodySmall.copy(color = White)
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = "🧠", style = MaterialTheme.typography.titleMedium.copy(
                fontSize = androidx.compose.ui.unit.TextUnit(52f, androidx.compose.ui.unit.TextUnitType.Sp)
            ))
            Text(text = "PocketMind", style = MaterialTheme.typography.titleMedium)
            Text(
                text  = "Your AI. Your device. Fully private.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewChatScreen() {
    PocketMindTheme {
        // Minimal preview without Hilt — pass a no-op lambda ViewModel preview helper
        Scaffold(
            containerColor = TrueBlack,
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(
                    title  = { Text("Python Regex Help", style = MaterialTheme.typography.titleMedium) },
                    actions = {
                        EngineStatusPill(
                            engine       = InferenceEngine.AICORE,
                            isGenerating = true,
                            onClick      = {}
                        )
                        Spacer(Modifier.width(4.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TrueBlack)
                )
            },
            bottomBar = {
                ChatInputArea("", {}, false, {}, {})
            }
        ) { padding ->
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding).background(TrueBlack),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item { ChatMessageBubble(Message(role = Role.USER, content = "How do I match emails with regex in Python?")) }
                item {
                    ChatMessageBubble(
                        Message(
                            role    = Role.AI,
                            content = "Use the **`re`** module:\n\n```python\nimport re\npattern = r'[\\w.-]+@[\\w.-]+\\.\\w+'\nmatches = re.findall(pattern, text)\n```\n\nThis matches most standard email formats."
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineSelectionBottomSheet(
    activeEngine: InferenceEngine,
    downloadState: com.pocketmind.data.repository.DownloadState,
    onEngineSelected: (InferenceEngine) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = ElevatedSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HintGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Inference Configuration",
                style = MaterialTheme.typography.titleMedium.copy(color = White)
            )

            Text(
                text = "Select which on-device engine to process your messages and run background tasks.",
                style = MaterialTheme.typography.bodySmall.copy(color = HintGray)
            )

            Spacer(Modifier.height(4.dp))

            // AICore Engine Option Card
            EngineOptionCard(
                title = "⚡ AICore (Gemini Nano)",
                description = "Google's system-managed on-device foundation model.",
                isSelected = activeEngine == InferenceEngine.AICORE,
                tasks = listOf(
                    "💬 Chat Generation: Streams private, high-quality responses.",
                    "📝 Title Generation: Summarizes conversation into short titles."
                ),
                statusContent = {
                    when (downloadState) {
                        is com.pocketmind.data.repository.DownloadState.Progress -> {
                            val mb = downloadState.bytesDownloaded / (1024f * 1024f)
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Downloading Model...", style = MaterialTheme.typography.bodySmall.copy(color = AiCoreGreen))
                                    Text("%.2f MB".format(mb), style = MaterialTheme.typography.bodySmall.copy(color = White))
                                }
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = AiCoreGreen,
                                    trackColor = TrueBlack
                                )
                            }
                        }
                        is com.pocketmind.data.repository.DownloadState.Started -> {
                            Text(
                                text = "Preparing download...",
                                style = MaterialTheme.typography.bodySmall.copy(color = AiCoreGreen),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is com.pocketmind.data.repository.DownloadState.Completed -> {
                            Text(
                                text = "✓ Model Ready",
                                style = MaterialTheme.typography.bodySmall.copy(color = AiCoreGreen),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        is com.pocketmind.data.repository.DownloadState.Failed -> {
                            Text(
                                text = "⚠ Download failed: ${downloadState.message}",
                                style = MaterialTheme.typography.bodySmall.copy(color = ErrorRed),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = "• Ready to activate",
                                style = MaterialTheme.typography.bodySmall.copy(color = HintGray),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                },
                onClick = { onEngineSelected(InferenceEngine.AICORE) }
            )

            // Local MediaPipe Engine Option Card
            EngineOptionCard(
                title = "🔷 Local Model (MediaPipe)",
                description = "Lightweight local Gemma model running entirely in-app.",
                isSelected = activeEngine == InferenceEngine.LOCAL_MEDIAPIPE,
                tasks = listOf(
                    "💬 Chat Generation: Active fallback when Gemini Nano is not ready."
                ),
                statusContent = {
                    Text(
                        text = "✓ Always Available Offline",
                        style = MaterialTheme.typography.bodySmall.copy(color = ElectricViolet),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                },
                onClick = { onEngineSelected(InferenceEngine.LOCAL_MEDIAPIPE) }
            )
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EngineOptionCard(
    title: String,
    description: String,
    isSelected: Boolean,
    tasks: List<String>,
    statusContent: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) ElectricViolet else AiChatBorder
    val bgColor = if (isSelected) AiChatSurface else TrueBlack

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = White
                    )
                )
                RadioButton(
                    selected = isSelected,
                    onClick = onClick,
                    colors = RadioButtonDefaults.colors(
                        selectedColor = ElectricViolet,
                        unselectedColor = HintGray
                    )
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(color = HintGray)
            )

            HorizontalDivider(color = AiChatBorder, thickness = 0.5.dp)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Configured Tasks:",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = White
                    )
                )
                tasks.forEach { task ->
                    Text(
                        text = task,
                        style = MaterialTheme.typography.bodySmall.copy(color = HintGray)
                    )
                }
            }

            statusContent()
        }
    }
}
