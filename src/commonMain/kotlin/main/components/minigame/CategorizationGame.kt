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
fun CategorizationGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    val items = remember { config.items.shuffled() }
    val categories = remember { config.items.mapNotNull { it.matchId }.distinct() }
    val sortedItems = remember { mutableStateListOf<String>() } // IDs of sorted items
    
    val currentIndex = remember { derivedStateOf { items.indexOfFirst { it.id !in sortedItems } } }
    val currentItem = remember { derivedStateOf { if (currentIndex.value != -1) items[currentIndex.value] else null } }

    var errorCategory by remember { mutableStateOf<String?>(null) }
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
            // Instruction
            P({
                style {
                    fontSize(24.px)
                    color(Color.white)
                    textAlign("center")
                    marginBottom(40.px)
                }
            }) {
                Text(translated("Sort the items into the correct categories"))
            }

            // Current Item (Crate/Conveyor)
            Div({
                classes(MinigameStyles.categorizationItemContainer)
            }) {
                currentItem.value?.let { item ->
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.categorizationItem)
                            onClick {
                                SoundManager.playItemAudio(item)
                            }
                        }
                    ) {
                        item.image?.let { url ->
                            Img(src = url, attrs = { classes(MinigameStyles.categorizationItemImage) })
                        }
                        item.text?.let { text ->
                            P({ classes(MinigameStyles.categorizationItemText) }) { Text(text) }
                        }
                        
                        val hasAudio = item.audio?.isNotBlank() == true || item.text?.isNotBlank() == true
                        if (hasAudio) {
                            Span({
                                style { fontSize(16.px); property("position", "absolute"); top(4.px); right(4.px) }
                            }) { Text("🔊") }
                        }
                    }
                } ?: Div { Text("Done!") }
            }

            // Baskets
            Div({
                classes(MinigameStyles.categorizationBasketsContainer)
            }) {
                categories.forEach { categoryId ->
                    val isError = errorCategory == categoryId
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.categorizationBasket)
                            if (isError) classes(MinigameStyles.shakeError)
                            onClick {
                                if (startTime == null) startTime = window.performance.now()
                                
                                val item = currentItem.value ?: return@onClick
                                totalAttempts++
                                
                                if (item.matchId == categoryId) {
                                    successCount++
                                    sortedItems.add(item.id)
                                    SoundManager.playItemAudio(item)
                                    
                                    if (sortedItems.size == items.size) {
                                        val endTime = window.performance.now()
                                        scope.launch {
                                            val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                            winResult = MinigameResult(
                                                minigameId = config.minigameId,
                                                type = MinigameType.Categorization,
                                                reflex = reflex,
                                                accuracy = accuracy,
                                                completed = true
                                            )
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        errorCategory = categoryId
                                        delay(500)
                                        errorCategory = null
                                    }
                                }
                            }
                        }
                    ) {
                        P({ classes(MinigameStyles.categorizationBasketText) }) {
                            Text(categoryId)
                        }
                    }
                }
            }
        }
    }
}
