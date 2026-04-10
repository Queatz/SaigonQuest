package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
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

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    val currentItem = config.items.getOrNull(currentIndex) ?: return

    val handleNext: (Boolean) -> Unit = { success ->
        if (startTime == null) startTime = window.performance.now()
        totalAttempts++
        if (success) successCount++
        
        if (currentIndex < config.items.size - 1) {
            currentIndex++
            isFlipped = false
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
    }

    Div({
        classes(MinigameStyles.gameContainer)
    }) {
        P {
            Text("${currentIndex + 1} / ${config.items.size}")
        }

        Div({
            classes(MinigameStyles.cardContainer)
            onClick { 
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
                    val imageUrl = currentItem.image
                    if (imageUrl != null) {
                        Img(src = imageUrl, attrs = {
                            style {
                                maxWidth(100.percent)
                                maxHeight(200.px)
                                borderRadius(8.px)
                                marginBottom(16.px)
                            }
                        })
                    }
                    H3 { Text(currentItem.text ?: "") }
                    if (currentItem.audio != null) {
                        Span({
                            style { fontSize(24.px) }
                        }) { Text("🔊") }
                    }
                }

                // Back Face
                FramedBorder(
                    attrs = {
                        classes(MinigameStyles.cardFace, MinigameStyles.cardBack)
                    }
                ) {
                    H3 { Text(currentItem.translation ?: "") }
                    P { Text(currentItem.definition ?: "") }
                }
            }
        }

        Div({
            classes(MinigameStyles.controlsContainer)
        }) {
            DialogButton(
                onClick = { handleNext(false) },
                style = DialogButtonStyle.Secondary
            ) {
                Text(translated("Still learning"))
            }

            DialogButton(
                onClick = { handleNext(true) },
                style = DialogButtonStyle.Primary
            ) {
                Text(translated("I know this!"))
            }
        }
    }
}

