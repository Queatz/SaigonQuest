package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.components.FramedBorder
import main.components.audio.SoundManager
import main.components.audio.TtsManager
import main.components.translated
import models.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun IdentifyGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    val items = remember { config.items.shuffled() }
    val identifiedItems = remember { mutableStateListOf<String>() } // IDs of correctly identified items
    
    val currentIndex = remember { derivedStateOf { items.indexOfFirst { it.id !in identifiedItems } } }
    val currentTarget = remember { derivedStateOf { if (currentIndex.value != -1) items[currentIndex.value] else null } }

    val options = remember(currentTarget.value) {
        val target = currentTarget.value ?: return@remember emptyList()
        val distractors = items.filter { it.id != target.id }.shuffled().take(3)
        (distractors + target).shuffled()
    }

    var errorId by remember { mutableStateOf<String?>(null) }
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

    // Auto-play audio when target changes
    LaunchedEffect(currentTarget.value) {
        currentTarget.value?.let { target ->
            delay(500)
            SoundManager.playItemAudio(target)
        }
    }

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    Div({
        classes(MinigameStyles.gameContainer)
    }) {
        Div({
            classes(MinigameStyles.gameContentWrapper)
        }) {
            // Instruction
            P({
                style {
                    fontSize(24.px)
                    color(Color.white)
                    textAlign("center")
                    marginBottom(40.px)
                }
            }) {
                Text(translated("Listen and identify the correct item"))
            }

            // Prompt (Audio Button)
            Div({
                classes(MinigameStyles.identifyPromptContainer)
            }) {
                currentTarget.value?.let { target ->
                    Div({
                        classes(MinigameStyles.identifyAudioButton)
                        onClick {
                            target.let { SoundManager.playItemAudio(it) }
                        }
                    }) {
                        Text("🔊")
                    }
                    P({
                        style { color(Color.white); opacity(0.6); fontSize(16.px) }
                    }) {
                        Text(translated("Click to play again"))
                    }
                }
            }

            // Options Grid
            Div({
                classes(MinigameStyles.identifyOptionsGrid)
            }) {
                options.forEach { option ->
                    val isError = errorId == option.id
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.identifyOption)
                            if (isError) classes(MinigameStyles.shakeError)
                            onClick {
                                if (startTime == null) startTime = window.performance.now()
                                
                                val target = currentTarget.value ?: return@onClick
                                totalAttempts++
                                
                                if (option.id == target.id) {
                                    successCount++
                                    identifiedItems.add(target.id)
                                    
                                    if (identifiedItems.size == items.size) {
                                        val endTime = window.performance.now()
                                        scope.launch {
                                            val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                            winResult = MinigameResult(
                                                minigameId = config.minigameId,
                                                type = MinigameType.Identify,
                                                reflex = reflex,
                                                accuracy = accuracy,
                                                completed = true
                                            )
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        errorId = option.id
                                        delay(500)
                                        errorId = null
                                    }
                                }
                            }
                        }
                    ) {
                        option.image?.let { url ->
                            Img(src = url, attrs = { classes(MinigameStyles.identifyOptionImage) })
                        }
                        option.text?.let { text ->
                            P({ classes(MinigameStyles.identifyOptionText) }) { Text(text) }
                        }
                    }
                }
            }
        }
    }
}
