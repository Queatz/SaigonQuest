package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.components.audio.SoundManager
import models.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import main.components.FramedBorder
import main.components.translated

@Composable
fun ScenarioGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    // The first item should contain the hero image
    val heroItem = config.items.find { it.image != null && it.hotspot == null }
    val targets = remember { config.items.filter { it.hotspot != null } }
    
    val matchedTargets = remember { mutableStateListOf<Int>() }
    val currentTargetIndex = targets.indices.find { !matchedTargets.contains(it) } ?: -1
    val currentPromptTarget = targets.getOrNull(currentTargetIndex)
    
    var startTime by remember { mutableStateOf<Double?>(null) }
    var mousePos by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val isInsideHotspot = { x: Double, y: Double, hotspot: Hotspot ->
        val dx = x - hotspot.x
        val dy = y - hotspot.y
        kotlin.math.sqrt(dx * dx + dy * dy) <= hotspot.radius
    }

    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    
    val scope = rememberCoroutineScope()
    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            SoundManager.stopAllSounds()
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
            FramedBorder(
                attrs = {
                    classes(MinigameStyles.scenarioBackground)
                }
            ) {
                currentPromptTarget?.let { target ->
                    H3({
                        classes(MinigameStyles.scenarioPrompt)
                    }) {
                        Text("${translated("Find")}: ${target.text ?: target.translation ?: ""}")
                    }
                }

                heroItem?.image?.let { url ->
                    Img(src = url, attrs = {
                        style {
                            maxWidth(100.percent)
                            maxHeight(80.vh)
                            display(DisplayStyle.Block)
                            property("object-fit", "contain")
                        }
                        onClick { event ->
                            if (matchedTargets.size >= targets.size) return@onClick
                            
                            if (startTime == null) startTime = window.performance.now()
                            
                            val rect = (event.currentTarget as? org.w3c.dom.Element)?.getBoundingClientRect() ?: return@onClick
                            val clickX = (event.clientX - rect.left) / rect.width
                            val clickY = (event.clientY - rect.top) / rect.height
                            
                            totalAttempts++
                            
                            val isHit = currentTargetIndex != -1 && isInsideHotspot(clickX, clickY, targets[currentTargetIndex].hotspot!!)
                            
                            if (isHit) {
                                val clickedIndex = currentTargetIndex
                                // Success!
                                successCount++
                                matchedTargets.add(clickedIndex)
                                
                                val clickedTarget = targets[clickedIndex]
                                // Play success sound or item audio
                                clickedTarget.audio?.let { SoundManager.playSound(it) }
                                
                                if (matchedTargets.size >= targets.size) {
                                    val endTime = window.performance.now()
                                    scope.launch {
                                        delay(1000)
                                        val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                        winResult = MinigameResult(
                                            minigameId = config.minigameId,
                                            type = MinigameType.Scenario,
                                            reflex = reflex,
                                            accuracy = accuracy,
                                            completed = true
                                        )
                                    }
                                }
                            }
                        }
                        onMouseMove { event ->
                            val rect = (event.currentTarget as? org.w3c.dom.Element)?.getBoundingClientRect() ?: return@onMouseMove
                            val mouseX = (event.clientX - rect.left) / rect.width
                            val mouseY = (event.clientY - rect.top) / rect.height
                            mousePos = mouseX to mouseY
                        }
                        onMouseLeave {
                            mousePos = null
                        }
                    })
                }

                // Show matched hotspots or hovered hotspots
                targets.indices.forEach { index ->
                    val target = targets[index]
                    val hotspot = target.hotspot!!
                    val isMatched = matchedTargets.contains(index)
                    
                    val isHovered = mousePos?.let { (mx, my) ->
                        isInsideHotspot(mx, my, hotspot)
                    } ?: false

                    if (isMatched || isHovered) {
                        Div({
                            classes(MinigameStyles.hotspotIndicator)
                            style {
                                left((hotspot.x * 100).percent)
                                top((hotspot.y * 100).percent)
                                width((hotspot.radius * 2 * 100).percent)
                                property("aspect-ratio", "1 / 1")
                                property("pointer-events", "none")

                                if (isMatched) {
                                    target.image?.let { url ->
                                        backgroundImage("url(\"$url\")")
                                        backgroundSize("100%")
                                        backgroundPosition("center")
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }
}
