package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.components.FramedBorder
import main.components.audio.SoundManager
import main.components.translated
import models.*
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun SequencingGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    val targetSequence = config.items
    val poolItems = remember { config.items.shuffled() }
    val currentSequence = remember { mutableStateListOf<MinigameItem>() }
    var errorIndex by remember { mutableStateOf<Int?>(null) }

    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()
    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

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
            // Workbench
            FramedBorder(
                attrs = {
                    classes(MinigameStyles.sequencingWorkbench)
                }
            ) {
                if (currentSequence.isEmpty()) {
                    P { Text(translated("Select the items in the correct order")) }
                } else {
                    currentSequence.forEach { item ->
                        FramedBorder(
                            attrs = {
                                classes(MinigameStyles.sequencingItem)
                            }
                        ) {
                            item.image?.let { url ->
                                Img(src = url, attrs = { classes(MinigameStyles.sequencingItemImage) })
                            }
                            item.text?.let { text ->
                                P({ classes(MinigameStyles.sequencingItemText) }) { Text(text) }
                            }
                        }
                    }
                }
            }

            // Pool
            Div({
                classes(MinigameStyles.sequencingPool)
            }) {
                poolItems.forEachIndexed { index, item ->
                    val isUsed = currentSequence.any { it.id == item.id }
                    if (isUsed) return@forEachIndexed

                    val isError = errorIndex == index

                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.sequencingItem)
                            if (isError) classes(MinigameStyles.shakeError)
                            onClick {
                                if (startTime == null) startTime = window.performance.now()

                                totalAttempts++
                                val nextTarget = targetSequence[currentSequence.size]

                                if (item.id == nextTarget.id) {
                                    successCount++
                                    currentSequence.add(item)
                                    SoundManager.playItemAudio(item)

                                    if (currentSequence.size == targetSequence.size) {
                                        val endTime = window.performance.now()
                                        scope.launch {
                                            val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                            winResult = MinigameResult(
                                                minigameId = config.minigameId,
                                                type = MinigameType.Sequencing,
                                                reflex = reflex,
                                                accuracy = accuracy,
                                                completed = true
                                            )
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        errorIndex = index
                                        delay(500)
                                        errorIndex = null
                                    }
                                }
                            }
                        }
                    ) {
                        item.image?.let { url ->
                            Img(src = url, attrs = { classes(MinigameStyles.sequencingItemImage) })
                        }
                        item.text?.let { text ->
                            P({ classes(MinigameStyles.sequencingItemText) }) { Text(text) }
                        }
                        
                        val hasAudio = item.audio?.isNotBlank() == true || item.text?.isNotBlank() == true
                        if (hasAudio) {
                            Span({
                                style { fontSize(16.px); property("position", "absolute"); top(4.px); right(4.px) }
                            }) { Text("🔊") }
                        }
                    }
                }
            }
        }
    }
}
