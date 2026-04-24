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
fun StoryGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    // Steps are items with matchId == null
    val steps = remember(config.items) { config.items.filter { it.matchId == null || it.matchId == "" } }
    
    // Track current step index
    var currentStepIndex by remember { mutableStateOf(0) }
    val currentStep = remember(currentStepIndex, steps) { 
        if (currentStepIndex < steps.size) steps[currentStepIndex] else null 
    }
    
    // Choices for current step are items with matchId == currentStep.id
    val choices = remember(currentStep, config.items) {
        config.items.filter { it.matchId == currentStep?.id }.shuffled()
    }

    var errorId by remember { mutableStateOf<String?>(null) }
    var successId by remember { mutableStateOf<String?>(null) }
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var isEntering by remember { mutableStateOf(true) }
    var isLeaving by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var winResult by remember { mutableStateOf<MinigameResult?>(null) }

    // Auto-play audio when step changes
    LaunchedEffect(currentStepIndex) {
        SoundManager.stopAllSounds()
        window.scrollTo(0.0, 0.0)
        isEntering = true
        delay(100)
        isEntering = false
        
        currentStep?.let { step ->
            delay(500)
            isAudioPlaying = true
            SoundManager.playItemAudio(step) {
                isAudioPlaying = false
            }
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
            classes(MinigameStyles.gameContentWrapper, MinigameStyles.storyTransition)
            if (isEntering || isLeaving) classes("entering")
        }) {
            // Story Step (Above)
            currentStep?.let { step ->
                Div({
                    classes(MinigameStyles.storyStepContainer)
                }) {
                    step.image?.let { url ->
                        Img(src = url, attrs = {
                            classes(MinigameStyles.storyStepImage)
                        })
                    }
                    
                    H3({
                        classes(MinigameStyles.storyStepText)
                    }) {
                        Text(step.text ?: "")
                    }
                    
                    if (step.audio != null || step.text != null) {
                        Div({
                            style { cursor("pointer"); fontSize(24.px) }
                            onClick { 
                                isAudioPlaying = true
                                SoundManager.playItemAudio(step) {
                                    isAudioPlaying = false
                                }
                            }
                        }) {
                            Text("🔊")
                        }
                    }
                }
            }

            // Choices (Below)
            Div({
                classes(MinigameStyles.storyChoicesContainer)
            }) {
                choices.forEach { choice ->
                    val isError = errorId == choice.id
                    val isSuccess = successId == choice.id
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.storyChoiceItem)
                            if (isError) {
                                classes(MinigameStyles.shakeError)
                                style {
                                    backgroundColor(rgba(244, 67, 54, 0.2))
                                }
                            }
                            if (isSuccess) {
                                classes(MinigameStyles.storyChoiceSuccess)
                            }
                            
                            onClick {
                                if (startTime == null) startTime = window.performance.now()
                                totalAttempts++
                                
                                val isCorrect = choice.definition == "true" || choice.definition == "correct"
                                
                                if (isCorrect) {
                                    successCount++
                                    isAudioPlaying = true
                                    SoundManager.playItemAudio(choice) {
                                        isAudioPlaying = false
                                    }
                                    
                                    scope.launch {
                                        successId = choice.id
                                        delay(800)
                                        // Wait for audio to finish
                                        while (isAudioPlaying) {
                                            delay(100)
                                        }
                                        
                                        if (currentStepIndex + 1 < steps.size) {
                                            isLeaving = true
                                            delay(500)
                                            successId = null
                                            currentStepIndex++
                                            isLeaving = false
                                        } else {
                                            val endTime = window.performance.now()
                                            val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                                            winResult = MinigameResult(
                                                minigameId = config.minigameId,
                                                type = MinigameType.Story,
                                                reflex = reflex,
                                                accuracy = accuracy,
                                                completed = true
                                            )
                                        }
                                    }
                                } else {
                                    scope.launch {
                                        errorId = choice.id
                                        SoundManager.playSound("https://saigon-quest.s3.ap-southeast-1.amazonaws.com/static/audio/error.mp3")
                                        delay(500)
                                        errorId = null
                                    }
                                }
                            }
                        }
                    ) {
                        choice.image?.let { url ->
                            Img(src = url, attrs = {
                                classes(MinigameStyles.storyChoiceImage)
                            })
                        }
                        
                        Span({
                            classes(MinigameStyles.storyChoiceText)
                        }) {
                            Text(choice.text ?: "")
                        }
                        
                        if (choice.audio != null) {
                            Span({ style { marginLeft(8.px); cursor("pointer") } }) { Text("🔊") }
                        }
                    }
                }
            }
        }
    }
}
