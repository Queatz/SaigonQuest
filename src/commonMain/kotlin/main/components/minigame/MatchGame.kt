package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
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
    
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }

    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    Div({
        classes(MinigameStyles.gameContainer)
    }) {
        P {
            Text("${matchedIds.size / 2} / ${config.items.size / 2} ${translated("pairs matched")}")
        }

        Div({
            classes(MinigameStyles.matchGrid)
        }) {
            shuffledItems.forEach { item ->
                val isMatched = matchedIds.contains(item.id)
                val isSelected = selectedId == item.id
                
                if (!isMatched) {
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.matchItem)
                            if (isSelected) classes(MinigameStyles.selectedMatchItem)
                            
                            onClick {
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
                                            winResult = MinigameResult(
                                                minigameId = config.minigameId,
                                                type = MinigameType.MatchGame,
                                                reflex = perItemReflex,
                                                accuracy = accuracy,
                                                completed = true
                                            )
                                        }
                                    } else {
                                        // No match
                                        selectedId = item.id
                                        item.audio?.let { SoundManager.playSound(it) }
                                    }
                                }
                            }
                        }
                    ) {
                        val imageUrl = item.image
                        if (imageUrl != null) {
                            Img(src = imageUrl, attrs = {
                                style {
                                    width(100.percent)
                                    height(100.percent)
                                    maxWidth(120.px)
                                    maxHeight(120.px)
                                    property("object-fit", "cover")
                                }
                            })
                        } else if (item.text != null) {
                            Text(item.text!!)
                        } else if (item.audio != null) {
                            Span({
                                style { fontSize(32.px) }
                            }) { Text("🔊") }
                        }
                    }
                } else {
                    Div { }
                }
            }
        }
    }
}
