package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import main.api.*
import main.components.media.AudioRecorder
import main.components.media.AudioRecorderUI
import main.components.media.rememberAudioRecorderState
import main.components.media.UploadMediaDialog
import main.theme.Styles
import main.theme.Theme
import models.DialogueData
import models.MediaType
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.fontWeight
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.textAlign
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Img
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.attributes.placeholder
import org.jetbrains.compose.web.attributes.autoFocus
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.height
import org.jetbrains.compose.web.css.marginTop
import org.jetbrains.compose.web.css.paddingTop
import org.jetbrains.compose.web.dom.Br
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Audio
import org.w3c.dom.HTMLElement
import web.animations.awaitAnimationFrame
import web.cssom.HtmlAttributes.Companion.value
import kotlin.invoke

/**
 * DialogueBox component
 *
 * This component displays a dialogue box with text and an OK button.
 * It's used to show dialogue messages from NPCs or objects.
 */
@Composable
fun DialogueBox(
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    
    // State to track the current dialogue
    var currentDialogue by remember { mutableStateOf<DialogueData?>(null) }

    // State to track if we're waiting for a new dialogue after a button click
    var isWaiting by remember { mutableStateOf(false) }

    // Responsive helper
    val isMobile = rememberIsMobile()
    var textInput by remember { mutableStateOf("") }
    var inputEl by remember { mutableStateOf<HTMLElement?>(null) }
    
    var uploadedImages by remember { mutableStateOf(listOf<String>()) }
    var uploadedAudios by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var uploadMediaType by remember { mutableStateOf<MediaType?>(null) }

    LaunchedEffect(currentDialogue) {
        if (currentDialogue == null) {
            onClose()
        }
    }

    // Add a listener for dialogue events
    LaunchedEffect(Unit) {
        WebSocketApi.addDialogueListener { dialogueData ->
            console.log("DialogueBox: Received dialogue: ${dialogueData.text}")
            currentDialogue = dialogueData
            isWaiting = false
        }
    }

    // Only show the dialogue box if there's a dialogue to show
    val dialogue = currentDialogue ?: return

    BaseDialog(
        onClose = { currentDialogue = null },
        title = dialogue.title,
        showCloseButton = false,
        isFullscreen = false,
        clickOutside = true,
        maxWidth = 800.px.toString(),
        anchorToBottom = isMobile.value,
        content = {
            // Optional image above the text
            if (!dialogue.imageUrl.isNullOrBlank()) {
                Img(
                    src = dialogue.imageUrl!!,
                    attrs = {
                        style {
                            width(100.percent)
                            borderRadius(8.px)
                            marginBottom(12.px)
                        }
                    }
                )
            }

            // Object title (optional)
            if (!dialogue.objectTitle.isNullOrBlank()) {
                P({
                    style {
                        margin(0.px)
                        padding(0.px)
                        textAlign("center")
                        fontSize(18.px)
                        fontWeight("bold")
                        marginBottom(6.px)
                    }
                }) {
                    Text(dialogue.objectTitle!!)
                }
            }

            // Dialogue text
            Md(
                content = dialogue.text,
                clickableWords = dialogue.clickableWords,
            ) {
                if (!dialogue.text.trim().contains("\n")) {
                    style {
                        textAlign("center")
                    }
                }

                onClick { event ->
                    val target = event.target as? org.w3c.dom.HTMLElement ?: return@onClick
                    val word = target.getAttribute("data-word") ?: return@onClick

                    val scope = MainScope()
                    scope.launch {
                        try {
                            val user = UserApi.getCurrentUser()
                            val userId = user.key
                            WebSocketApi.sendDialogueConfirmation(
                                dialogueId = dialogue.dialogueId,
                                userId = userId,
                                clickedWord = word,
                            )
                        } catch (e: Throwable) {
                            console.error("Error sending word click confirmation", e)
                        }
                    }
                }
            }

            if (dialogue.requiresImage) {
                Div({
                    classes(Styles.imageInputContainer)
                }) {
                    uploadedImages.forEach { url ->
                        Div({
                            classes(Styles.imagePreviewItem)
                        }) {
                            Img(src = url, attrs = {
                                classes(Styles.imagePreview)
                            })
                            Button(attrs = {
                                if (isWaiting) disabled()
                                classes(Styles.imageRemoveButton)
                                onClick {
                                    if (isWaiting) return@onClick
                                    uploadedImages = uploadedImages.filter { it != url }
                                }
                            }) {
                                Text("×")
                            }
                        }
                    }

                    if (dialogue.allowMultipleImages || uploadedImages.isEmpty()) {
                        Button(attrs = {
                            if (isWaiting) disabled()
                            classes(Styles.imageAddButton)
                            onClick {
                                if (isWaiting) return@onClick
                                uploadMediaType = MediaType.Image
                            }
                        }) {
                            Text("➕")
                            Text(translated("Add image"))
                        }
                    }
                }
            }

            if (dialogue.requiresAudio) {
                Div({
                    classes(Styles.imageInputContainer)
                }) {
                    uploadedAudios.forEach { (url, name) ->
                        Div({
                            classes(Styles.imagePreviewItem)
                            style {
                                width(320.px)
                                property("height", "auto")
                                padding(12.px)
                                paddingTop(32.px)
                                property("overflow", "visible")
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(8.px)
                            }
                        }) {
                            P({
                                style {
                                    margin(0.px)
                                    fontSize(14.px)
                                    fontWeight("bold")
                                    property("word-break", "break-all")
                                }
                            }) {
                                Text(name)
                            }
                            Audio(attrs = {
                                attr("src", url)
                                attr("controls", "true")
                                style {
                                    width(100.percent)
                                }
                            })
                            Button(attrs = {
                                if (isWaiting) disabled()
                                classes(Styles.imageRemoveButton)
                                onClick {
                                    if (isWaiting) return@onClick
                                    uploadedAudios = uploadedAudios.filter { it.first != url }
                                }
                            }) {
                                Text("×")
                            }
                        }
                    }

                    if (dialogue.allowMultipleAudio || uploadedAudios.isEmpty()) {
                        val recorderState = rememberAudioRecorderState(
                            onUpload = { file ->
                                scope.launch {
                                    try {
                                        val token = UserApi.getToken() ?: ""
                                        val record = ContentApi.upload(file, token)
                                        val url = ContentApi.imageUrlFor(record.uploadedFilePath)
                                        uploadedAudios = if (dialogue.allowMultipleAudio) {
                                            uploadedAudios + (url to record.originalFileName)
                                        } else {
                                            listOf(url to record.originalFileName)
                                        }
                                    } catch (t: Throwable) {
                                        console.error("Failed to upload recorded audio", t)
                                    }
                                }
                            },
                            onError = { console.error(it) }
                        )

                        if (recorderState.isRecording) {
                            AudioRecorderUI(
                                state = recorderState,
                                isFullWidth = true,
                                finishButtonText = "Done"
                            )
                        } else {
                            Button(attrs = {
                                if (isWaiting) disabled()
                                classes(Styles.imageAddButton)
                                onClick {
                                    if (isWaiting) return@onClick
                                    uploadMediaType = MediaType.Audio
                                }
                            }) {
                                Text("➕")
                                Text(translated("Add audio"))
                            }

                            AudioRecorder(
                                onUpload = {},
                                state = recorderState,
                                buttonAttrs = {
                                    if (isWaiting) classes(Styles.disabledAction)
                                    classes(Styles.imageAddButton)
                                },
                                isEnabled = !isWaiting,
                                buttonContent = {
                                    Text("🎤 ")
                                    Text(translated("Record"))
                                },
                                finishButtonText = "Done"
                            )
                        }
                    }
                }
            }

            if (dialogue.requiresInput) {
                Div(
                    attrs = {
                        style {
                            display(DisplayStyle.Flex)
                            gap(8.px)
                            marginTop(16.px)
                        }
                    }
                ) {
                    DialogInput(
                        value = textInput,
                        onInput = { textInput = it },
                        placeholder = dialogue.placeholder?.takeIf { it.isNotBlank() },
                        wrapperAttrs = {
                            style {
                                property("flex-grow", "1")
                                width(0.px)
                            }
                        },
                        enabled = !isWaiting,
                        autoFocus = true,
                        attrs = {
                            onKeyDown { ev ->
                                if (ev.key == "Enter" && !isWaiting) {
                                    isWaiting = true
                                    val scope = MainScope()
                                    scope.launch {
                                        try {
                                            val user = UserApi.getCurrentUser()
                                            val userId = user.key
                                            WebSocketApi.sendDialogueConfirmation(dialogue.dialogueId, userId, 0, textInput)
                                        } catch (e: Throwable) {
                                            console.error("Error sending dialogue confirmation via Enter", e)
                                            isWaiting = false
                                        } finally {
                                            textInput = ""

                                            delay(500)
                                            if (isWaiting && currentDialogue?.dialogueId == dialogue.dialogueId) {
                                                currentDialogue = null
                                                isWaiting = false
                                            }
                                        }
                                    }
                                }
                            }

                            ref {
                                it.focus()

                                inputEl = it

                                onDispose {
                                    inputEl = null
                                }
                            }
                        }
                    )

                    // Voice input button - appends transcribed text to the end of textInput
                    VoiceInput(
                        onText = { transcribed ->
                            textInput = buildString {
                                append(textInput)
                                if (isNotEmpty() && !textInput.endsWith(" ")) append(' ')
                                append(transcribed.trim())
                            }
                            scope.launch {
                                delay(100)
                                awaitAnimationFrame()
                                inputEl?.let { element ->
                                    element.scrollLeft = element.scrollWidth.toDouble()
                                }
                            }
                        }
                    )
                }
            }
        },
        actions = {
            // Container to allow wrapping when many buttons
            Div({
                classes(Styles.dialogueButtons)
            }) {
                dialogue.buttons.forEachIndexed { index, buttonText ->
                    DialogButton(
                        onClick = {
                            if (isWaiting) return@DialogButton
                            isWaiting = true
                            
                            val scope = MainScope()
                            scope.launch {
                                try {
                                    val user = UserApi.getCurrentUser()
                                    val userId = user.key
                                    val inputText = if (dialogue.requiresInput) textInput else null
                                    val imageUrls = if (dialogue.requiresImage) uploadedImages else null
                                    val audioUrls = if (dialogue.requiresAudio) uploadedAudios.map { it.first } else null
                                    WebSocketApi.sendDialogueConfirmation(
                                        dialogueId = dialogue.dialogueId,
                                        userId = userId,
                                        buttonIndex = index,
                                        inputText = inputText,
                                        imageUrls = imageUrls,
                                        audioUrls = audioUrls,
                                    )
                                } catch (e: Throwable) {
                                    console.error("Error sending dialogue confirmation", e)
                                    isWaiting = false
                                } finally {
                                    // Reset
                                    textInput = ""
                                    uploadedImages = emptyList()
                                    uploadedAudios = emptyList()
                                    
                                    delay(500)
                                    if (isWaiting && currentDialogue?.dialogueId == dialogue.dialogueId) {
                                        currentDialogue = null
                                        isWaiting = false
                                    }
                                }
                            }
                        },
                        style = DialogButtonStyle.Primary,
                        isDisabled = isWaiting,
                    ) { Text(buttonText) }
                }
            }
        }
    )

    if (uploadMediaType != null) {
        val currentType = uploadMediaType!!
        UploadMediaDialog(
            onClose = { uploadMediaType = null },
            onSelect = { url, _, name ->
                if (currentType == MediaType.Image) {
                    uploadedImages = if (dialogue.allowMultipleImages) {
                        uploadedImages + url
                    } else {
                        listOf(url)
                    }
                } else if (currentType == MediaType.Audio) {
                    uploadedAudios = if (dialogue.allowMultipleAudio) {
                        uploadedAudios + (url to name)
                    } else {
                        listOf(url to name)
                    }
                }
                uploadMediaType = null
            },
            filter = currentType
        )
    }
}
