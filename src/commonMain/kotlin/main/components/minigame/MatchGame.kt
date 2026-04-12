package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import main.api.*
import main.components.audio.SoundManager
import main.theme.Theme
import models.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import main.theme.Styles
import main.components.translated
import main.components.FramedBorder
import kotlin.random.Random

@Composable
fun MatchGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    val shuffledItems = remember { config.items.shuffled() }
    val matchedIds = remember { mutableStateListOf<String>() }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var errorIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }

    var winResult by remember { mutableStateOf<MinigameResult?>(null) }
    val scope = rememberCoroutineScope()

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
            FramedBorder(
                attrs = {
                    style {
                        padding(8.px, 16.px)
                        marginBottom(24.px)
                    }
                }
            ) {
                Text("${matchedIds.size / 2} / ${config.items.size / 2} ${translated("pairs matched")}")
            }

            Div({
                classes(MinigameStyles.matchGrid)
            }) {
                shuffledItems.forEach { item ->
                    val isMatched = matchedIds.contains(item.id)
                    val isSelected = selectedId == item.id
                    val isError = errorIds.contains(item.id)
                    
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.matchItem)
                            if (isSelected) classes(MinigameStyles.selectedMatchItem)
                            if (isMatched) classes(MinigameStyles.matchedMatchItem)
                            if (isError) classes(MinigameStyles.shakeError)
                            
                            onClick {
                                if (isMatched || isError) return@onClick
                                
                                if (startTime == null) startTime = window.performance.now()
                                
                                if (isSelected) {
                                    selectedId = null
                                } else if (selectedId == null) {
                                    selectedId = item.id
                                    // Play audio if available
                                    item.audio?.let { SoundManager.playSound(it) }
                                } else {
                                    totalAttempts++
                                    val firstItem = shuffledItems.find { it.id == selectedId }
                                    if (firstItem != null && firstItem.matchId == item.matchId) {
                                        // Match!
                                        matchedIds.add(firstItem.id)
                                        matchedIds.add(item.id)
                                        successCount++
                                        selectedId = null
                                        
                                        // Check if complete
                                        if (matchedIds.size == shuffledItems.size) {
                                            val endTime = window.performance.now()
                                            // Each attempt in MatchGame involves 2 items, so we double the count for reflex consistency with other games
                                            val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                            val perItemReflex = reflex / 2.0
                                            
                                            scope.launch {
                                                delay(1000)
                                                winResult = MinigameResult(
                                                    minigameId = config.minigameId,
                                                    type = MinigameType.MatchGame,
                                                    reflex = perItemReflex,
                                                    accuracy = accuracy,
                                                    completed = true
                                                )
                                            }
                                        }
                                    } else {
                                        // No match
                                        val prevId = selectedId
                                        scope.launch {
                                            errorIds = setOfNotNull(prevId, item.id)
                                            delay(400)
                                            errorIds = emptySet()
                                        }
                                        selectedId = null
                                        item.audio?.let { SoundManager.playSound(it) }
                                    }
                                }
                            }
                        }
                    ) {
                        item.image?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                            Img(src = imageUrl, attrs = {
                                classes(MinigameStyles.matchItemImage)
                            })
                        }

                        item.text?.takeIf { it.isNotBlank() }?.let { text ->
                            P({
                                classes(MinigameStyles.matchItemText)
                            }) {
                                Text(text)
                            }
                        }

                        item.audio?.takeIf { it.isNotBlank() }?.let {
                            Span({
                                classes(MinigameStyles.matchItemAudioIcon)
                            }) { Text("🔊") }
                        }
                    }
                }
            }
        }
    }
}
