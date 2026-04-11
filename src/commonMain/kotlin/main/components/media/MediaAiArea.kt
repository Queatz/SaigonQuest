package main.components.media

import androidx.compose.runtime.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import main.api.AiApi
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.DialogSelect
import main.components.Translated
import main.components.translated
import main.components.common.Spinner
import main.theme.Styles
import models.AiPromptHistory
import models.MediaType
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*
import org.w3c.dom.HTMLTextAreaElement

@Composable
fun MediaAiArea(
    onSelect: (String, MediaType, String) -> Unit,
    onError: (String) -> Unit = {},
    filter: MediaType? = null,
    onRefreshLibrary: () -> Unit = {},
) {
    var prompt by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("1024*1024") }
    var generating by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<AiPromptHistory>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val sizeOptions = listOf(
        "1024*1024" to "1024 x 1024 (Square)",
        "720*1280" to "720 x 1280 (Portrait)",
        "1280*720" to "1280 x 720 (Landscape)",
        "768*1024" to "768 x 1024 (Portrait)",
        "1024*768" to "1024 x 768 (Landscape)"
    )

    fun loadHistory() {
        scope.launch {
            try {
                history = AiApi.getHistory(20).filter { it.responsePreview.startsWith("http") }
            } catch (t: Throwable) {
                // Ignore history load errors
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    val placeholderText = translated("Describe the image you want to generate...")

    Div(attrs = {
        classes(Styles.mediaUploadArea)
    }) {
        if (generating) {
            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    flexDirection(FlexDirection.Column)
                    alignItems(AlignItems.Center)
                    justifyContent(JustifyContent.Center)
                    height(100.percent)
                    gap(12.px)
                }
            }) {
                Spinner(translated("Generating your image..."))
            }
        } else {
            TextArea(
                value = prompt,
                attrs = {
                    classes(Styles.dialogInputField)
                    style {
                        width(100.percent)
                        minHeight(60.px)
                        boxSizing("border-box")
                        property("resize", "vertical")
                    }
                    attr("placeholder", placeholderText)
                    onInput { prompt = it.value }
                }
            )

            Div(attrs = {
                style {
                    display(DisplayStyle.Flex)
                    justifyContent(JustifyContent.SpaceBetween)
                    alignItems(AlignItems.Center)
                    gap(8.px)
                }
            }) {
                DialogSelect(
                    options = sizeOptions,
                    value = size,
                    onInput = { size = it },
                    containerAttrs = {
                        style { flex(1) }
                    }
                )

                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        gap(8.px)
                    }
                }) {
                    if (history.isNotEmpty()) {
                        DialogButton(
                            onClick = { showHistory = !showHistory },
                            style = DialogButtonStyle.Alt,
                            small = true
                        ) {
                            Text(if (showHistory) "⌨️" else "📜")
                        }
                    }

                    DialogButton(
                        onClick = {
                            if (prompt.isBlank()) return@DialogButton
                            generating = true
                            // Use MainScope to continue in background even if this component is disposed
                            MainScope().launch {
                                try {
                                    val url = AiApi.generateImage(prompt, size)
                                    onSelect(url, MediaType.Image, prompt.take(30))
                                    onRefreshLibrary()
                                } catch (t: Throwable) {
                                    onError(t.message ?: "Generation failed")
                                } finally {
                                    generating = false
                                }
                            }
                        },
                        style = DialogButtonStyle.Primary,
                        isDisabled = prompt.isBlank(),
                        small = true
                    ) {
                        Translated("Generate")
                    }
                }
            }

            if (showHistory && history.isNotEmpty()) {
                Div(attrs = {
                    style {
                        maxHeight(340.px)
                        overflowY("auto")
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        gap(4.px)
                        padding(4.px)
                        backgroundColor(Color("#00000022"))
                        borderRadius(4.px)
                    }
                }) {
                    history.forEach { item ->
                        Div(attrs = {
                            style {
                                padding(4.px)
                                cursor("pointer")
                                fontSize(0.8.em)
                                property("border-bottom", "1px solid #00000011")
                                display(DisplayStyle.Flex)
                                gap(8.px)
                                alignItems(AlignItems.Center)
                            }
                            onClick {
                                prompt = item.prompt
                                showHistory = false
                            }
                        }) {
                            Img(src = item.responsePreview, attrs = {
                                style {
                                    width(32.px)
                                    height(32.px)
                                    property("object-fit", "cover")
                                    borderRadius(2.px)
                                }
                            })
                            Span { Text(item.prompt) }
                        }
                    }
                }
            }
        }
    }
}
