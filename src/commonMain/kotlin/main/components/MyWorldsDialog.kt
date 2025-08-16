package main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.api.WorldApi
import models.World
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.color
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.fontSize
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.marginBottom
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.rgba
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/**
 * Dialog listing all worlds owned by the current user.
 */
@Composable
fun MyWorldsDialog(
    onClose: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var worlds by remember { mutableStateOf<List<World>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        try {
            worlds = WorldApi.getWorlds()
            isLoading = false
        } catch (e: Throwable) {
            console.error("Error fetching user worlds", e)
            error = e.message ?: "Failed to load worlds"
            isLoading = false
        }
    }

    BaseDialog(
        onClose = onClose,
        title = translated("My Worlds"),
        maxWidth = "560px",
        content = {
            when {
                isLoading -> {
                    Div(attrs = { style { padding(12.px) } }) {
                        Translated("Loading...")
                    }
                }
                error != null -> {
                    Div(
                        attrs = {
                            style {
                                padding(12.px)
                                color(rgba(255, 80, 80, 0.9))
                            }
                        }
                    ) { Text(error!!) }
                }
                worlds.isEmpty() -> {
                    Div(attrs = { style { padding(12.px) } }) {
                        Translated("You don't own any worlds yet")
                    }
                }
                else -> {
                    Div(
                        attrs = {
                            style {
                                display(DisplayStyle.Flex)
                                flexDirection(FlexDirection.Column)
                                gap(8.px)
                            }
                        }
                    ) {
                        worlds.forEach { world ->
                            Button(
                                attrs = {
                                    style {
                                        display(DisplayStyle.Flex)
                                        flexDirection(FlexDirection.Column)
                                        alignItems(AlignItems.FlexStart)
                                        gap(4.px)
                                        padding(12.px)
                                        backgroundColor(rgba(30, 30, 30, 0.6))
                                        borderRadius(8.px)
                                        color(rgba(255, 255, 255, 0.9))
                                        property("border", "1px solid rgba(100, 100, 100, 0.3)")
                                        property("text-align", "left")
                                        property("cursor", "pointer")
                                    }
                                    onClick {
                                        // Close dialog then navigate to the selected world's URL
                                        onClose()
                                        val path = if (world.url.isEmpty()) "/" else "/${world.url}"
                                        onNavigate(path)
                                    }
                                }
                            ) {
                                // Title
                                Span(
                                    attrs = {
                                        style {
                                            fontSize(16.px)
                                            property("font-weight", "bold")
                                        }
                                    }
                                ) { Text(world.title.ifEmpty { translated("Untitled World") }) }

                                // Description
                                if (world.description.isNotEmpty()) {
                                    P(
                                        attrs = {
                                            style {
                                                fontSize(13.px)
                                                color(rgba(220, 220, 220, 0.85))
                                                marginBottom(0.px)
                                            }
                                        }
                                    ) { Text(world.description) }
                                }

                                // URL
                                Span(
                                    attrs = {
                                        style {
                                            fontSize(12.px)
                                            color(rgba(180, 200, 255, 0.85))
                                        }
                                    }
                                ) { Text("/${world.url}") }
                            }
                        }
                    }
                }
            }
        },
        actions = {
            DialogButton(onClick = onClose) { Translated("Close") }
        }
    )
}
