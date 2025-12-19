package main.components.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import main.components.Translated
import main.components.player.CharacterRenderer
import main.components.rememberDarkMode
import main.components.rememberIsMobile
import main.components.world.SpriteSheet
import main.theme.Styles
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.AlignItems
import org.jetbrains.compose.web.css.DisplayStyle
import org.jetbrains.compose.web.css.FlexDirection
import org.jetbrains.compose.web.css.JustifyContent
import org.jetbrains.compose.web.css.backgroundColor
import org.jetbrains.compose.web.css.borderRadius
import org.jetbrains.compose.web.css.display
import org.jetbrains.compose.web.css.alignItems
import org.jetbrains.compose.web.css.boxSizing
import org.jetbrains.compose.web.css.flexDirection
import org.jetbrains.compose.web.css.gap
import org.jetbrains.compose.web.css.justifyContent
import org.jetbrains.compose.web.css.margin
import org.jetbrains.compose.web.css.maxHeight
import org.jetbrains.compose.web.css.overflowY
import org.jetbrains.compose.web.css.padding
import org.jetbrains.compose.web.css.percent
import org.jetbrains.compose.web.css.px
import org.jetbrains.compose.web.css.vh
import org.jetbrains.compose.web.css.width
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H3
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.url.URL
import web.cssom.pct

private data class LocalSheet(
    val sheet: SpriteSheet,
    val blobUrl: String,
)

@Composable
fun CharacterToolPage() {
    val darkMode = rememberDarkMode()
    val mobile by rememberIsMobile()

    // Common sprite sheet settings
    var cellWidth by remember { mutableStateOf(32) }
    var cellHeight by remember { mutableStateOf(48) }
    var columns by remember { mutableStateOf(8) }
    var fps by remember { mutableStateOf(8f) }
    var settingsExpanded by remember { mutableStateOf(false) }

    // Base sheet
    var base by remember { mutableStateOf<LocalSheet?>(null) }

    // Additional sheets
    val overlays = remember { mutableStateListOf<LocalSheet>() }

    // Walking toggle
    var walking by remember { mutableStateOf(false) }

    // Ensure sane values
    LaunchedEffect(cellWidth, cellHeight, columns, fps) {
        if (cellWidth <= 0) cellWidth = 32
        if (cellHeight <= 0) cellHeight = 48
        if (columns <= 0) columns = 8
        if (fps <= 0f) fps = 8f
    }

    // Layout
    Div(attrs = {
        style {
            display(DisplayStyle.Flex)
            if (mobile) {
                flexDirection(FlexDirection.Column)
            } else {
                flexDirection(FlexDirection.Row)
            }
            gap(16.px)
            padding(16.px)
            overflowY("auto")
            maxHeight(100.vh)
            boxSizing("border-box")
        }
    }) {
        // Left: Controls
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                gap(12.px)
                width(if (mobile) 100.percent else 360.px)
                overflowY("auto")
                maxHeight(100.percent)
                boxSizing("border-box")
            }
        }) {
            H3 { Translated("Character tool") }

            // Common settings (collapsed section)
            Button(attrs = {
                classes(Styles.secondaryButton)
                onClick { settingsExpanded = !settingsExpanded }
            }) {
                Text(if (settingsExpanded) "\u25BC " else "\u25B6 ")
                Translated("Sprite sheet settings")
            }

            if (settingsExpanded) {
                Label { Translated("Cell width (px)") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    attr("min", "1")
                    value(cellWidth.toString())
                    onInput { ev ->
                        val v = (ev.target as HTMLInputElement).value
                        cellWidth = v.toIntOrNull() ?: cellWidth
                    }
                })
                Label { Translated("Cell height (px)") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    attr("min", "1")
                    value(cellHeight.toString())
                    onInput { ev ->
                        val v = (ev.target as HTMLInputElement).value
                        cellHeight = v.toIntOrNull() ?: cellHeight
                    }
                })
                Label { Translated("Columns") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    attr("min", "1")
                    value(columns.toString())
                    onInput { ev ->
                        val v = (ev.target as HTMLInputElement).value
                        columns = v.toIntOrNull() ?: columns
                    }
                })
                Label { Translated("Frames per second") }
                Input(type = InputType.Number, attrs = {
                    classes(Styles.inputField)
                    attr("min", "1")
                    value(fps.toInt().toString())
                    onInput { ev ->
                        val v = (ev.target as HTMLInputElement).value
                        fps = (v.toFloatOrNull() ?: fps).coerceAtLeast(1f)
                    }
                })
            }

            // Base upload
            P { Translated("Base sprite sheet") }
            Input(type = InputType.File, attrs = {
                attr("accept", "image/*")
                onInput { ev ->
                    val f = ev.target.files?.item(0)
                    if (f != null) {
                        base?.let { URL.revokeObjectURL(it.blobUrl) }
                        val blob = URL.createObjectURL(f)
                        val sheet = SpriteSheet(
                            imageUrl = blob,
                            cellWidthPx = cellWidth,
                            cellHeightPx = cellHeight,
                            columns = columns,
                            framesPerSecond = fps,
                        )
                        base = LocalSheet(sheet, blob)
                    }
                }
            })

            // Overlays upload
            P { Translated("Additional sprite sheets") }
            Input(type = InputType.File, attrs = {
                attr("accept", "image/*")
                onInput { ev ->
                    val f = ev.target.files?.item(0)
                    if (f != null) {
                        val blob = URL.createObjectURL(f)
                        val sheet = SpriteSheet(
                            imageUrl = blob,
                            cellWidthPx = cellWidth,
                            cellHeightPx = cellHeight,
                            columns = columns,
                            framesPerSecond = fps,
                        )
                        overlays.add(LocalSheet(sheet, blob))
                    }
                }
            })

            // List overlays
            overlays.forEachIndexed { index, ls ->
                Div(attrs = {
                    style {
                        display(DisplayStyle.Flex)
                        justifyContent(JustifyContent.SpaceBetween)
                        alignItems(AlignItems.Center)
                        if (darkMode) {
                            backgroundColor(main.theme.Theme.colors.surfaceVariantDark)
                        } else {
                            backgroundColor(main.theme.Theme.colors.surfaceVariant)
                        }
                        borderRadius(8.px)
                        padding(8.px)
                    }
                }) {
                    Translated("Overlay")
                    Text(" ${index + 1}")
                    Button(attrs = { classes(Styles.primaryButton) ; onClick {
                        URL.revokeObjectURL(ls.blobUrl)
                        overlays.removeAt(index)
                    } }) { Translated("Remove") }
                }
            }

            // Walking toggle
            Button(attrs = {
                classes(Styles.primaryButton)
                onClick { walking = !walking }
            }) { Translated(if (walking) "Stop walking" else "Start walking") }
        }

        // Right: Preview
        Div(attrs = {
            style {
                display(DisplayStyle.Flex)
                flexDirection(FlexDirection.Column)
                alignItems(AlignItems.Center)
                justifyContent(JustifyContent.Center)
                padding(16.px)
                margin(8.px)
            }
        }) {
            val baseSheet = base?.sheet ?: SpriteSheet.createPlayerSpriteSheet(sex = null)
            CharacterRenderer(
                characterSpriteSheet = baseSheet,
                additionalSpriteSheets = overlays.map { it.sheet },
                enableInventoryIntegration = false,
                useProvidedBaseSpriteSheet = true,
                isWalking = walking,
            )
        }
    }
}
