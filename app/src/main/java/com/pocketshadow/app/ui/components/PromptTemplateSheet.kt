package com.pocketshadow.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketshadow.app.ui.theme.ElectricViolet
import com.pocketshadow.app.ui.theme.PocketShadowMotion
import com.pocketshadow.app.ui.theme.VioletGlow

enum class PromptTemplateKind { CODING, WRITING, LEARNING, PLANNING, SUMMARIZING, BRAINSTORMING }

data class PromptTemplate(
    val kind: PromptTemplateKind,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconColor: Color
)

val promptTemplates = listOf(
    PromptTemplate(PromptTemplateKind.CODING, "Coding", "Write, debug, or explain code", Icons.Rounded.Code, Color(0xFF66D17A)),
    PromptTemplate(PromptTemplateKind.WRITING, "Writing", "Draft something clear and polished", Icons.Rounded.Edit, ElectricViolet),
    PromptTemplate(PromptTemplateKind.LEARNING, "Learning", "Understand a topic step by step", Icons.Rounded.School, Color(0xFF65A9FF)),
    PromptTemplate(PromptTemplateKind.PLANNING, "Planning", "Turn a goal into an actionable plan", Icons.Rounded.EventNote, Color(0xFFC084FC)),
    PromptTemplate(PromptTemplateKind.SUMMARIZING, "Summarizing", "Extract the important points", Icons.Rounded.Summarize, Color(0xFF38C7C0)),
    PromptTemplate(PromptTemplateKind.BRAINSTORMING, "Brainstorming", "Generate and explore fresh ideas", Icons.Rounded.AutoAwesome, Color(0xFFF472B6))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptTemplateSheet(
    template: PromptTemplate,
    onUse: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val values = remember(template.kind) { mutableStateMapOf<String, String>() }
    var selectedOption by remember(template.kind) { mutableStateOf(defaultOption(template.kind)) }

    fun value(key: String) = values[key].orEmpty()
    fun setValue(key: String, text: String) { values[key] = text }

    val requiredKey = when (template.kind) {
        PromptTemplateKind.SUMMARIZING -> "text"
        PromptTemplateKind.CODING -> "task"
        PromptTemplateKind.WRITING -> "request"
        PromptTemplateKind.LEARNING -> "topic"
        PromptTemplateKind.PLANNING -> "goal"
        PromptTemplateKind.BRAINSTORMING -> "topic"
    }
    val canUse = value(requiredKey).isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .animateContentSize(animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ))
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = template.iconColor.copy(alpha = 0.14f)
                ) {
                    Icon(
                        template.icon,
                        contentDescription = null,
                        tint = template.iconColor,
                        modifier = Modifier.padding(10.dp).size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(template.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        template.description,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Close, "Close template", modifier = Modifier.size(20.dp))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (template.kind) {
                    PromptTemplateKind.CODING -> {
                        TemplateField("What do you want to build or fix?", value("task"), { setValue("task", it) }, "Describe the task, bug, or code you want help with.")
                        TemplateField("Language or framework", value("language"), { setValue("language", it) }, "Optional", singleLine = true)
                        TemplateField("Context and constraints", value("context"), { setValue("context", it) }, "Optional: errors, APIs, style rules, or target platform.")
                        TemplateOptions("Mode", listOf("Write code", "Debug", "Explain"), selectedOption) { selectedOption = it }
                    }
                    PromptTemplateKind.WRITING -> {
                        TemplateField("What should be written?", value("request"), { setValue("request", it) }, "Example: a launch email for a new feature.")
                        TemplateField("Audience", value("audience"), { setValue("audience", it) }, "Optional", singleLine = true)
                        TemplateOptions("Tone", listOf("Clear", "Friendly", "Professional", "Persuasive"), selectedOption) { selectedOption = it }
                    }
                    PromptTemplateKind.LEARNING -> {
                        TemplateField("What do you want to learn?", value("topic"), { setValue("topic", it) }, "Example: how neural networks learn.")
                        TemplateOptions("Your level", listOf("Beginner", "Intermediate", "Advanced"), selectedOption) { selectedOption = it }
                        TemplateField("What should you be able to do?", value("goal"), { setValue("goal", it) }, "Optional learning goal or question.")
                    }
                    PromptTemplateKind.PLANNING -> {
                        TemplateField("What are you planning?", value("goal"), { setValue("goal", it) }, "Example: a two-week study plan for exams.")
                        TemplateField("Timeframe", value("timeframe"), { setValue("timeframe", it) }, "Optional", singleLine = true)
                        TemplateField("Constraints or preferences", value("constraints"), { setValue("constraints", it) }, "Optional: budget, schedule, tools, or priorities.")
                    }
                    PromptTemplateKind.SUMMARIZING -> {
                        TemplateField("Paste the text to summarize", value("text"), { setValue("text", it) }, "The text stays on this device.", minLines = 5, maxLines = 9)
                        TemplateOptions("Format", listOf("Brief", "Key points", "Detailed"), selectedOption) { selectedOption = it }
                    }
                    PromptTemplateKind.BRAINSTORMING -> {
                        TemplateField("What should we brainstorm?", value("topic"), { setValue("topic", it) }, "Example: ideas for a healthier lunch routine.")
                        TemplateOptions("Number of ideas", listOf("5", "10", "20"), selectedOption) { selectedOption = it }
                        TemplateField("Audience or constraints", value("constraints"), { setValue("constraints", it) }, "Optional", singleLine = false)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onUse(buildPrompt(template.kind, values, selectedOption)) },
                enabled = canUse,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricViolet,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("Use template", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TemplateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supportingText: String,
    singleLine: Boolean = false,
    minLines: Int = 2,
    maxLines: Int = 5
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = { Text(supportingText) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        maxLines = if (singleLine) 1 else maxLines,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = if (label.contains("text", ignoreCase = true)) KeyboardType.Text else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ElectricViolet,
            cursorColor = ElectricViolet,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun TemplateOptions(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricViolet,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = option == selected,
                        selectedBorderColor = ElectricViolet,
                        selectedBorderWidth = 0.dp,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        borderWidth = 0.5.dp
                    )
                )
            }
        }
    }
}

