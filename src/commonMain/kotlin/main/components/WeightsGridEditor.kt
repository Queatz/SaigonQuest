package main.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.web.css.*
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Label
import org.jetbrains.compose.web.dom.Text
import main.theme.Styles

/**
 * Reusable 5x5 numeric grid editor for wind weights (values in [0,1]).
 * - grid: 5x5 list of strings representing numeric values
 * - onGridChange: callback with updated grid when any cell changes
 * - showReset: whether to render a Reset to defaults button
 * - onReset: optional reset handler; if null and showReset=true, a default reset pattern is used
 * - backgroundUrl: optional background image to overlay on (e.g. spritesheet)
 * - isWearable: if true, uses 32x48 aspect ratio for the preview, else 32x32
 */
@Composable
fun WeightsGridEditor(
    grid: List<List<String>>,
    onGridChange: (List<List<String>>) -> Unit,
    showReset: Boolean = true,
    onReset: (() -> Unit)? = null,
    backgroundUrl: String? = null,
    isWearable: Boolean = false,
) {
    // Reset button row (optional)
    if (showReset) {
        Div(attrs = { style { marginBottom(8.px) } }) {
            DialogButton(
                onClick = {
                    if (onReset != null) {
                        onReset()
                    } else {
                        // Default gradient-like values top->bottom
                        val rv = listOf("0", "0.0125", "0.025", "0.5", "1")
                        val def = List(5) { j -> List(5) { rv[j] } }
                        onGridChange(def)
                    }
                },
                style = DialogButtonStyle.Secondary,
            ) { Translated("Reset to defaults") }
        }
    }

    // 5x5 grid inputs
    Div(
        attrs = {
            style {
                display(DisplayStyle.Grid)
                property("grid-template-columns", "repeat(5, 64px)")
                property("gap", "6px")
                marginBottom(16.px)
            }
        }
    ) {
        for (j in 0 until 5) {
            for (i in 0 until 5) {
                DecimalNumberInput(
                    value = grid[j][i],
                    onChange = { v ->
                        val newGrid = grid.mapIndexed { r, row ->
                            if (r != j) row else row.mapIndexed { c, cell -> if (c == i) v else cell }
                        }
                        onGridChange(newGrid)
                    }
                )
            }
        }
    }

    // 5x5 Preview section
    val cellWidth = 48
    val cellHeight = if (isWearable) 72 else 48
    val gridWidth = cellWidth * 5
    val gridHeight = cellHeight * 5

    Label { Translated("Preview") }
    Div(attrs = {
        classes(Styles.weightsPreviewGrid)
        style {
            property("grid-template-columns", "repeat(5, ${cellWidth}px)")
            property("grid-template-rows", "repeat(5, ${cellHeight}px)")
            property("width", "${gridWidth}px")
            property("height", "${gridHeight}px")

            if (backgroundUrl != null) {
                backgroundImage("url(\"$backgroundUrl\")")
                // Sprite sheets are 256x256 (8x8) for apparel, 320x320 (10x10) for icons.
                // Sprites are 32px wide.
                val spriteWidth = 32.0
                val scale = gridWidth.toDouble() / spriteWidth
                val columns = if (isWearable) 8 else 10
                val sheetSize = (columns * spriteWidth) * scale
                backgroundSize("${sheetSize}px ${sheetSize}px")

                if (isWearable) {
                    // Character/Wearable: index 0 (standing south) is at the bottom row (vMin=0 in BabylonJS)
                    backgroundPosition("0 -${sheetSize - gridHeight}px")
                } else {
                    // Item: index 0 starts at bottom-left of the 10x10 grid
                    backgroundPosition("0 -${sheetSize - gridHeight}px")
                }
            } else {
                backgroundColor(Color("rgba(255,255,255,0.05)"))
            }
        }
    }) {
        for (j in 0 until 5) {
            for (i in 0 until 5) {
                val weightStr = grid[j][i]
                val w = (weightStr.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 1.0)
                val v = (w * 255).toInt().coerceIn(0, 255)

                Div(attrs = {
                    classes(Styles.weightsPreviewCell)
                    style {
                        property("background-color", "rgba($v, $v, $v, ${if (backgroundUrl != null) 0.5 else 0.8})")
                        color(if (v > 160) Color("black") else Color("white"))
                        property("text-shadow", "0 0 2px ${if (v > 160) "white" else "black"}")
                    }
                }) {
                    Text(weightStr.take(4))
                }
            }
        }
    }
}
