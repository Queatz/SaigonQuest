package main.components.tools

import androidx.compose.runtime.Composable
import app.softwork.routingcompose.Router
import main.components.DialogButton
import main.components.DialogButtonStyle
import main.components.Translated
import main.components.rememberDarkMode
import main.theme.Theme
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.*

@Composable
fun ToolsPage() {
    val router = Router.current
    val darkMode = rememberDarkMode()

    Div(attrs = {
        style {
            padding(24.px)
            display(DisplayStyle.Flex)
            flexDirection(FlexDirection.Column)
            alignItems(AlignItems.Center)
            gap(24.px)
            height(100.vh)
            boxSizing("border-box")
            backgroundColor(if (darkMode) Theme.colors.backgroundDark else Theme.colors.background)
        }
    }) {
        H1(attrs = {
            style {
                color(if (darkMode) Theme.colors.onSurfaceDark else Theme.colors.onSurface)
                marginTop(0.px)
            }
        }) {
            Translated("Tools")
        }

        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Row)
                flexWrap(FlexWrap.Wrap)
                justifyContent(JustifyContent.Center)
                gap(24.px)
            }
        }) {
            DialogButton(
                onClick = { router.navigate("/tools/character") },
                style = DialogButtonStyle.Secondary,
                attrs = {
                    style {
                        minWidth(200.px)
                    }
                }
            ) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        gap(8.px)
                    }
                }) {
                    Span(attrs = { style { fontSize(32.px) } }) { Text("👤") }
                    Translated("Character tool")
                }
            }

            DialogButton(
                onClick = { router.navigate("/tools/scenes") },
                style = DialogButtonStyle.Secondary,
                attrs = {
                    style {
                        minWidth(200.px)
                    }
                }
            ) {
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        flexDirection(FlexDirection.Column)
                        alignItems(AlignItems.Center)
                        gap(8.px)
                    }
                }) {
                    Span(attrs = { style { fontSize(32.px) } }) { Text("🎬") }
                    Translated("English lesson scenes")
                }
            }
        }
    }
}
