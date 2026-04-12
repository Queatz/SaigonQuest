package main.components.minigame

import androidx.compose.runtime.*
import main.api.*
import main.components.BaseDialog
import models.*
import org.jetbrains.compose.web.dom.*
import org.jetbrains.compose.web.css.*
import main.theme.Theme
import main.components.translated
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.FramedBorder

/**
 * MinigameOverlay component
 * Handles receiving minigame prompts and rendering the appropriate game UI.
 */
@Composable
fun MinigameOverlay(
    onClose: () -> Unit = {}
) {
    var currentConfig by remember { mutableStateOf<MinigameConfig?>(null) }

    LaunchedEffect(Unit) {
        WebSocketApi.addMinigameListener { config ->
            console.log("MinigameOverlay: Received minigame: ${config.title}")
            currentConfig = config
        }
    }

    val config = currentConfig ?: return

    val handleComplete: (MinigameResult) -> Unit = { result ->
        WebSocketApi.sendMinigameResult(result)
        currentConfig = null
        onClose()
    }

    BaseDialog(
        onClose = { currentConfig = null },
        title = config.title,
        showCloseButton = true,
        isFullscreen = true,
        useBorder = false,
        content = {
            when (config.type) {
                MinigameType.Flashcards -> FlashcardsGame(config, handleComplete)
                MinigameType.MatchGame -> MatchGame(config, handleComplete)
                MinigameType.Scenario -> ScenarioGame(config, handleComplete)
                MinigameType.Sequencing -> SequencingGame(config, handleComplete)
            }
        }
    )
}

/**
 * Common WIN screen for all minigames
 */
@Composable
fun WinScreen(
    result: MinigameResult,
    onContinue: (MinigameResult) -> Unit
) {
    Div({
        classes(MinigameStyles.gameContainer)
        style {
            gap(32.px)
        }
    }) {
        Div({
            classes(MinigameStyles.gameContentWrapper)
        }) {
            FramedBorder(
                attrs = {
                    style {
                        width(100.percent)
                        maxWidth(450.px)
                        padding(40.px)
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        gap(24.px)
                        boxSizing("border-box")
                    }
                }
            ) {
                H3({
                    style {
                        margin(0.px)
                        property("border-bottom", "2px solid ${Theme.colors.primary}")
                        paddingBottom(12.px)
                        width(100.percent)
                        textAlign("center")
                        property("text-transform", "uppercase")
                        property("letter-spacing", "1px")
                    }
                }) {
                    Text(translated("Quest results"))
                }

                Div({
                    style {
                        display(DisplayStyle.Grid)
                        gridTemplateColumns("40px 1fr")
                        gap(16.px)
                        alignItems(AlignItems.Center)
                        width(100.percent)
                        padding(16.px, 0.px)
                    }
                }) {
                    // Reflex stat
                    Span({
                        style { fontSize(32.px); textAlign("center") }
                    }) { Text("⚡") }
                    Div({
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                        }
                    }) {
                        Span({ style { fontSize(18.px); opacity(0.8) } }) { Text(translated("Avg reflex")) }
                        Span({ style { fontSize(28.px); fontWeight("bold") } }) {
                            val reflexSeconds = (result.reflex * 100).toInt() / 100.0
                            Text("${reflexSeconds}s")
                        }
                    }

                    // Accuracy stat
                    Span({
                        style { fontSize(32.px); textAlign("center") }
                    }) { Text("🎯") }
                    Div({
                        style {
                            display(DisplayStyle.Flex)
                            flexDirection(FlexDirection.Column)
                        }
                    }) {
                        Span({ style { fontSize(18.px); opacity(0.8) } }) { Text(translated("Accuracy")) }
                        Span({ style { fontSize(28.px); fontWeight("bold") } }) {
                            Text("${(result.accuracy * 100).toInt()}%")
                        }
                    }
                }

                DialogButton(
                    onClick = { onContinue(result) },
                    style = DialogButtonStyle.Primary,
                    attrs = {
                        style {
                            width(100.percent)
                            marginTop(12.px)
                        }
                    }
                ) {
                    Text(translated("Continue"))
                }
            }
        }
    }
}

/**
 * Common helper to calculate accuracy and reflex
 */
fun calculateMetrics(
    startTime: Double,
    endTime: Double,
    totalAttempts: Int,
    successCount: Int
): Pair<Double, Double> {
    val durationSeconds = (endTime - startTime) / 1000.0
    // Reflex should be based on all attempts to measure actual speed of interaction
    val reflex = if (totalAttempts > 0) durationSeconds / totalAttempts else durationSeconds
    val accuracy = if (totalAttempts > 0) successCount.toDouble() / totalAttempts else 0.0
    return reflex to accuracy
}
