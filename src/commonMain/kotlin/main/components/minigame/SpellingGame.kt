package main.components.minigame

import androidx.compose.runtime.*
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.components.FramedBorder
import main.components.audio.SoundManager
import main.components.translated
import models.*
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.maxLength
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.css.Position
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLInputElement

@Composable
fun SpellingGame(
    config: MinigameConfig,
    onComplete: (MinigameResult) -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var startTime by remember { mutableStateOf<Double?>(null) }
    var totalAttempts by remember { mutableStateOf(0) }
    var successCount by remember { mutableStateOf(0) }
    
    val currentItem = config.items.getOrNull(currentIndex) ?: return
    val targetWord = (currentItem.text ?: "").trim().uppercase()
    
    var userLetters by remember(currentIndex) { mutableStateOf(List(targetWord.length) { "" }) }
    var letterResults by remember(currentIndex) { mutableStateOf(List(targetWord.length) { 0 }) } // 0: none, 1: correct, 2: wrong
    
    val scope = rememberCoroutineScope()
    var winResult by remember { mutableStateOf<MinigameResult?>(null) }
    var isWordCompleted by remember { mutableStateOf(false) }

    if (winResult != null) {
        WinScreen(winResult!!, onComplete)
        return
    }

    // Refs for all inputs to handle focus
    val inputRefs = remember { mutableStateMapOf<Int, HTMLInputElement>() }
    val firstInputIndex = remember(targetWord) { targetWord.indexOfFirst { it != ' ' } }

    LaunchedEffect(currentIndex) {
        // Auto-focus first input on new word
        delay(100)
        if (firstInputIndex != -1) {
            inputRefs[firstInputIndex]?.focus()
        }
        SoundManager.playItemAudio(currentItem)
    }

    fun completeWord() {
        if (isWordCompleted) return
        isWordCompleted = true
        
        scope.launch {
            delay(1000)
            if (currentIndex < config.items.size - 1) {
                currentIndex++
                isWordCompleted = false
            } else {
                val endTime = window.performance.now()
                val (reflex, accuracy) = calculateMetrics(startTime ?: endTime, endTime, totalAttempts, successCount)
                winResult = MinigameResult(
                    minigameId = config.minigameId,
                    type = MinigameType.Spelling,
                    reflex = reflex,
                    accuracy = accuracy,
                    completed = true
                )
            }
        }
    }

    Div({
        classes(MinigameStyles.gameContainer, MinigameStyles.noOverflow)
    }) {
        Div({
            classes(MinigameStyles.gameContentWrapper)
            if (isWordCompleted) classes(MinigameStyles.spellingSuccessAnimation)
        }) {
            // Instruction
            P({
                style {
                    fontSize(24.px)
                    color(Color.white)
                    textAlign("center")
                    marginBottom(24.px)
                }
            }) {
                Text(translated("Spell the word correctly"))
            }

            // Progress
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

            // Card information (Back of the card)
            FramedBorder(
                attrs = {
                    classes(MinigameStyles.cardFace)
                    style {
                        position(Position.Relative)
                        width(400.px)
                        maxWidth(100.percent)
                        property("aspect-ratio", "3/4")
                        justifyContent(JustifyContent.Center)
                        cursor("pointer")
                    }
                    onClick { SoundManager.playItemAudio(currentItem) }
                }
            ) {
                currentItem.image?.let { url ->
                    Img(src = url, attrs = {
                        classes(MinigameStyles.flashcardImage)
                    })
                }
                
                currentItem.translation?.let { trans ->
                    Div({
                        classes(MinigameStyles.flashcardTranslation)
                    }) { Text(trans) }
                }
                
                currentItem.definition?.let { def ->
                    Div({
                        classes(MinigameStyles.flashcardDefinition)
                    }) { Text(def) }
                }

                val hasAudio = currentItem.audio?.isNotBlank() == true || currentItem.text?.isNotBlank() == true
                if (hasAudio) {
                    Span({
                        style { fontSize(28.px); marginTop(16.px) }
                    }) { Text("🔊") }
                }
            }

            // Spelling inputs
            Div({
                classes(MinigameStyles.spellingInputContainer)
            }) {
                targetWord.forEachIndexed { index, char ->
                    if (char == ' ') {
                        Div({
                            style { width(30.px) }
                        })
                    } else {
                        Input(InputType.Text) {
                            classes(MinigameStyles.spellingLetterInput)
                            if (letterResults[index] == 1) classes("is-correct")
                            if (letterResults[index] == 2) classes("is-wrong")
                            
                            value(userLetters[index])
                            maxLength(1)
                            
                            ref { element ->
                                inputRefs[index] = element
                                onDispose { inputRefs.remove(index) }
                            }

                            onInput { event ->
                                if (startTime == null) startTime = window.performance.now()
                                if (isWordCompleted) return@onInput
                                
                                val newVal = event.value.takeLast(1).uppercase()
                                if (newVal.isNotEmpty()) {
                                    val isCorrect = newVal[0] == targetWord[index]
                                    totalAttempts++
                                    if (isCorrect) successCount++
                                    
                                    val newList = userLetters.toMutableList()
                                    newList[index] = newVal
                                    userLetters = newList

                                    val newResults = letterResults.toMutableList()
                                    newResults[index] = if (isCorrect) 1 else 2
                                    letterResults = newResults

                                    if (isCorrect) {
                                        if (index < targetWord.length - 1) {
                                            // Move to next non-space input
                                            var nextIndex = index + 1
                                            while (nextIndex < targetWord.length && targetWord[nextIndex] == ' ') {
                                                nextIndex++
                                            }
                                            if (nextIndex < targetWord.length) {
                                                inputRefs[nextIndex]?.focus()
                                            } else {
                                                completeWord()
                                            }
                                        } else {
                                            completeWord()
                                        }
                                    } else {
                                        scope.launch {
                                            delay(400)
                                            val resetResults = letterResults.toMutableList()
                                            resetResults[index] = 0
                                            letterResults = resetResults
                                            
                                            val resetLetters = userLetters.toMutableList()
                                            resetLetters[index] = ""
                                            userLetters = resetLetters
                                        }
                                    }
                                }
                            }
                            
                            onKeyDown { event ->
                                if (event.key == "Backspace" && userLetters[index].isEmpty() && index > 0) {
                                    var prevIndex = index - 1
                                    while (prevIndex >= 0 && targetWord[prevIndex] == ' ') {
                                        prevIndex--
                                    }
                                    if (prevIndex >= 0) {
                                        inputRefs[prevIndex]?.focus()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
