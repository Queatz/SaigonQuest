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
import main.components.FramedBorder
import main.components.translated
import org.jetbrains.compose.web.attributes.InputType
import org.w3c.dom.events.MouseEvent

@Composable
fun ScenarioGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    // The first item should contain the hero image
    val heroItem = config.items.find { it.image != null && it.hotspot == null }
    val targets = remember { config.items.filter { it.hotspot != null } }
    
    var currentTargetIndex by remember { mutableStateOf(0) }
    val currentTarget = targets.getOrNull(currentTargetIndex)
    
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    
    val matchedTargets = remember { mutableStateListOf<Int>() }

    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    Div({
        classes(MinigameStyles.gameContainer)
    }) {
        currentTarget?.let { target ->
            H3 { 
                Text("${translated("Find")}: ${target.text ?: target.translation ?: ""}")
            }
        }

        FramedBorder(
            attrs = {
                classes(MinigameStyles.scenarioBackground)
            }
        ) {
            heroItem?.image?.let { url ->
                Img(src = url, attrs = {
                    style {
                        maxWidth(100.percent)
                        maxHeight(80.vh)
                        display(DisplayStyle.Block)
                        property("object-fit", "contain")
                    }
                    onClick { event ->
                        if (startTime == null) startTime = window.performance.now()
                        if (currentTarget == null) return@onClick
                        
                        val rect = (event.currentTarget as? org.w3c.dom.Element)?.getBoundingClientRect() ?: return@onClick
                        val clickX = (event.clientX - rect.left) / rect.width
                        val clickY = (event.clientY - rect.top) / rect.height
                        
                        totalAttempts++
                        
                        val hotspot = currentTarget.hotspot!!
                        val dx = clickX - hotspot.x
                        val dy = clickY - hotspot.y
                        val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                        
                        if (distance <= hotspot.radius) {
                            // Success!
                            successCount++
                            matchedTargets.add(currentTargetIndex)
                            currentTargetIndex++
                            
                            // Play success sound or item audio
                            currentTarget.audio?.let { SoundManager.playSound(it) }
                            
                            if (currentTargetIndex >= targets.size) {
                                val endTime = window.performance.now()
                                val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                winResult = MinigameResult(
                                    minigameId = config.minigameId,
                                    userId = "",
                                    type = MinigameType.Scenario,
                                    reflex = reflex,
                                    accuracy = accuracy,
                                    completed = true
                                )
                            }
                        }
                    }
                })
            }

            // Optional: show matched hotspots
            matchedTargets.forEach { index ->
                val target = targets[index]
                val hotspot = target.hotspot!!
                Div({
                    classes(MinigameStyles.hotspotIndicator)
                    style {
                        left((hotspot.x * 100).percent)
                        top((hotspot.y * 100).percent)
                        width((hotspot.radius * 2 * 100).percent)
                        property("aspect-ratio", "1 / 1")
                    }
                })
            }
        }
    }
}
