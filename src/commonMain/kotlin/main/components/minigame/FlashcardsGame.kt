package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import main.api.*
import main.components.audio.SoundManager
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.translated
import main.theme.Theme
import models.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import main.theme.Styles
import main.components.FramedBorder

@Composable
fun FlashcardsGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }

    var winResult by remember { mutableStateOf<MinigameResult?>(null) }
    val scope = rememberCoroutineScope()
    var isTransitioning by remember { mutableStateOf(false) }

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    val currentItem = config.items.getOrNull(currentIndex) ?: return

    val handleNext: (Boolean) -> Unit = { success ->
        if (!isTransitioning) {
            isTransitioning = true
            if (startTime == null) startTime = window.performance.now()
            totalAttempts++
            if (success) successCount++

            scope.launch {
                if (isFlipped) {
                    isFlipped = false
                    delay(600) // Match the transition time in MinigameStyles.kt (0.6s)
                }

                if (currentIndex < config.items.size - 1) {
                    currentIndex++
                } else {
                    val endTime = window.performance.now()
                    val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                    winResult = MinigameResult(
                        minigameId = config.minigameId,
                        type = MinigameType.Flashcards,
                        reflex = reflex,
                        accuracy = accuracy,
                        completed = true
                    )
                }
                isTransitioning = false
            }
        }
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
                Text("${currentIndex + 1} / ${config.items.size}")
            }

            Div({
                classes(MinigameStyles.cardContainer)
                onClick { 
                    if (isTransitioning) return@onClick
                    if (startTime == null) startTime = window.performance.now()
                    isFlipped = !isFlipped
                    if (isFlipped) {
                        // Play audio if available on flip
                        currentItem.audio?.let { url ->
                            SoundManager.playSound(url)
                        }
                    }
                }
            }) {
                Div({
                    classes(MinigameStyles.cardInner)
                    if (isFlipped) classes("is-flipped")
                    style {
                        property("transform-style", "preserve-3d")
                    }
                }) {
                    // Front Face
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.cardFace)
                        }
                    ) {
                        Div({
                            classes(MinigameStyles.flashcardText)
                        }) { Text(currentItem.text ?: "") }
                        
                        if (currentItem.audio != null) {
                            Span({
                                style { fontSize(28.px) }
                            }) { Text("🔊") }
                        }
                    }

                    // Back Face
                    FramedBorder(
                        attrs = {
                            classes(MinigameStyles.cardFace, MinigameStyles.cardBack)
                        }
                    ) {
                        val imageUrl = currentItem.image
                        if (imageUrl != null) {
                            Img(src = imageUrl, attrs = {
                                classes(MinigameStyles.flashcardImage)
                            })
                        }
                        
                        Div({
                            classes(MinigameStyles.flashcardTranslation)
                        }) { Text(currentItem.translation ?: "") }
                        
                        Div({
                            classes(MinigameStyles.flashcardDefinition)
                        }) { Text(currentItem.definition ?: "") }
                    }
                }
            }

            Div({
                classes(MinigameStyles.controlsContainer)
            }) {
                DialogButton(
                    onClick = { handleNext(false) },
                    style = DialogButtonStyle.Secondary,
                    isDisabled = isTransitioning
                ) {
                    Text(translated("Still learning"))
                }

                DialogButton(
                    onClick = { handleNext(true) },
                    style = DialogButtonStyle.Primary,
                    isDisabled = isTransitioning
                ) {
                    Text(translated("I know this!"))
                }
            }
        }
    }
}

