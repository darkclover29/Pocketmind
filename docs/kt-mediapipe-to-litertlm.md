# Knowledge Transfer: MediaPipe → LiteRT-LM Migration

**Date:** 2026-07-01  
**Scope:** On-device inference engine swap + related bug fixes

---

## Why

MediaPipe (`com.google.mediapipe:tasks-genai`) was the original inference backend. It was replaced by **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`) which supports the `.litertlm` model format and provides a cleaner Kotlin coroutines-native API.

---

## Files Changed

### 1. `gradle/libs.versions.toml`
- Removed `mediapipe = "0.10.35"` and `mediapipe-tasks-genai` library entry.
- Added `litertlm = "0.13.1"` and:
  ```toml
  litertlm-android = { group = "com.google.ai.edge.litertlm", name = "litertlm-android", version.ref = "litertlm" }
  ```

### 2. `app/build.gradle.kts`
- Replaced `implementation(libs.mediapipe.tasks.genai)` with `implementation(libs.litertlm.android)`.

### 3. `app/.../data/repository/LlmRepository.kt` ⬅ biggest change
Complete rewrite. Key differences:

| Before (MediaPipe) | After (LiteRT-LM) |
|---|---|
| `LlmInference` | `Engine` + `EngineConfig` |
| Manual `<start_of_turn>` prompt templating | `ConversationConfig(systemInstruction, initialMessages)` handles it |
| Hardcoded path `/data/local/tmp/llm/model.bin` | `resolveModelPath()` tries user selection, then `.litertlm`, then `.bin` fallbacks |
| No model discovery | `scanAvailableModels()` scans 3 directories for `.litertlm` / `.bin` files |
| No `ModelFile` type | `data class ModelFile(path, filename, displayName, sizeMb)` added |
| `ModelInfo` had no display name | `ModelInfo.displayName` populated from `resolveDisplayName(filename)` |
| No `resetModel()` | `resetModel()` calls `engine?.close()` so next send reloads the newly selected model |
| Streaming via MediaPipe callback | `conversation.sendMessageAsync(q).collect { }` (coroutines Flow) |

**Inference flow:**
```
streamResponse() → getOrCreateEngine() → Engine(EngineConfig(path, GPU or CPU, cacheDir))
  → engine.createConversation(ConversationConfig(systemInstruction, initialMessages))
  → conversation.sendMessageAsync(userQuestion).collect { emit(delta) }
```
GPU is tried first; falls back to CPU automatically.

### 4. `app/.../ui/viewmodel/ChatViewModel.kt`
- `loadSession()` fixed: was calling `historyRepo.getMessagesForSession()` (DAO-only Flow) — changed to `historyRepo.loadSessionMessages()` (Repository suspend fun returning `List`).
- Entity field reference fixed: `e.id` → `e.messageId` (matches `ChatMessageEntity` primary key).

### 5. `app/.../ui/screens/PocketMindChatScreen.kt`
- Help text in `ModelInfoSheet` updated from `model.bin` references to `model.litertlm`.

---

## Model File Setup (unchanged workflow, new extension)

```bash
adb push your-model.litertlm /data/local/tmp/llm/
```

The auto-detected default is `gemma-4-E4B-it.litertlm`. Any `.litertlm` or `.bin` file placed in `/data/local/tmp/llm/` will appear in the model picker.

---

## What to Watch For

- `EngineConfig` constructor signature may differ across LiteRT-LM patch versions — check `modelPath`, `backend`, `cacheDir` parameter names if you upgrade past `0.13.1`.
- `conversation.sendMessageAsync()` emits accumulated text on some builds and deltas on others — the `removePrefix(lastText)` pattern in `streamResponse()` handles both.
- `Engine` is `Closeable` — always call `engine?.close()` in `resetModel()` and when the app closes (the `@Singleton` scope means this happens at process death, which is fine for now).
