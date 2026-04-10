# Saigon.Quest Language Learning Implementation Status

This document tracks the progress of the 3-part language learning enhancement plan.

## Part 1: Infrastructure (Completed ✓)
- [x] **New Node IDs**: Added `AiImage`, `AiTts`, `Minigame`, and `JsonExtract` to `NodeId.kt`.
- [x] **Logic Node Models**: Implemented data classes for the new nodes in `LogicNode.kt`.
- [x] **AI Client Extensions**:
    - Added `generateImage(prompt)` and `ttsUrl(text)` to `AiClient.kt`.
    - Implemented `generateImage` in `AlibabaAiClient.kt` using Wanx-v1 (asynchronous polling).
- [x] **Minigame Models**: Created `MinigameModels.kt` to define `MinigameType`, `MinigameItem`, `MinigameConfig`, and `MinigameResult`.
- [x] **WebSocket Actions**: Added `MINIGAME_PROMPT` and `MINIGAME_RESULT` to `WebSocketAction.kt`.

## Part 2: Generate Lessons - Workflow (Completed ✓)
- [x] **AiImageNodeRunner**: Runner for generating AI images from prompts. ✓
- [x] **AiTtsNodeRunner**: Runner for generating AI TTS audio from text. ✓
- [x] **JsonExtractNodeRunner**: Utility for extracting specific fields from AI-generated JSON. ✓
- [x] **MinigameNodeRunner**: Runner for triggering interactive minigames on the client. ✓
- [x] **Node Registration**: Integrating the new runners into the node execution engine. ✓
- [x] **Persistent Asset URLs**: Implemented local `/gen` static serving and `saveGenFile` in AI clients. ✓

## Part 3: Minigames - UI (Completed ✓)
- [x] **MinigameOverlay**: New @Composable for rendering minigames. ✓
- [x] **Flashcards UI**: Implementation of the flashcard flip game. ✓
- [x] **MatchGame UI**: Implementation of the "Clear the field" matching game. ✓
- [x] **Scenario UI**: Implementation of the hotspot selection game. ✓
- [x] **Result Tracking**: Frontend logic to calculate and return `reflex` and `accuracy` scores. ✓