private fun defaultOption(kind: PromptTemplateKind): String = when (kind) {
    PromptTemplateKind.CODING -> "Write code"
    PromptTemplateKind.WRITING -> "Clear"
    PromptTemplateKind.LEARNING -> "Beginner"
    PromptTemplateKind.PLANNING -> ""
    PromptTemplateKind.SUMMARIZING -> "Brief"
    PromptTemplateKind.BRAINSTORMING -> "5"
}

private fun buildPrompt(
    kind: PromptTemplateKind,
    values: Map<String, String>,
    option: String
): String {
    fun v(key: String) = values[key].orEmpty().trim()
    fun optional(label: String, value: String) = if (value.isBlank()) "" else " $label: $value."

    return when (kind) {
        PromptTemplateKind.CODING -> "${option}. ${v("task")}." +
            optional("Language or framework", v("language")) + optional("Context", v("context")) +
            " Include a concise explanation and point out important trade-offs."
        PromptTemplateKind.WRITING -> "Write ${v("request")}." +
            optional("Audience", v("audience")) + " Tone: $option. Make it clear, natural, and ready to use."
        PromptTemplateKind.LEARNING -> "Teach me about ${v("topic")} as a $option learner." +
            optional("Learning goal", v("goal")) + " Explain it step by step, use a simple example, and finish with a short recap."
        PromptTemplateKind.PLANNING -> "Create a practical plan for ${v("goal")}." +
            optional("Timeframe", v("timeframe")) + optional("Constraints", v("constraints")) +
            " Break it into prioritized steps, milestones, and a realistic first action."
        PromptTemplateKind.SUMMARIZING -> "Summarize the following text in a $option format. Preserve the important facts and do not invent details:\n\n${v("text")}"
        PromptTemplateKind.BRAINSTORMING -> "Generate $option original ideas for ${v("topic")}." +
            optional("Audience or constraints", v("constraints")) +
            " For each idea, include a short description and why it could work."
    }
}
